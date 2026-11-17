/**
 * author : 王星星
 * date : 2026-11-16 13:44
 * email : 1099420259@qq.com
 * description : 分享仓储基础读写；内容为创建时快照，不与 clips 建立外键。
 *               注意：所有方法必须在事务内调用，token_hash 是唯一主键。
 */
package com.wangxingxing.clipbox.server.db

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

data class ShareRow(
    val tokenHash: String,
    val clipId: String,
    val content: String,
    val createdAt: Long,
    val expiresAt: Long,
    val oneTime: Boolean,
    val openedAt: Long?,
)

object ShareRepository {

    fun insert(share: ShareRow) {
        SharesTable.insert {
            it[tokenHash] = share.tokenHash
            it[clipId] = share.clipId
            it[content] = share.content
            it[createdAt] = share.createdAt
            it[expiresAt] = share.expiresAt
            it[oneTime] = if (share.oneTime) 1 else 0
            it[openedAt] = share.openedAt
        }
    }

    fun findByTokenHash(tokenHash: String): ShareRow? =
        SharesTable.selectAll()
            .where { SharesTable.tokenHash eq tokenHash }
            .singleOrNull()
            ?.toShareRow()

    /**
     * 一次性分享的原子消费：只有未打开且未过期的一次性记录会被标记。
     * 返回受影响行数；调用方必须据此判断是否真正获得了本次访问权。
     */
    fun markOpenedIfOneTime(tokenHash: String, now: Long): Int =
        SharesTable.update({
            (SharesTable.tokenHash eq tokenHash) and
                (SharesTable.oneTime eq 1) and
                SharesTable.openedAt.isNull() and
                (SharesTable.expiresAt greater now)
        }) {
            it[openedAt] = now
        }

    private fun ResultRow.toShareRow() = ShareRow(
        tokenHash = this[SharesTable.tokenHash],
        clipId = this[SharesTable.clipId],
        content = this[SharesTable.content],
        createdAt = this[SharesTable.createdAt],
        expiresAt = this[SharesTable.expiresAt],
        oneTime = this[SharesTable.oneTime] != 0,
        openedAt = this[SharesTable.openedAt],
    )
}
