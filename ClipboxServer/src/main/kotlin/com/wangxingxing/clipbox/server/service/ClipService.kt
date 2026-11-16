/**
 * author : 王星星
 * date : 2026-11-16 15:21
 * email : 1099420259@qq.com
 * description : 剪贴记录读写服务：读侧按 since 游标组装变更页，写侧负责创建、置顶与软删除的校验、幂等与序号分配。
 *               注意：游标、syncFloor 与变更行必须在同一读事务内读取；写操作必须在同一写事务内分配并写入 server_seq；
 *                     幂等命中不得推进序号；排序只依赖 serverSeq，createdAt 不参与。
 */
package com.wangxingxing.clipbox.server.service

import com.wangxingxing.clipbox.server.db.ClipRepository
import com.wangxingxing.clipbox.server.db.ClipRow
import com.wangxingxing.clipbox.server.db.ClipSyncRow
import com.wangxingxing.clipbox.server.db.DatabaseFactory
import com.wangxingxing.clipbox.server.db.MetaRepository
import com.wangxingxing.clipbox.server.db.SeqAllocator
import io.ktor.http.HttpStatusCode

/** 写接口的业务错误；由路由映射为统一错误 JSON。 */
class ClipWriteFailure(
    val status: HttpStatusCode,
    val code: String,
    override val message: String,
) : Exception(message)

/** created 区分首次写入（201）与幂等命中（200），供路由选择状态码。 */
data class CreateClipOutcome(
    val clip: ClipRow,
    val created: Boolean,
)

/** 变更页条目：墓碑只携带 id 与 serverSeq，正文不参与同步。 */
sealed interface SyncItem {
    data class Active(
        val id: String,
        val content: String,
        val sourceDeviceId: String?,
        val sourceDeviceName: String,
        val pinned: Boolean,
        val createdAt: Long,
        val serverSeq: Long,
    ) : SyncItem

    data class Tombstone(
        val id: String,
        val serverSeq: Long,
    ) : SyncItem
}

data class ClipSyncPage(
    val items: List<SyncItem>,
    val nextSince: Long,
    val hasMore: Boolean,
    val resyncRequired: Boolean,
    val minAvailableSeq: Long,
    val serverMaxSeq: Long,
)

