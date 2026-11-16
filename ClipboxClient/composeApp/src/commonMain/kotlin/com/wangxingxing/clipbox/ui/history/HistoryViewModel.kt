/**
 * author : 王星星
 * date : 2026-11-16 18:05
 * email : 1099420259@qq.com
 * description : 历史页状态机：把设置流与本地记录流合并为展示状态；发送与置顶只调用 ClipRepository，不触网、不直接访问 Room
 */
package com.wangxingxing.clipbox.ui.history

import com.wangxingxing.clipbox.repository.ClipRepository
import com.wangxingxing.clipbox.repository.SendResult
import com.wangxingxing.clipbox.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val clipRepository: ClipRepository,
    settingsRepository: SettingsRepository,
    private val scope: CoroutineScope,
) {

    val state: StateFlow<HistoryUiState> = combine(
        settingsRepository.settings,
        clipRepository.observeClips(),
    ) { settings, clips ->
        HistoryUiState(
            isLoaded = true,
            paired = !settings.deviceToken.isNullOrEmpty(),
            deviceId = settings.deviceId.orEmpty(),
            clips = clips,
        )
    }.stateIn(
        scope = scope,
        // 切到其它一级页时暂停观察，返回后由 Room 立即补发最新列表
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HistoryUiState(),
    )

    private val _events = Channel<HistoryEvent>(Channel.BUFFERED)
    val events: Flow<HistoryEvent> = _events.receiveAsFlow()

    // 本地写入是毫秒级操作，仅用于拦截连点造成的重复记录
    private var isSending = false

    fun send(content: String) {
        if (isSending) return
        isSending = true
        scope.launch {
            try {
                when (clipRepository.send(content)) {
                    is SendResult.Success -> _events.send(HistoryEvent.Sent)
                    SendResult.TooLarge -> _events.send(HistoryEvent.TooLarge)
                    SendResult.Empty -> Unit
                }
            } finally {
                isSending = false
            }
        }
    }

    fun setPinned(id: String, pinned: Boolean) {
        scope.launch { clipRepository.pin(id, pinned) }
    }
}
