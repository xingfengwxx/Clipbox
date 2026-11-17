/**
 * author : 王星星
 * date : 2026-11-16 17:15
 * email : 1099420259@qq.com
 * description : Android 临时依赖图；进程级单例避免同进程重复创建 DataStore 与数据库，诊断日志默认值与默认服务器地址跟随 debuggable 构建
 */
package com.wangxingxing.clipbox.di

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.wangxingxing.clipbox.config.BundledConfig
import com.wangxingxing.clipbox.repository.PairingRepository
import com.wangxingxing.clipbox.repository.createClipRepository
import com.wangxingxing.clipbox.settings.createSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Composable
internal actual fun rememberAppGraph(): AppGraph {
    val context = LocalContext.current.applicationContext
    return remember(context) { AndroidAppGraph.instance(context) }
}

private object AndroidAppGraph {

    @Volatile
    private var graph: AppGraph? = null

    fun instance(context: Context): AppGraph = graph ?: synchronized(this) {
        graph ?: create(context).also { graph = it }
    }

    private fun create(context: Context): AppGraph {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val isDebug = context.isDebuggable()
        val settingsRepository = createSettingsRepository(
            context = context,
            scope = scope,
            diagnosticLoggingDefault = isDebug,
        )
        return AppGraph(
            settingsRepository = settingsRepository,
            pairingRepository = PairingRepository(settingsRepository),
            clipRepository = createClipRepository(context, settingsRepository),
            defaultServerUrl = BundledConfig.serverUrl(isDebug),
            defaultDeviceName = "Android · ${Build.MODEL}",
        )
    }

    private fun Context.isDebuggable(): Boolean =
        applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
}
