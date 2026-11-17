/**
 * author : 王星星
 * date : 2026-11-17 15:33
 * email : 1099420259@qq.com
 * description : 设置页状态机；聚合设置持久值、同步状态与最近同步时间，改址与解绑统一交给 AppContainer 重建会话。
 *               注意：已配对时改址必须先把新地址落盘再解绑，否则重建出的会话仍指向旧服务器
 */
package com.wangxingxing.clipbox.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wangxingxing.clipbox.app.AppContainer
import com.wangxingxing.clipbox.model.ApiFailure
import com.wangxingxing.clipbox.repository.SyncRepository
import com.wangxingxing.clipbox.settings.ServerUrlValidation
import com.wangxingxing.clipbox.settings.ServerUrlValidator
import com.wangxingxing.clipbox.settings.SettingsRepository
import com.wangxingxing.clipbox.settings.ThemeMode
import com.wangxingxing.clipbox.sync.SyncNowResult
import com.wangxingxing.clipbox.sync.SyncStatusHolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val container: AppContainer,
    private val settingsRepository: SettingsRepository,
    private val syncRepository: SyncRepository,
    syncStatusHolder: SyncStatusHolder,
) : ViewModel() {

    private val _state = MutableStateFlow(
        SettingsUiState(
            isDesktop = container.isDesktop,
            isDebugBuild = container.isDebugBuild,
            versionName = container.versionName,
            buildType = container.buildType,
        )
    )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _state.update {
                    it.copy(
                        loaded = true,
                        paired = !settings.deviceToken.isNullOrEmpty(),
                        serverUrl = settings.serverUrl ?: container.initialServerUrl(),
                        deviceName = settings.deviceName.orEmpty(),
                        // 只展示摘要，完整令牌永远不进入界面
                        deviceId = settings.deviceId.orEmpty(),
                        autoPaste = settings.autoPaste,
                        autoClipboardSend = settings.autoClipboardSend,
                        themeMode = settings.themeMode,
                        language = settings.appLanguage,
                        diagnosticLoggingEnabled = settings.diagnosticLoggingEnabled,
                    )
                }
            }
        }
        viewModelScope.launch {
            syncStatusHolder.status.collect { status ->
                _state.update { it.copy(syncStatus = status) }
            }
        }
        viewModelScope.launch {
            syncRepository.lastSyncAt.collect { at ->
                _state.update { it.copy(lastSyncAt = at) }
            }
        }
    }

    fun syncNow() {
        if (_state.value.isSyncing) return
        _state.update { it.copy(isSyncing = true) }
        viewModelScope.launch {
            val event = try {
                when (val result = syncRepository.syncNow()) {
                    is SyncNowResult.Success -> SettingsEvent.SyncSucceeded
                    SyncNowResult.WaitingForPairing -> SettingsEvent.SyncUnpaired
                    is SyncNowResult.Failure -> when (result.failure) {
                        ApiFailure.Offline -> SettingsEvent.SyncOffline
                        is ApiFailure.Unauthorized -> SettingsEvent.SyncUnauthorized
                        else -> SettingsEvent.SyncFailed
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                SettingsEvent.SyncFailed
            } finally {
                _state.update { it.copy(isSyncing = false) }
            }
            _events.send(event)
        }
    }

    /**
     * 已配对时改址会解除配对：点击保存前由页面弹出二次确认，确认后再调用本方法。
     * 新地址先落盘，后续 unpair 重建会话即可直接使用新服务器。
     */
    fun saveServerUrl(raw: String) {
        val url = (ServerUrlValidator.validate(raw) as? ServerUrlValidation.Valid)?.url ?: return
        viewModelScope.launch {
            val wasPaired = _state.value.paired
            val changed = url != _state.value.serverUrl
            settingsRepository.setServerUrl(url)
            if (wasPaired && changed) {
                container.unpair()
            } else {
                _state.update { it.copy(serverUrl = url) }
                _events.send(SettingsEvent.ServerUrlSaved)
            }
        }
    }

    fun unpair() {
        viewModelScope.launch { container.unpair() }
    }

    fun setAutoPaste(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoPaste(enabled) }
    }

    fun setAutoClipboardSend(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoClipboardSend(enabled) }
    }

    fun setDiagnosticLogging(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDiagnosticLoggingEnabled(enabled) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }
}
