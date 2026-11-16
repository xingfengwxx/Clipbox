/**
 * author : 王星星
 * date : 2026-11-16 15:52
 * email : 1099420259@qq.com
 * description : outbox 表实体与操作类型；opId 自增保证全局入队顺序，同一 clipId 的后续操作必然排在前面操作之后
 */
package com.wangxingxing.clipbox.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val opId: Long = 0,
    val clipId: String,
    val opType: OutboxOpType,
    val payload: String,
    val retryCount: Int = 0,
    val nextRetryAt: Long = 0,
    val createdAt: Long,
    val lastErrorCode: String? = null,
)

enum class OutboxOpType {
    CREATE,
    PATCH_PIN,
    DELETE,
}
