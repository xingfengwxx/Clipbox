/**
 * author : 王星星
 * date : 2026-11-16 14:01
 * email : 1099420259@qq.com
 * description : 同步状态条与设置页共用的状态集合；状态由 SyncEngine 推导，UI 不自行拼接
 */
package com.wangxingxing.clipbox.model

sealed interface SyncStatus {
    data object Idle : SyncStatus
    data object Syncing : SyncStatus
    data object Offline : SyncStatus
    data object Unauthorized : SyncStatus

    data class Pending(val count: Int) : SyncStatus
    data class Failed(val count: Int) : SyncStatus
}
