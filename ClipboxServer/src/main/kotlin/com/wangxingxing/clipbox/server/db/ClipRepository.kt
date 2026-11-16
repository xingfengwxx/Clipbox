/**
 * author : 王星星
 * date : 2026-11-16 13:44
 * email : 1099420259@qq.com
 * description : 剪贴记录仓储基础读写；pinned 在库中为 0/1，对上层暴露为布尔。
 *               注意：所有方法必须在事务内调用，墓碑与保留策略由上层服务负责。
 */
package com.wangxingxing.clipbox.server.db

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

data class ClipRow(
    val id: String,
    val content: String,
    val sourceDeviceId: String?,
    val pinned: Boolean,
    val createdAt: Long,
    val deletedAt: Long?,
    val serverSeq: Long,
)

object ClipRepository {

    fun insert(clip: ClipRow) {
        ClipsTable.insert {
            it[id] = clip.id
            it[content] = clip.content
            it[sourceDeviceId] = clip.sourceDeviceId
            it[pinned] = if (clip.pinned) 1 else 0
            it[createdAt] = clip.createdAt
            it[deletedAt] = clip.deletedAt
            it[serverSeq] = clip.serverSeq
        }
    }

    fun findById(id: String): ClipRow? =
        ClipsTable.selectAll()
            .where { ClipsTable.id eq id }
            .singleOrNull()
            ?.toClipRow()

    private fun ResultRow.toClipRow() = ClipRow(
        id = this[ClipsTable.id],
        content = this[ClipsTable.content],
        sourceDeviceId = this[ClipsTable.sourceDeviceId],
        pinned = this[ClipsTable.pinned] != 0,
        createdAt = this[ClipsTable.createdAt],
        deletedAt = this[ClipsTable.deletedAt],
        serverSeq = this[ClipsTable.serverSeq],
    )
}
