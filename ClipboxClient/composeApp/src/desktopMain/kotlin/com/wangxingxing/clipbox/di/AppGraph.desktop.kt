/**
 * author : 王星星
 * date : 2026-11-16 17:15
 * email : 1099420259@qq.com
 * description : Desktop 临时依赖图；进程级懒加载，设备名取系统名称，诊断日志默认值待 T-CORE-13 收敛
 */
package com.wangxingxing.clipbox.di

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.wangxingxing.clipbox.config.BundledConfig
import com.wangxingxing.clipbox.repository.PairingRepository
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
            defaultServerUrl = BundledConfig.DEFAULT_SERVER_URL,
            defaultDeviceName = "Desktop · ${System.getProperty("os.name")}",
        )
    }
}
