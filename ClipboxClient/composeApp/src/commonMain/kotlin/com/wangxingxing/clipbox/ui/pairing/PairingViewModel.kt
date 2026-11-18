/**
 * author : 王星星
 * date : 2026-11-16 17:18
 * email : 1099420259@qq.com
 * description : 配对页状态机；输入只生成状态、提交期禁止重复请求、429 按 Retry-After 启动冷却，成功后发出一次性事件；
 *               空库初始化与配对共用冷却与错误映射，成功后走同一重建会话事件
 */
package com.wangxingxing.clipbox.ui.pairing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wangxingxing.clipbox.repository.DEVICE_NAME_MAX_LENGTH
import com.wangxingxing.clipbox.repository.PAIRING_CODE_LENGTH
import com.wangxingxing.clipbox.repository.PairResult
import com.wangxingxing.clipbox.repository.PairingError
import com.wangxingxing.clipbox.repository.PairingRepository
import com.wangxingxing.clipbox.settings.isInsecureServerUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PairingViewModel(
    private val repository: PairingRepository,
    initialServerUrl: String,
    defaultDeviceName: String,
) : ViewModel() {

    private val _state = MutableStateFlow(
        PairingUiState(
            serverUrl = initialServerUrl,
            deviceName = defaultDeviceName,
            insecure = isInsecureServerUrl(initialServerUrl),
        )
    )
    val state: StateFlow<PairingUiState> = _state.asStateFlow()

    private val _pairedEvents = Channel<Unit>(Channel.BUFFERED)
    val pairedEvents: Flow<Unit> = _pairedEvents.receiveAsFlow()

    private var cooldownJob: Job? = null

    fun onCodeChange(raw: String) {
        val digits = raw.filter { it.isDigit() }.take(PAIRING_CODE_LENGTH)
        _state.update { it.copy(code = digits, error = null) }
    }

    fun onServerUrlChange(value: String) {
        _state.update {
            it.copy(
                serverUrl = value,
                insecure = isInsecureServerUrl(value),
                error = null,
            )
        }
    }

    fun onDeviceNameChange(value: String) {
        _state.update { it.copy(deviceName = value, error = null) }
    }

    fun submit() {
        val current = _state.value
        // 状态更新是同步的，重复点击时第二次读取已处于提交中或冷却期
        if (!current.canSubmit) return
        if (current.deviceName.trim().length > DEVICE_NAME_MAX_LENGTH) {
            _state.update { it.copy(error = PairingError.DeviceName) }
            return
        }

        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            val result = try {
                repository.pair(
                    serverUrl = current.serverUrl,
                    code = current.code,
                    deviceName = current.deviceName,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // 落盘等非网络异常也要回到可重试状态，避免按钮永久停在“配对中”
                PairResult.Failure(PairingError.Server)
            }

            when (result) {
                PairResult.Success -> {
                    _state.update { it.copy(isSubmitting = false) }
                    _pairedEvents.send(Unit)
                }

                is PairResult.Failure -> {
                    _state.update { it.copy(isSubmitting = false, error = result.error) }
                    (result.error as? PairingError.RateLimited)?.let { startCooldown(it.retryAfterSeconds) }
                }
            }
        }
    }

    /** 空库首次初始化；成功后与配对一致重建会话并进入历史页 */
    fun initialize() {
        val current = _state.value
        if (!current.canInitialize) return
        if (current.deviceName.trim().length > DEVICE_NAME_MAX_LENGTH) {
            _state.update { it.copy(error = PairingError.DeviceName) }
            return
        }

        _state.update { it.copy(isInitializing = true, error = null) }
        viewModelScope.launch {
            val result = try {
                repository.initialize(
                    serverUrl = current.serverUrl,
                    deviceName = current.deviceName,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                PairResult.Failure(PairingError.Server)
            }

            when (result) {
                PairResult.Success -> {
                    _state.update { it.copy(isInitializing = false) }
                    _pairedEvents.send(Unit)
                }

                is PairResult.Failure -> {
                    _state.update { it.copy(isInitializing = false, error = result.error) }
                    (result.error as? PairingError.RateLimited)?.let { startCooldown(it.retryAfterSeconds) }
                }
            }
        }
    }

    private fun startCooldown(retryAfterSeconds: Long?) {
        cooldownJob?.cancel()
        val seconds = (retryAfterSeconds ?: DEFAULT_COOLDOWN_SECONDS).coerceAtLeast(1L).toInt()
        cooldownJob = viewModelScope.launch {
            _state.update { it.copy(cooldownSeconds = seconds) }
            repeat(seconds) {
                delay(1_000)
                _state.update { it.copy(cooldownSeconds = (it.cooldownSeconds - 1).coerceAtLeast(0)) }
            }
        }
    }

    private companion object {
        const val DEFAULT_COOLDOWN_SECONDS = 60L
    }
}
