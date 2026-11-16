/**
 * author : 王星星
 * date : 2026-11-16 15:21
 * email : 1099420259@qq.com
 * description : 增量同步读侧：按 since 游标与 limit 组装变更页，并判定是否需要客户端全量重同步。
 *               注意：游标、syncFloor 与变更行必须在同一读事务内读取；排序只依赖 serverSeq，createdAt 不参与。
 */
package com.wangxingxing.clipbox.server.service

import com.wangxingxing.clipbox.server.db.ClipRepository
import com.wangxingxing.clipbox.server.db.ClipSyncRow
import com.wangxingxing.clipbox.server.db.DatabaseFactory
import com.wangxingxing.clipbox.server.db.MetaRepository

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

class ClipService(private val databaseFactory: DatabaseFactory) {

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
    }
}
