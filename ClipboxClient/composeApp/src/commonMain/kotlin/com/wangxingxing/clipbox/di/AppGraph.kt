/**
 * author : 王星星
 * date : 2026-11-16 17:14
 * email : 1099420259@qq.com
 * description : Koin 装配落地前的临时依赖图；承载设置、配对、记录与分享仓库以及已启动的同步运行时，平台 actual 负责进程级单例与默认设备名
 */
package com.wangxingxing.clipbox.di

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.wangxingxing.clipbox.repository.ClipRepository
import com.wangxingxing.clipbox.repository.PairingRepository
import com.wangxingxing.clipbox.repository.ShareRepository
import com.wangxingxing.clipbox.settings.SettingsRepository
import com.wangxingxing.clipbox.sync.OutboxWorker
import com.wangxingxing.clipbox.sync.SyncCoordinator
import com.wangxingxing.clipbox.sync.SyncStatusHolder

internal class AppGraph(
    val settingsRepository: SettingsRepository,
    val pairingRepository: PairingRepository,
    val clipRepository: ClipRepository,
    val shareRepository: ShareRepository,
    val outboxWorker: OutboxWorker,
    val syncCoordinator: SyncCoordinator,
    val syncStatusHolder: SyncStatusHolder,
    val defaultServerUrl: String,
    val defaultDeviceName: String,
)

internal val LocalAppGraph = staticCompositionLocalOf<AppGraph> {
    error("AppGraph is not provided")
}

@Composable
internal expect fun rememberAppGraph(): AppGraph
