/**
 * author : 王星星
 * date : 2026-11-17 18:45
 * email : 1099420259@qq.com
 * description : iOS 平台注入：UIPasteboard 剪贴板、NSLog 输出、Room 与 DataStore 工厂；设备名由 IosDeviceInfo 提供，构建类型按二进制是否 debug 区分
 */
package com.wangxingxing.clipbox.di

import com.wangxingxing.clipbox.IosDeviceInfo
import com.wangxingxing.clipbox.app.PlatformServices
import com.wangxingxing.clipbox.config.BundledConfig
import com.wangxingxing.clipbox.db.ClipboxDatabase
import com.wangxingxing.clipbox.db.createClipboxDatabase
import com.wangxingxing.clipbox.logging.IosLogSink
import com.wangxingxing.clipbox.logging.LogSink
import com.wangxingxing.clipbox.platform.ClipboardService
import com.wangxingxing.clipbox.platform.IosClipboardService
import com.wangxingxing.clipbox.settings.SettingsRepository
import com.wangxingxing.clipbox.settings.createSettingsRepository
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSBundle

fun iosPlatformModule(): Module = module {
    single<PlatformServices> { IosPlatformServices() }
}

private class IosPlatformServices : PlatformServices {

    @OptIn(ExperimentalNativeApi::class)
    private val isDebug = Platform.isDebugBinary

    override val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    override val clipboardService: ClipboardService = IosClipboardService()
    override val logSink: LogSink = IosLogSink()
    override val defaultServerUrl = BundledConfig.serverUrl(isDebug)
    override val defaultDeviceName = IosDeviceInfo.defaultDeviceName
    override val isDesktop = false
    override val isIos = true
    override val isDebugBuild = isDebug
    override val buildType = if (isDebug) "debug" else "release"

    override val versionName: String =
        NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String
            ?: BundledConfig.appVersion

    override fun createDatabase(): ClipboxDatabase =
        createClipboxDatabase()

    // REQ-SET-011：诊断日志 debug 默认开、release 默认关；release 开启时必须走设置页的风险确认
    override fun createSettingsRepository(): SettingsRepository =
        createSettingsRepository(appScope, diagnosticLoggingDefault = isDebug)
}
