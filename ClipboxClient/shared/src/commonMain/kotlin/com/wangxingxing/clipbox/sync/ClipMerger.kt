/**
 * author : 王星星
 * date : 2026-11-17 13:32
 * email : 1099420259@qq.com
 * description : 远端记录与本地待发状态的唯一合并规则；纯函数、无 IO，供 REST 拉取与 WS 事件共用。注意：墓碑只删除没有未发送操作的本地记录，失败记录保留到用户重试或墓碑到达
 */
package com.wangxingxing.clipbox.sync

import com.wangxingxing.clipbox.db.ClipEntity
import com.wangxingxing.clipbox.db.OutboxEntity
import com.wangxingxing.clipbox.model.ClipDto
import com.wangxingxing.clipbox.model.SyncState

internal object ClipMerger {

    fun merge(local: ClipEntity?, remote: ClipDto, outboxOps: List<OutboxEntity>): MergeResult =
        if (remote.deleted) mergeTombstone(local, outboxOps) else mergeActive(local, remote)

    private fun mergeTombstone(local: ClipEntity?, outboxOps: List<OutboxEntity>): MergeResult {
        if (local == null) return MergeResult.Ignore
        // 未发送操作优先于墓碑：直接删除会丢掉用户尚未同步的修改；已失败操作不算未发送
        if (outboxOps.any { it.lastErrorCode == null }) return MergeResult.Ignore
        return MergeResult.Delete
    }

    private fun mergeActive(local: ClipEntity?, remote: ClipDto): MergeResult {
        if (local == null) return MergeResult.Upsert(remote.toSyncedEntity())
        return when (local.syncState) {
            SyncState.SYNCED -> MergeResult.Upsert(remote.toSyncedEntity())

            // 正文一致说明本地 CREATE 实际已在服务端生效，只补 serverSeq 并保留待发状态；
            // 正文不同不在此处判定冲突，留给 CREATE 重放的 409 CONFLICT 暴露，避免静默覆盖
            SyncState.PENDING_CREATE ->
                if (remote.content.orEmpty() != local.content) {
                    MergeResult.Ignore
                } else {
                    local.withServerSeq(remote.serverSeq)
                }

            SyncState.PENDING_UPDATE -> local.withServerSeq(remote.serverSeq)

            // 本地删除意图不得被活动记录复活；失败状态只允许用户重试或墓碑清除
            SyncState.PENDING_DELETE,
            SyncState.FAILED_CREATE,
            SyncState.FAILED_UPDATE,
            SyncState.FAILED_DELETE -> MergeResult.Ignore
        }
    }

    private fun ClipEntity.withServerSeq(serverSeq: Long): MergeResult =
        if (this.serverSeq == serverSeq) MergeResult.Ignore else MergeResult.Upsert(copy(serverSeq = serverSeq))

    private fun ClipDto.toSyncedEntity(): ClipEntity = ClipEntity(
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
}

sealed interface MergeResult {
    data class Upsert(val entity: ClipEntity) : MergeResult
    data object Delete : MergeResult
    data object Ignore : MergeResult
}
