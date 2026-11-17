/**
 * author : 王星星
 * date : 2026-11-16 17:15
 * email : 1099420259@qq.com
 * description : iOS 临时依赖图；进程级单例避免重复创建数据库与 WS 连接，设备名由 IosDeviceInfo 提供，服务器地址按二进制是否为 debug 区分。
 *               注意：Compose 与宿主生命周期桥必须复用同一单例，否则会出现两条 WS 连接与两个数据库。
 */
package com.wangxingxing.clipbox.di

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.wangxingxing.clipbox.IosDeviceInfo
import com.wangxingxing.clipbox.config.BundledConfig
import com.wangxingxing.clipbox.repository.PairingRepository
import com.wangxingxing.clipbox.settings.createSettingsRepository
import com.wangxingxing.clipbox.sync.createSyncRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

@Composable
internal actual fun rememberAppGraph(): AppGraph = remember { IosAppGraph.instance }

/** 供 IosAppLifecycle 等宿主入口取得同一进程级依赖图，避免重复创建数据库与同步运行时。 */
internal fun iosAppGraph(): AppGraph = IosAppGraph.instance

private object IosAppGraph {

    val instance: AppGraph by lazy { create() }

    @OptIn(ExperimentalNativeApi::class)
    private fun create(): AppGraph {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val serverUrl = BundledConfig.serverUrl(Platform.isDebugBinary)
        val settingsRepository = createSettingsRepository(scope, diagnosticLoggingDefault = true)
        val runtime = createSyncRuntime(
            scope = scope,
            settingsRepository = settingsRepository,
            defaultServerUrl = serverUrl,
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
            defaultServerUrl = serverUrl,
            defaultDeviceName = IosDeviceInfo.defaultDeviceName,
        )
    }
}
