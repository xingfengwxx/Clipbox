/**
 * author : 王星星
 * date : 2026-11-17 18:45
 * email : 1099420259@qq.com
 * description : Desktop 平台注入：AWT 剪贴板、stdout 日志、Room 与 DataStore 工厂；开发运行标记只由 run 任务注入，打包产物按 release 处理
 */
package com.wangxingxing.clipbox.di

import com.wangxingxing.clipbox.app.PlatformServices
import com.wangxingxing.clipbox.config.BundledConfig
import com.wangxingxing.clipbox.db.ClipboxDatabase
import com.wangxingxing.clipbox.db.createClipboxDatabase
import com.wangxingxing.clipbox.logging.DesktopLogSink
import com.wangxingxing.clipbox.logging.LogSink
import com.wangxingxing.clipbox.platform.ClipboardService
import com.wangxingxing.clipbox.platform.DesktopClipboardService
import com.wangxingxing.clipbox.settings.SettingsRepository
import com.wangxingxing.clipbox.settings.createSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.dsl.module

fun desktopPlatformModule(): Module = module {
    single<PlatformServices> { DesktopPlatformServices() }
}

private class DesktopPlatformServices : PlatformServices {

    private val isDebug = System.getProperty("clipbox.debugBuild") == "true"

    override val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    override val clipboardService: ClipboardService = DesktopClipboardService()
    override val logSink: LogSink = DesktopLogSink()
    override val defaultServerUrl = BundledConfig.serverUrl(isDebug)
    override val defaultDeviceName = "Desktop · ${System.getProperty("os.name")}"
    override val isDesktop = true
    override val isDebugBuild = isDebug
    override val buildType = if (isDebug) "debug" else "release"
    override val versionName = "1.0.0"

    override fun createDatabase(): ClipboxDatabase =
        createClipboxDatabase()

    // REQ-SET-011：诊断日志 debug 默认开、release 默认关；打包产物不带 debugBuild 标记，按 release 处理
    override fun createSettingsRepository(): SettingsRepository =
        createSettingsRepository(appScope, diagnosticLoggingDefault = isDebug)
}
