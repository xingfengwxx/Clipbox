/**
 * author : 王星星
 * date : 2026-11-16 18:05
 * email : 1099420259@qq.com
 * description : 历史页状态机：把设置流、本地记录流、300ms 防抖搜索与同步状态合并为展示状态；发送、置顶、删除、重试只调用 ClipRepository，写入成功后显式唤醒 Outbox 立即重放；状态条重试经 SyncCoordinator 先补拉再唤醒同步
 */
package com.wangxingxing.clipbox.ui.history

import com.wangxingxing.clipbox.model.Clip
import com.wangxingxing.clipbox.repository.ClipRepository
import com.wangxingxing.clipbox.repository.SendResult
import com.wangxingxing.clipbox.settings.SettingsRepository
import com.wangxingxing.clipbox.sync.OutboxWorker
import com.wangxingxing.clipbox.sync.SyncCoordinator
import com.wangxingxing.clipbox.sync.SyncStatusHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val clipRepository: ClipRepository,
    settingsRepository: SettingsRepository,
    private val outboxWorker: OutboxWorker,
    syncStatusHolder: SyncStatusHolder,
    private val syncCoordinator: SyncCoordinator,
    private val scope: CoroutineScope,
) {

    private val query = MutableStateFlow("")

    val state: StateFlow<HistoryUiState> = combine(
        settingsRepository.settings,
        clipRepository.observeClips(),
        query,
        // 只查本地 Room；防抖避免每次按键都切换查询流
        query.debounce(SEARCH_DEBOUNCE_MILLIS).flatMapLatest { raw ->
            if (raw.isBlank()) flowOf(emptyList()) else clipRepository.search(raw.trim())
        },
        syncStatusHolder.status,
    ) { settings, clips, rawQuery, results, status ->
        val searching = rawQuery.isNotBlank()
        HistoryUiState(
            isLoaded = true,
            paired = !settings.deviceToken.isNullOrEmpty(),
            deviceId = settings.deviceId.orEmpty(),
            query = rawQuery,
            searching = searching,
            clips = if (searching) results else clips,
            syncStatus = status,
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

    fun setQuery(text: String) {
        query.value = text
    }

    fun send(content: String) {
        if (isSending) return
        isSending = true
        scope.launch {
            try {
                when (clipRepository.send(content)) {
                    is SendResult.Success -> {
                        outboxWorker.requestReplay()
                        _events.send(HistoryEvent.Sent)
                    }

                    SendResult.TooLarge -> _events.send(HistoryEvent.TooLarge)
                    SendResult.Empty -> Unit
                }
            } finally {
                isSending = false
            }
        }
    }

    fun setPinned(id: String, pinned: Boolean) {
        scope.launch {
            if (clipRepository.pin(id, pinned)) outboxWorker.requestReplay()
        }
    }

    fun delete(id: String) {
        scope.launch {
            // 记录已被删除或不存在时无需重放；删除确认只由 UI 负责
            if (clipRepository.delete(id)) outboxWorker.requestReplay()
        }
    }

    fun retry(id: String) {
        scope.launch {
            if (clipRepository.retry(id)) outboxWorker.requestReplay()
        }
    }

    // 状态条重试：先 REST 补拉再唤醒 Outbox 与 WS，失败只反映在状态条上，不打断当前操作
    fun syncNow() {
        scope.launch { syncCoordinator.syncNow() }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 300L
    }
}
