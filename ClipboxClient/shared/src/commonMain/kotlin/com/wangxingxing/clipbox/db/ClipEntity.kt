/**
 * author : 王星星
 * date : 2026-11-16 15:52
 * email : 1099420259@qq.com
 * description : clips 表实体，字段与 docs/客户端技术架构.md §6.1.1 一一对应；syncState 以枚举名存 TEXT，改名等同数据库结构变更
 */
package com.wangxingxing.clipbox.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wangxingxing.clipbox.model.SyncState

@Entity(
    tableName = "clips",
    indices = [
        Index("createdAt"),
        Index("pinned"),
        Index("syncState"),
    ],
)
data class ClipEntity(
    @PrimaryKey val id: String,
    val content: String,
    val sourceDeviceId: String,
    val sourceDeviceName: String,
    val pinned: Boolean,
    val createdAt: Long,
    val serverSeq: Long = 0,
    val deleted: Boolean = false,
    val syncState: SyncState,
    val lastErrorCode: String? = null,
    val lastErrorAt: Long? = null,
)
