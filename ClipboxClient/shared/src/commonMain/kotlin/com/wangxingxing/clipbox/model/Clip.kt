/**
 * author : 王星星
 * date : 2026-11-16 14:01
 * email : 1099420259@qq.com
 * description : 剪贴记录领域模型；serverSeq 为 0 表示尚未获得服务端序号，createdAt 只用于界面排序，不能作为同步游标
 */
package com.wangxingxing.clipbox.model

data class Clip(
    val id: String,
    val content: String,
    val sourceDeviceId: String,
    val sourceDeviceName: String,
    val pinned: Boolean,
    val createdAt: Long,
    val syncState: SyncState,
    val serverSeq: Long = 0,
    val deleted: Boolean = false,
    val lastErrorCode: ApiErrorCode? = null,
    val lastErrorAt: Long? = null,
)
