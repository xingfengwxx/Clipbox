/**
 * author : 王星星
 * date : 2026-11-16 17:15
 * email : 1099420259@qq.com
 * description : iOS 临时依赖图；进程级懒加载，设备名取 UIDevice.model，诊断日志默认值待 T-CORE-13 收敛；服务器地址按二进制是否为 debug 区分
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
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform
import platform.UIKit.UIDevice

@Composable
internal actual fun rememberAppGraph(): AppGraph = remember { IosAppGraph.instance }

private object IosAppGraph {

    val instance: AppGraph by lazy { create() }

    @OptIn(ExperimentalNativeApi::class)
    private fun create(): AppGraph {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val settingsRepository = createSettingsRepository(scope, diagnosticLoggingDefault = true)
        return AppGraph(
            settingsRepository = settingsRepository,
            pairingRepository = PairingRepository(settingsRepository),
            clipRepository = createClipRepository(settingsRepository),
            defaultServerUrl = BundledConfig.serverUrl(Platform.isDebugBinary),
            defaultDeviceName = "iOS · ${UIDevice.currentDevice.model}",
        )
    }
}
