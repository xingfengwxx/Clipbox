/**
 * author : 王星星
 * date : 2026-11-17 18:45
 * email : 1099420259@qq.com
 * description : Android 平台注入：应用级协程作用域、系统剪贴板、Logcat 输出、Room 与 DataStore 工厂，以及设备名与构建类型判断；容器本身不感知 Android API
 */
package com.wangxingxing.clipbox.di

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import com.wangxingxing.clipbox.app.PlatformServices
import com.wangxingxing.clipbox.config.BundledConfig
import com.wangxingxing.clipbox.db.ClipboxDatabase
import com.wangxingxing.clipbox.db.createClipboxDatabase
import com.wangxingxing.clipbox.logging.AndroidLogSink
import com.wangxingxing.clipbox.logging.LogSink
import com.wangxingxing.clipbox.platform.AndroidClipboardService
import com.wangxingxing.clipbox.platform.ClipboardService
import com.wangxingxing.clipbox.settings.SettingsRepository
import com.wangxingxing.clipbox.settings.createSettingsRepository
import com.wangxingxing.clipbox.update.AndroidUpdateInstaller
import com.wangxingxing.clipbox.update.UpdateInstaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.dsl.module

fun androidPlatformModule(context: Context): Module = module {
    single<PlatformServices> { AndroidPlatformServices(context.applicationContext) }
}

private class AndroidPlatformServices(private val context: Context) : PlatformServices {

    private val isDebug = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    // 进程级作用域只承载设置观察与容器启动，关闭应用时随进程回收
    override val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override val clipboardService: ClipboardService = AndroidClipboardService(context)
    override val logSink: LogSink = AndroidLogSink()
    override val defaultServerUrl = BundledConfig.serverUrl(isDebug)
    override val defaultDeviceName = "Android · ${Build.MODEL}"
    override val isDesktop = false
    override val isDebugBuild = isDebug
    override val buildType = if (isDebug) "debug" else "release"

    override val versionName: String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: BundledConfig.appVersion

    override fun createDatabase(): ClipboxDatabase =
        createClipboxDatabase(context)

    override fun createSettingsRepository(): SettingsRepository =
        createSettingsRepository(
            context = context,
            scope = appScope,
            diagnosticLoggingDefault = isDebug,
        )

    override fun createUpdateInstaller(): UpdateInstaller = AndroidUpdateInstaller(context)
}
