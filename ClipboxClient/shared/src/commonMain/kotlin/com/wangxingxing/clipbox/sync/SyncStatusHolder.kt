/**
 * author : 王星星
 * date : 2026-11-17 13:24
 * email : 1099420259@qq.com
 * description : 同步状态唯一推导点；把 WS 连接阶段与 Outbox 待同步 / 失败数量聚合为 SyncStatus，UI 只观察结果、不自行拼接。注意：Unauthorized 在界面层映射为「未配对」；失败数量优先于待同步数量展示
 */
package com.wangxingxing.clipbox.sync

import com.wangxingxing.clipbox.db.ClipboxDatabase
import com.wangxingxing.clipbox.model.SyncStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * 同步内核向状态条上报连接阶段的窄接口；实现方不得在此做网络或数据库读写。
 * 默认空实现供不关心状态的测试与只跑拉取的场景使用。
 */
interface SyncStatusReporter {
    fun onSyncing() {}
    fun onResyncing() {}
    fun onSynced() {}
    fun onOffline() {}
    fun onUnauthorized() {}

    companion object {
        val None: SyncStatusReporter = object : SyncStatusReporter {}
    }
}

class SyncStatusHolder(
    database: ClipboxDatabase,
    scope: CoroutineScope,
) : SyncStatusReporter {

    private enum class Connection { Idle, Syncing, Resyncing, Connected, Offline, Unauthorized }

    private val connection = MutableStateFlow(Connection.Idle)

    val status: StateFlow<SyncStatus> = combine(
        connection,
        database.outboxDao().observePendingCount(),
        database.outboxDao().observeFailedCount(),
    ) { conn, pending, failed ->
        when {
            conn == Connection.Unauthorized -> SyncStatus.Unauthorized
            conn == Connection.Resyncing -> SyncStatus.Resyncing
            conn == Connection.Syncing -> SyncStatus.Syncing
            // 离线优先于计数展示：用户先要知道当前没有网络，而不是还有多少条没发
            conn == Connection.Offline -> SyncStatus.Offline
            failed > 0 -> SyncStatus.Failed(failed)
            pending > 0 -> SyncStatus.Pending(pending)
            else -> SyncStatus.Idle
        }
    }.stateIn(scope, SharingStarted.Eagerly, SyncStatus.Idle)

    override fun onSyncing() {
        connection.value = Connection.Syncing
    }

    override fun onResyncing() {
        connection.value = Connection.Resyncing
    }

    override fun onSynced() {
        connection.value = Connection.Connected
    }

    override fun onOffline() {
        connection.value = Connection.Offline
    }

    override fun onUnauthorized() {
        connection.value = Connection.Unauthorized
    }
}