class ClipService(
    private val databaseFactory: DatabaseFactory,
    private val now: () -> Long = System::currentTimeMillis,
) {

    suspend fun sync(since: Long, limit: Int): ClipSyncPage {
        val page = databaseFactory.readTransaction {
            val serverMaxSeq = MetaRepository.getLong(MetaRepository.SERVER_SEQ_KEY) ?: 0L
            val syncFloor = MetaRepository.getLong(MetaRepository.SYNC_FLOOR_KEY) ?: 0L
            val resyncRequired = (since > 0 && since < syncFloor) || since > serverMaxSeq
            // 需要重同步时不再查询变更行，避免把无意义的半页数据发给客户端。
            val rows = if (resyncRequired) emptyList() else ClipRepository.listSince(since, limit + 1)
            PageSnapshot(serverMaxSeq, syncFloor, resyncRequired, rows)
        }

        if (page.resyncRequired) {
            return ClipSyncPage(
                items = emptyList(),
                nextSince = since,
                hasMore = false,
                resyncRequired = true,
                minAvailableSeq = page.syncFloor + 1,
                serverMaxSeq = page.serverMaxSeq,
            )
        }

        val items = page.rows.take(limit)
        return ClipSyncPage(
            items = items.map { it.toSyncItem() },
            nextSince = items.lastOrNull()?.serverSeq ?: since,
            hasMore = page.rows.size > limit,
            resyncRequired = false,
            minAvailableSeq = page.syncFloor + 1,
            serverMaxSeq = page.serverMaxSeq,
        )
    }

    /**
     * 创建剪贴记录。客户端 ULID 是幂等键：同 ID 同正文重放返回既有记录（200），
     * 同 ID 不同正文或已软删除返回 409，只有首次创建回传 created=true 并分配序号。
     */
    suspend fun create(
        id: String,
        content: String,
        createdAt: Long,
        sourceDeviceId: String,
    ): CreateClipOutcome {
        validateCreateInput(id, content, createdAt)

        return databaseFactory.writeTransaction {
            val existing = ClipRepository.findById(id)
            if (existing != null) {
                // 墓碑不可复活：旧 Outbox 重放必须得到冲突，而不是悄悄恢复一条已删除记录。
                if (existing.deletedAt != null || existing.content != content) {
                    throw ClipWriteFailure(
                        HttpStatusCode.Conflict,
                        "CONFLICT",
                        "同 ID 记录已存在且正文不一致或已删除",
                    )
                }
                return@writeTransaction CreateClipOutcome(existing, created = false)
            }

            val serverSeq = SeqAllocator.nextSeq()
            val clip = ClipRow(
                id = id,
                content = content,
                sourceDeviceId = sourceDeviceId,
                pinned = false,
                createdAt = createdAt,
                deletedAt = null,
                serverSeq = serverSeq,
            )
            ClipRepository.insert(clip)
            CreateClipOutcome(clip, created = true)
        }
    }

    /** 置顶幂等：值相同不推进序号；不存在或已软删除按 404 处理。 */
    suspend fun setPinned(id: String, pinned: Boolean): ClipRow =
        databaseFactory.writeTransaction {
            val existing = ClipRepository.findById(id) ?: throw clipNotFound()
            if (existing.deletedAt != null) throw clipNotFound()
            if (existing.pinned == pinned) {
                existing
            } else {
                val serverSeq = SeqAllocator.nextSeq()
                ClipRepository.updatePinned(id, pinned, serverSeq)
                existing.copy(pinned = pinned, serverSeq = serverSeq)
            }
        }

    /** 软删除幂等：重复删除返回既有序号；从未存在的记录按 404 处理。 */
    suspend fun delete(id: String): ClipRow =
        databaseFactory.writeTransaction {
            val existing = ClipRepository.findById(id) ?: throw clipNotFound()
            if (existing.deletedAt != null) {
                existing
            } else {
                val serverSeq = SeqAllocator.nextSeq()
                val deletedAt = now()
                ClipRepository.markDeleted(id, deletedAt, serverSeq)
                existing.copy(deletedAt = deletedAt, serverSeq = serverSeq)
            }
        }

    private fun validateCreateInput(id: String, content: String, createdAt: Long) {
        if (!ULID_PATTERN.matches(id)) {
            throw validationError("id 必须是 26 位大写 Crockford Base32 ULID")
        }
        if (content.isBlank()) {
            throw validationError("content 不能为空或仅包含空白")
        }
        if (content.toByteArray(Charsets.UTF_8).size > MAX_CONTENT_BYTES) {
            throw validationError("content 超过 $MAX_CONTENT_BYTES 个 UTF-8 字节")
        }
        if (createdAt <= 0) {
            throw validationError("createdAt 必须大于 0")
        }
    }

    private fun validationError(message: String) =
        ClipWriteFailure(HttpStatusCode.BadRequest, "VALIDATION_ERROR", message)

    private fun clipNotFound() =
        ClipWriteFailure(HttpStatusCode.NotFound, "NOT_FOUND", "剪贴记录不存在")

    private fun ClipSyncRow.toSyncItem(): SyncItem =
        if (deletedAt != null) {
            SyncItem.Tombstone(id = id, serverSeq = serverSeq)
        } else {
            SyncItem.Active(
                id = id,
                content = content,
                sourceDeviceId = sourceDeviceId,
                sourceDeviceName = sourceDeviceName ?: UNKNOWN_DEVICE_NAME,
                pinned = pinned,
                createdAt = createdAt,
                serverSeq = serverSeq,
            )
        }

    private data class PageSnapshot(
        val serverMaxSeq: Long,
        val syncFloor: Long,
        val resyncRequired: Boolean,
        val rows: List<ClipSyncRow>,
    )

    companion object {
        const val UNKNOWN_DEVICE_NAME = "未知设备"

        private const val MAX_CONTENT_BYTES = 65536
        private val ULID_PATTERN = Regex("^[0-9A-HJKMNP-TV-Z]{26}$")
    }
}
