/**
 * author : 王星星
 * date : 2026-11-17 15:12
 * email : 1099420259@qq.com
 * description : Desktop 剪贴板自动粘贴与自动发送控制器；只消费 WS 已成功应用的 clip.created，REST 补拉不写剪贴板。
 *               移动端由 isDesktop=false 关闭全部自动行为，仅保留用户显式操作。注意：监听回环只由 ClipboardGuard 的 lastAppliedHash 时间窗决定
 */
package com.wangxingxing.clipbox.clipboard

import com.wangxingxing.clipbox.model.ClipDto
import com.wangxingxing.clipbox.platform.ClipboardService
import com.wangxingxing.clipbox.platform.ClipboardWriteResult
import com.wangxingxing.clipbox.repository.ClipRepository
import com.wangxingxing.clipbox.repository.SendResult
import com.wangxingxing.clipbox.settings.SettingsRepository
import com.wangxingxing.clipbox.util.TextSize
import com.wangxingxing.clipbox.util.clipNowMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

sealed interface ClipboardEvent {
    data object AutoPasteFailed : ClipboardEvent
    data object AutoSendTooLarge : ClipboardEvent
}

class ClipboardController(
    private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val clipRepository: ClipRepository,
    private val clipboardService: ClipboardService,
    private val realtimeCreated: SharedFlow<ClipDto>,
    private val isDesktop: Boolean,
    private val clock: () -> Long = ::clipNowMillis,
    private val wakeOutbox: () -> Unit = {},
) {

    private val guard = ClipboardGuard()
    private val debouncer = ClipboardDebouncer()
    private val eventFlow = MutableSharedFlow<ClipboardEvent>(extraBufferCapacity = 8)
    private var watching = false
    private var settingsJob: Job? = null
    private var realtimeJob: Job? = null
    private var debounceJob: Job? = null
    private var lastTooLargeHash: Long? = null

    val events: SharedFlow<ClipboardEvent> = eventFlow.asSharedFlow()

    fun start() {
        if (!isDesktop) return
        if (settingsJob?.isActive == true) return
        settingsJob = scope.launch {
            settingsRepository.settings
                .map { it.autoClipboardSend }
                .distinctUntilChanged()
                .collect { enabled ->
                    if (enabled) startWatcher() else stopWatcher()
                }
        }
        realtimeJob = scope.launch {
            realtimeCreated.collect { clip -> handleRealtimeCreated(clip) }
        }
    }

    fun stop() {
        settingsJob?.cancel()
        settingsJob = null
        realtimeJob?.cancel()
        realtimeJob = null
        stopWatcher()
        guard.reset()
    }

    private fun startWatcher() {
        if (watching || !isDesktop) return
        watching = true
        clipboardService.startWatching { text ->
            scope.launch { offerWatchedText(text) }
        }
    }

    private fun stopWatcher() {
        if (!watching) return
        watching = false
        clipboardService.stopWatching()
        debounceJob?.cancel()
        debounceJob = null
        debouncer.reset()
    }

    private fun offerWatchedText(text: String) {
        if (!watching) return
        val now = clock()
        debouncer.offer(text, now)
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(DEBOUNCE_MILLIS)
            val ready = debouncer.poll(clock()) ?: return@launch
            upload(ready)
        }
    }

    private suspend fun upload(text: String) {
        if (!watching || !isDesktop) return
        if (text.isBlank()) return
        val bytes = TextSize.utf8ByteCount(text)
        if (bytes > TextSize.MAX_CLIP_UTF8_BYTES) {
            reportTooLarge(text)
            return
        }
        lastTooLargeHash = null
        if (guard.shouldSkipUpload(text, clock())) return
        when (clipRepository.send(text)) {
            is SendResult.Success -> {
                guard.onSent(text)
                // 自动发送没有 UI 调用方，必须显式唤醒重放循环，否则记录会滞留到下一次用户操作
                wakeOutbox()
            }
            SendResult.TooLarge -> reportTooLarge(text)
            SendResult.Empty -> Unit
        }
    }

    private suspend fun handleRealtimeCreated(clip: ClipDto) {
        if (!isDesktop || clip.deleted) return
        val settings = settingsRepository.current()
        if (!settings.autoPaste) return
        val content = clip.content ?: return
        // 本机发送的记录可能被服务端回显，不能覆盖本机剪贴板
        if (!settings.deviceId.isNullOrEmpty() && clip.sourceDeviceId == settings.deviceId) return
        if (content.isEmpty()) return

        when (clipboardService.writeText(content)) {
            ClipboardWriteResult.Success -> guard.onRemoteApplied(content, clock())
            ClipboardWriteResult.Failed -> eventFlow.tryEmit(ClipboardEvent.AutoPasteFailed)
        }
    }

    private fun reportTooLarge(text: String) {
        val hash = ClipboardHash.of(text)
        if (lastTooLargeHash == hash) return
        lastTooLargeHash = hash
        eventFlow.tryEmit(ClipboardEvent.AutoSendTooLarge)
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 800L
    }
}
