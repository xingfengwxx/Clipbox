/**
 * author : 王星星
 * date : 2026-11-16 17:15
 * email : 1099420259@qq.com
 * description : Desktop 临时依赖图；进程级懒加载，设备名取系统名称，诊断日志默认值待 T-CORE-13 收敛；服务器地址按 run 任务的开发标记区分 debug / release
 */
package com.wangxingxing.clipbox.di

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.wangxingxing.clipbox.config.BundledConfig
import com.wangxingxing.clipbox.repository.PairingRepository
import com.wangxingxing.clipbox.repository.createClipRepository
import com.wangxingxing.clipbox.settings.createSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Composable
internal actual fun rememberAppGraph(): AppGraph = remember { DesktopAppGraph.instance }

private object DesktopAppGraph {

    val instance: AppGraph by lazy { create() }

    private fun create(): AppGraph {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val settingsRepository = createSettingsRepository(scope, diagnosticLoggingDefault = true)
        return AppGraph(
            settingsRepository = settingsRepository,
            pairingRepository = PairingRepository(settingsRepository),
            clipRepository = createClipRepository(settingsRepository),
            defaultServerUrl = BundledConfig.serverUrl(isDevRun()),
            defaultDeviceName = "Desktop · ${System.getProperty("os.name")}",
        )
    }

    // 该标记只由构建脚本的 run 任务注入；打包产物没有它，按 release 处理
    private fun isDevRun(): Boolean = System.getProperty("clipbox.debugBuild") == "true"
}
