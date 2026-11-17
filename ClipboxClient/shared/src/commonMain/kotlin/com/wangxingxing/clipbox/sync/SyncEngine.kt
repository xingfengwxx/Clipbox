/**
 * author : 王星星
 * date : 2026-11-17 11:28
 * email : 1099420259@qq.com
 * description : 增量拉取与全量重同步；按 serverSeq 分页应用变更并推进 meta.lastSeq，墓碑只删除无待发操作的本地记录。注意：lastSeq 只能由 nextSince 推进，写接口响应不得参与；syncMutex 必须与 OutboxWorker 使用同一实例，否则重放与拉取无法串行改本地库
 */
package com.wangxingxing.clipbox.sync

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.wangxingxing.clipbox.db.ClipDao
import com.wangxingxing.clipbox.db.ClipEntity
import com.wangxingxing.clipbox.db.ClipboxDatabase
import com.wangxingxing.clipbox.db.MetaDao
import com.wangxingxing.clipbox.db.MetaKeys
import com.wangxingxing.clipbox.db.OutboxDao
import com.wangxingxing.clipbox.model.ApiErrorCode
import com.wangxingxing.clipbox.model.ApiFailure
import com.wangxingxing.clipbox.model.ClipDto
import com.wangxingxing.clipbox.model.SyncResponse
import com.wangxingxing.clipbox.model.SyncState
import com.wangxingxing.clipbox.network.ApiResult
import com.wangxingxing.clipbox.network.ApiService
import com.wangxingxing.clipbox.settings.SettingsRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SyncEngine(
    private val database: ClipboxDatabase,
    private val apiService: ApiService,
    private val settingsRepository: SettingsRepository,
    private val syncMutex: Mutex = Mutex(),
) {

    /**
     * 从 meta.lastSeq 拉取增量变更，遇到缺口或 `resyncRequired` 时自动全量重同步。
     * 返回的 [PullResult.Success.lastSeq] 是本次成功提交的游标；失败时游标停在最后一页成功值。
     */
    suspend fun pull(): PullResult = syncMutex.withLock { pullLocked() }

    private suspend fun pullLocked(): PullResult {
        if (settingsRepository.current().deviceToken.isNullOrEmpty()) return PullResult.WaitingForPairing

        var since = database.metaDao().get(MetaKeys.LAST_SEQ)
        var appliedItems = 0
        while (true) {
            val page = when (val result = apiService.getClips(since = since, limit = PAGE_LIMIT)) {
                is ApiResult.Failure -> return PullResult.Failure(result.failure)
                is ApiResult.Success -> result.value
            }
            // 游标早于服务端可保证范围，或来自被重置的服务端（恢复旧备份）时，只能全量重拉
            if (page.resyncRequired || since > page.serverMaxSeq) {
                return resyncLocked()
            }
            appliedItems += applyPage(page, since)
            val advanced = page.nextSince > since
            since = page.nextSince
            if (!page.hasMore) break
            // 服务端承诺空页 hasMore=false；游标不再前进时直接退出，避免无限请求
            if (!advanced) break
        }
        database.clipDao().trimNonPinned()
        return PullResult.Success(appliedItems = appliedItems, lastSeq = since, resynced = false)
    }

    // 重同步期间保留 Outbox 与待发 / 失败记录，只清空已同步记录后从 0 重拉
    private suspend fun resyncLocked(): PullResult {
        writeTransaction { clipDao, _, metaDao ->
            clipDao.deleteSynced()
            metaDao.put(MetaKeys.LAST_SEQ, 0)
        }

        var since = 0L
        var appliedItems = 0
        while (true) {
            val page = when (val result = apiService.getClips(since = since, limit = PAGE_LIMIT)) {
                is ApiResult.Failure -> return PullResult.Failure(result.failure)
                is ApiResult.Success -> result.value
            }
            // since=0 不应再要求重同步；出现即视为服务端违约，终止以免反复全量拉取
            if (page.resyncRequired) {
                return PullResult.Failure(ApiFailure.Retryable(ApiErrorCode.INVALID_RESPONSE))
            }
            appliedItems += applyPage(page, since)
            val advanced = page.nextSince > since
            since = page.nextSince
            if (!page.hasMore) break
            if (!advanced) break
        }
        database.clipDao().trimNonPinned()
        return PullResult.Success(appliedItems = appliedItems, lastSeq = since, resynced = true)
    }

    // 整页应用与游标写入同事务：避免记录已写而游标未存导致重复拉取，或游标越过后丢失记录
    private suspend fun applyPage(page: SyncResponse, since: Long): Int =
        writeTransaction { clipDao, outboxDao, metaDao ->
            var applied = 0
            for (item in page.items) {
                if (item.deleted) {
                    if (clipDao.getById(item.id) == null) continue
                    // 本地仍有待发操作时保留，避免墓碑吞掉用户尚未同步的修改
                    if (outboxDao.opsForClip(item.id).isNotEmpty()) continue
                    clipDao.deleteById(item.id)
                    applied++
                } else {
                    val local = clipDao.getById(item.id)
                    // 待发 / 失败记录的合并规则由 T-CORE-10 统一收口，本层不覆盖本地意图
                    if (local != null && local.syncState != SyncState.SYNCED) continue
                    clipDao.upsert(item.toEntity())
                    applied++
                }
            }
            if (page.nextSince >= since) metaDao.put(MetaKeys.LAST_SEQ, page.nextSince)
            applied
        }

    private fun ClipDto.toEntity(): ClipEntity = ClipEntity(
        id = id,
        content = content.orEmpty(),
        sourceDeviceId = sourceDeviceId.orEmpty(),
        sourceDeviceName = sourceDeviceName.orEmpty(),
        pinned = pinned ?: false,
        createdAt = createdAt ?: 0,
        serverSeq = serverSeq,
        deleted = false,
        syncState = SyncState.SYNCED,
    )

    private suspend fun <T> writeTransaction(
        block: suspend (ClipDao, OutboxDao, MetaDao) -> T,
    ): T = database.useWriterConnection { transactor ->
        transactor.immediateTransaction {
            block(database.clipDao(), database.outboxDao(), database.metaDao())
        }
    }

    private companion object {
        const val PAGE_LIMIT = 200
    }
}

sealed interface PullResult {
    data class Success(
        val appliedItems: Int,
        val lastSeq: Long,
        val resynced: Boolean,
    ) : PullResult

    data class Failure(val failure: ApiFailure) : PullResult

    data object WaitingForPairing : PullResult
}
