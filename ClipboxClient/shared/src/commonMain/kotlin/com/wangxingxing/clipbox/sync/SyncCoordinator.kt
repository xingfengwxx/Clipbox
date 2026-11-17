/**
 * author : 王星星
 * date : 2026-11-17 13:28
 * email : 1099420259@qq.com
 * description : 同步运行时编排；观察配对令牌启停 Outbox 与 WS、提供前台恢复与手动重试入口，并保证平台重复调用幂等。注意：配对完成前不得建立 WS 连接；后台断开只影响 WS，Outbox 保留队列等待下次唤醒
 */
package com.wangxingxing.clipbox.sync

import com.wangxingxing.clipbox.model.ApiFailure
import com.wangxingxing.clipbox.network.SyncSocket
import com.wangxingxing.clipbox.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class SyncCoordinator(
    private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val syncEngine: SyncEngine,
    private val outboxWorker: OutboxWorker,
    private val socket: SyncSocket,
    private val statusReporter: SyncStatusReporter,
) {

    private var started = false

    fun start() {
        if (started) return
        started = true
        outboxWorker.start(scope)
        socket.start(scope)
        scope.launch {
            settingsRepository.settings
                .map { it.deviceToken }
                .distinctUntilChanged()
                .collect { token ->
                    if (token.isNullOrEmpty()) {
                        socket.onBackground()
                        statusReporter.onUnauthorized()
                    } else {
                        // 重新配对后解除 Outbox 的 401 暂停，并立刻补拉 + 连接
                        outboxWorker.onPaired()
                        socket.onForeground()
                    }
                }
        }
    }

    /** iOS scenePhase 等前台恢复入口；Android / Desktop 由进程生命周期自然保持连接 */
    fun onForeground() {
        socket.onForeground()
    }

    /** 切后台立即断开 WS；不回写状态，避免把「已同步」误显示为离线 */
    fun onBackground() {
        socket.onBackground()
    }

    /** 状态条重试与手动同步：先 REST 增量拉取，再实际重放 Outbox，重复触发由 SyncRepository 合并 */
    suspend fun syncNow(): SyncNowResult {
        statusReporter.onSyncing()
        val result = when (val pull = syncEngine.pull()) {
            is PullResult.Success -> {
                val report = outboxWorker.replayDue()
                if (report.waitingForPairing) {
                    statusReporter.onUnauthorized()
                    SyncNowResult.WaitingForPairing
                } else {
                    statusReporter.onSynced()
                    SyncNowResult.Success(
                        appliedItems = pull.appliedItems,
                        resynced = pull.resynced,
                        outboxApplied = report.applied,
                    )
                }
            }

            is PullResult.Failure -> {
                if (pull.failure is ApiFailure.Unauthorized) {
                    statusReporter.onUnauthorized()
                } else {
                    statusReporter.onOffline()
                }
                SyncNowResult.Failure(pull.failure)
            }

            PullResult.WaitingForPairing -> {
                statusReporter.onUnauthorized()
                SyncNowResult.WaitingForPairing
            }
        }
        // 拉取失败时仍唤醒 Outbox 的重试循环，让队列按自身退避策略继续尝试
        outboxWorker.requestReplay()
        socket.requestConnect()
        return result
    }
}
