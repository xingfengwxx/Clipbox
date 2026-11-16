/**
 * author : 王星星
 * date : 2026-11-16 15:52
 * email : 1099420259@qq.com
 * description : meta 表键值与键名常量；lastSeq 是同步游标唯一存储位置，只能由 REST 拉取或连续 WS 事件推进
 */
package com.wangxingxing.clipbox.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "meta")
data class MetaEntity(
    @PrimaryKey @ColumnInfo(name = "key") val key: String,
    val value: Long,
)

object MetaKeys {
    const val LAST_SEQ = "lastSeq"
    const val LAST_SYNC_AT = "lastSyncAt"
    const val LAST_FULL_SYNC_AT = "lastFullSyncAt"
    const val LAST_SERVER_MAX_SEQ = "lastServerMaxSeq"
}
