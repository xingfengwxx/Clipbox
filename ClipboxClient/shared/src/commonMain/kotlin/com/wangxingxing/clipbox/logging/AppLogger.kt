/**
 * author : 王星星
 * date : 2026-11-17 16:08
 * email : 1099420259@qq.com
 * description : 应用日志入口；开关关闭时零输出，开启后输出白名单字段并统一脱敏。开关跟随 DataStore 即时生效，诊断模式也不会输出明文令牌。
 *               注意：release 首次开启需要二次确认，由设置页读取 requiresConfirmation 决定
 */
package com.wangxingxing.clipbox.logging

import com.wangxingxing.clipbox.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

class AppLogger(
    private val sink: LogSink,
    private val isDebugBuild: Boolean,
    initialEnabled: Boolean = isDebugBuild,
) {

    private val enabledState = MutableStateFlow(initialEnabled)

    @Volatile
    private var enabledValue = initialEnabled

    val enabled: StateFlow<Boolean> = enabledState.asStateFlow()

    /** release 构建手动开启前必须由设置页弹出风险确认 */
    val requiresConfirmation: Boolean get() = !isDebugBuild

    fun setEnabled(enabled: Boolean) {
        enabledValue = enabled
        enabledState.value = enabled
    }

    /** 跟随设置持久值即时生效；重复调用只会叠加收集协程，生产装配只调用一次 */
    fun observe(settingsRepository: SettingsRepository, scope: CoroutineScope) {
        scope.launch {
            settingsRepository.settings
                .map { it.diagnosticLoggingEnabled }
                .distinctUntilChanged()
                .collect(::setEnabled)
        }
    }

    fun log(event: LogEvent, level: LogLevel = LogLevel.INFO) {
        if (!enabledValue) return
        sink.write(level, TAG, LogRedactor.redact(render(event)))
    }

    private fun render(event: LogEvent): String = when (event) {
        is LogEvent.Network -> buildString {
            append("NET ")
            append(event.method)
            append(' ')
            append(event.pathTemplate)
            append(" status=")
            append(event.status ?: "-")
            append(" durationMs=")
            append(event.durationMillis)
            append(" operation=")
            append(event.operation)
            event.errorCode?.let { append(" errorCode=").append(it) }
            event.clipId?.let { append(" clipId=").append(it) }
            event.authorization?.let { append(" authorization=").append(it) }
            event.requestBody?.let { append(" request=").append(it) }
            event.responseBody?.let { append(" response=").append(it) }
        }

        is LogEvent.Socket -> buildString {
            append("WS ")
            append(event.event)
            append(" detail=")
            append(event.detail)
            append(" operation=")
            append(event.operation)
            event.closeCode?.let { append(" closeCode=").append(it) }
            event.authToken?.let { append(" token=").append(it) }
        }

        is LogEvent.Business -> buildString {
            append("APP ")
            append(event.operation)
            event.errorCode?.let { append(" errorCode=").append(it) }
            event.clipId?.let { append(" clipId=").append(it) }
            event.durationMillis?.let { append(" durationMs=").append(it) }
        }
    }

    private companion object {
        const val TAG = "Clipbox"
    }
}
