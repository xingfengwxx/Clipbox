/**
 * author : 王星星
 * date : 2026-11-16 16:14
 * email : 1099420259@qq.com
 * description : 剪贴记录写入入口；发送、置顶、删除、重试全部先写本地 Room 并与 Outbox 入队同事务完成，不调用网络。注意：ULID 生成依赖进程内状态，只能在写入事务内调用；PENDING_CREATE 未完成前不得跳过 POST 发送 PATCH/DELETE
 */
package com.wangxingxing.clipbox.repository

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.wangxingxing.clipbox.db.ClipDao
import com.wangxingxing.clipbox.db.ClipEntity
import com.wangxingxing.clipbox.db.ClipboxDatabase
import com.wangxingxing.clipbox.db.OutboxDao
import com.wangxingxing.clipbox.db.OutboxEntity
import com.wangxingxing.clipbox.db.OutboxOpType
import com.wangxingxing.clipbox.model.ApiErrorCode
import com.wangxingxing.clipbox.model.Clip
import com.wangxingxing.clipbox.model.CreateClipRequest
import com.wangxingxing.clipbox.model.PatchPinnedRequest
import com.wangxingxing.clipbox.model.SyncState
import com.wangxingxing.clipbox.settings.SettingsRepository
import com.wangxingxing.clipbox.util.TextSize
import com.wangxingxing.clipbox.util.Ulid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class ClipRepository(
    private val database: ClipboxDatabase,
    private val settingsRepository: SettingsRepository,
    private val clock: () -> Long = ::defaultClockMillis,
) {

    private val payloadJson = Json { ignoreUnknownKeys = true }

    fun observeClips(limitNonPinned: Int = ClipDao.DEFAULT_NON_PINNED_LIMIT): Flow<List<Clip>> =
        database.clipDao().observeHistory(limitNonPinned).map { clips ->
            clips.map { it.toModel() }
        }

    suspend fun send(content: String): SendResult {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return SendResult.Empty
        if (TextSize.utf8ByteCount(trimmed) > TextSize.MAX_CLIP_UTF8_BYTES) return SendResult.TooLarge

        // 设备身份可能尚未配对，存空串由界面映射为“未知设备”，不阻塞离线写入
        val settings = settingsRepository.current()
        val deviceId = settings.deviceId.orEmpty()
        val deviceName = settings.deviceName.orEmpty()

        val clipId = writeInTransaction { clipDao, outboxDao ->
            val createdAt = clock()
            val id = Ulid.generate(createdAt)
            clipDao.upsert(
                ClipEntity(
                    id = id,
                    content = trimmed,
                    sourceDeviceId = deviceId,
                    sourceDeviceName = deviceName,
                    pinned = false,
                    createdAt = createdAt,
                    syncState = SyncState.PENDING_CREATE,
                )
            )
            outboxDao.enqueue(
                OutboxEntity(
                    clipId = id,
                    opType = OutboxOpType.CREATE,
                    payload = payloadJson.encodeToString(
                        CreateClipRequest(id = id, content = trimmed, createdAt = createdAt),
                    ),
                    createdAt = createdAt,
                )
            )
            id
        }
        return SendResult.Success(clipId)
    }

    suspend fun pin(id: String, pinned: Boolean): Boolean = writeInTransaction { clipDao, outboxDao ->
        val clip = clipDao.getById(id) ?: return@writeInTransaction false
        if (clip.deleted || clip.syncState in PIN_BLOCKED_STATES) return@writeInTransaction false
        if (clip.pinned == pinned) return@writeInTransaction true

        val ops = outboxDao.opsForClip(id)
        val payload = payloadJson.encodeToString(PatchPinnedRequest(pinned = pinned))
        val pendingPatch = ops.lastOrNull { it.opType == OutboxOpType.PATCH_PIN }
        if (pendingPatch != null) {
            // 连续置顶只保留最终值，避免为同一条记录堆积多笔 PATCH
            outboxDao.updatePayload(pendingPatch.opId, payload)
            if (clip.syncState == SyncState.FAILED_UPDATE) {
                outboxDao.markRetry(pendingPatch.opId, retryCount = 0, nextRetryAt = 0, errorCode = null)
            }
        } else {
            outboxDao.enqueue(
                OutboxEntity(
                    clipId = id,
                    opType = OutboxOpType.PATCH_PIN,
                    payload = payload,
                    createdAt = clock(),
                )
            )
        }

        // CREATE 尚未发送时保持 PENDING_CREATE，opId 顺序保证 POST 先于 PATCH
        val createPending = ops.any { it.opType == OutboxOpType.CREATE }
        clipDao.upsert(
            clip.copy(
                pinned = pinned,
                syncState = if (createPending) SyncState.PENDING_CREATE else SyncState.PENDING_UPDATE,
                lastErrorCode = null,
                lastErrorAt = null,
            )
        )
        true
    }

    suspend fun delete(id: String): Boolean = writeInTransaction { clipDao, outboxDao ->
        val clip = clipDao.getById(id) ?: return@writeInTransaction false
        if (clip.deleted) return@writeInTransaction true

        val ops = outboxDao.opsForClip(id)
        if (ops.none { it.opType == OutboxOpType.DELETE }) {
            outboxDao.enqueue(
                OutboxEntity(
                    clipId = id,
                    opType = OutboxOpType.DELETE,
                    payload = "{}",
                    createdAt = clock(),
                )
            )
        }
        // 先本地隐藏，DELETE 确认后由同步层物理移除
        clipDao.upsert(
            clip.copy(
                deleted = true,
                syncState = SyncState.PENDING_DELETE,
                lastErrorCode = null,
                lastErrorAt = null,
            )
        )
        true
    }

    suspend fun retry(id: String): Boolean = writeInTransaction { clipDao, outboxDao ->
        val clip = clipDao.getById(id) ?: return@writeInTransaction false
        val pendingState = when (clip.syncState) {
            SyncState.FAILED_CREATE -> SyncState.PENDING_CREATE
            SyncState.FAILED_UPDATE -> SyncState.PENDING_UPDATE
            SyncState.FAILED_DELETE -> SyncState.PENDING_DELETE
            else -> return@writeInTransaction false
        }

        val now = clock()
        val ops = outboxDao.opsForClip(id)
        // 重置已有操作的退避信息，避免重试后仍被旧的 nextRetryAt 阻塞
        ops.forEach { op ->
            outboxDao.markRetry(op.opId, retryCount = 0, nextRetryAt = 0, errorCode = null)
        }

        when (pendingState) {
            SyncState.PENDING_CREATE -> if (ops.none { it.opType == OutboxOpType.CREATE }) {
                outboxDao.enqueueCreate(clip, now)
            }

            SyncState.PENDING_UPDATE -> {
                val payload = payloadJson.encodeToString(PatchPinnedRequest(pinned = clip.pinned))
                val patch = ops.lastOrNull { it.opType == OutboxOpType.PATCH_PIN }
                if (patch != null) {
                    outboxDao.updatePayload(patch.opId, payload)
                } else {
                    outboxDao.enqueue(
                        OutboxEntity(
                            clipId = id,
                            opType = OutboxOpType.PATCH_PIN,
                            payload = payload,
                            createdAt = now,
                        )
                    )
                }
            }

            SyncState.PENDING_DELETE -> if (ops.none { it.opType == OutboxOpType.DELETE }) {
                outboxDao.enqueue(
                    OutboxEntity(
                        clipId = id,
                        opType = OutboxOpType.DELETE,
                        payload = "{}",
                        createdAt = now,
                    )
                )
            }

            else -> Unit
        }

        clipDao.upsert(
            clip.copy(
                syncState = pendingState,
                lastErrorCode = null,
                lastErrorAt = null,
            )
        )
        true
    }

    // internal：测试用同一事务边界验证失败时 clips 与 outbox 一并回滚
    internal suspend fun <T> writeInTransaction(
        block: suspend (ClipDao, OutboxDao) -> T,
    ): T = database.useWriterConnection { transactor ->
        transactor.immediateTransaction {
            block(database.clipDao(), database.outboxDao())
        }
    }

    private suspend fun OutboxDao.enqueueCreate(clip: ClipEntity, now: Long) {
        enqueue(
            OutboxEntity(
                clipId = clip.id,
                opType = OutboxOpType.CREATE,
                payload = payloadJson.encodeToString(
                    CreateClipRequest(id = clip.id, content = clip.content, createdAt = clip.createdAt),
                ),
                createdAt = now,
            )
        )
    }

    private fun ClipEntity.toModel(): Clip = Clip(
        id = id,
        content = content,
        sourceDeviceId = sourceDeviceId,
        sourceDeviceName = sourceDeviceName,
        pinned = pinned,
        createdAt = createdAt,
        syncState = syncState,
        serverSeq = serverSeq,
        deleted = deleted,
        lastErrorCode = lastErrorCode?.let(ApiErrorCode::fromWire),
        lastErrorAt = lastErrorAt,
    )

    private companion object {
        val PIN_BLOCKED_STATES = setOf(
            SyncState.PENDING_DELETE,
            SyncState.FAILED_DELETE,
            SyncState.FAILED_CREATE,
        )
    }
}

sealed interface SendResult {
    data class Success(val clipId: String) : SendResult
    data object Empty : SendResult
    data object TooLarge : SendResult
}

@OptIn(ExperimentalTime::class)
private fun defaultClockMillis(): Long = Clock.System.now().toEpochMilliseconds()
