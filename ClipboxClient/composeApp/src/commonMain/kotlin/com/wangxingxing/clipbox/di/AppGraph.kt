/**
 * author : 王星星
 * date : 2026-11-16 17:14
 * email : 1099420259@qq.com
 * description : Koin 装配落地前的临时依赖图；只承载本卡需要的设置、配对与记录仓库，平台 actual 负责进程级单例与默认设备名
 */
package com.wangxingxing.clipbox.di

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.wangxingxing.clipbox.repository.ClipRepository
import com.wangxingxing.clipbox.repository.PairingRepository
import com.wangxingxing.clipbox.settings.SettingsRepository

internal class AppGraph(
    val settingsRepository: SettingsRepository,
    val pairingRepository: PairingRepository,
    val clipRepository: ClipRepository,
    val defaultServerUrl: String,
    val defaultDeviceName: String,
)

internal val LocalAppGraph = staticCompositionLocalOf<AppGraph> {
    error("AppGraph is not provided")
}

@Composable
internal expect fun rememberAppGraph(): AppGraph
