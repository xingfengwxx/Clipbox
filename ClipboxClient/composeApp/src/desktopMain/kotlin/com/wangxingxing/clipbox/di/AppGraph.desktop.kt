/**
 * author : 王星星
 * date : 2026-11-16 17:15
 * email : 1099420259@qq.com
 * description : Desktop 临时依赖图；进程级单例避免重复创建数据库与 WS 连接，设备名取系统名称，服务器地址按 run 任务的开发标记区分 debug / release
 */
package com.wangxingxing.clipbox.di

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.wangxingxing.clipbox.config.BundledConfig
import com.wangxingxing.clipbox.logging.AppLogger
import com.wangxingxing.clipbox.logging.DesktopLogSink
import com.wangxingxing.clipbox.repository.PairingRepository
import com.wangxingxing.clipbox.settings.createSettingsRepository
import com.wangxingxing.clipbox.sync.createSyncRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Composable
internal actual fun rememberAppGraph(): AppGraph = remember { DesktopAppGraph.instance }

private object DesktopAppGraph {

    val instance: AppGraph by lazy { create() }

    private fun create(): AppGraph {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val isDebug = isDevRun()
        val serverUrl = BundledConfig.serverUrl(isDebug)
        val appLogger = AppLogger(DesktopLogSink(), isDebugBuild = isDebug)
        val settingsRepository = createSettingsRepository(scope, diagnosticLoggingDefault = true)
        val runtime = createSyncRuntime(
            scope = scope,
            settingsRepository = settingsRepository,
            defaultServerUrl = serverUrl,
            appLogger = appLogger,
        )
        runtime.syncCoordinator.start()
        return AppGraph(
            settingsRepository = settingsRepository,
            pairingRepository = PairingRepository(settingsRepository),
            clipRepository = runtime.clipRepository,
            shareRepository = runtime.shareRepository,
            outboxWorker = runtime.outboxWorker,
            syncCoordinator = runtime.syncCoordinator,
            syncStatusHolder = runtime.syncStatusHolder,
            clipboardService = runtime.clipboardService,
            clipboardController = runtime.clipboardController,
            appLogger = appLogger,
            defaultServerUrl = serverUrl,
            defaultDeviceName = "Desktop · ${System.getProperty("os.name")}",
            isDesktop = true,
        )
    }

    // 该标记只由构建脚本的 run 任务注入；打包产物没有它，按 release 处理
    private fun isDevRun(): Boolean = System.getProperty("clipbox.debugBuild") == "true"
}
