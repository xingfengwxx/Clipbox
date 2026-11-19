/**
 * author : 王星星
 * date : 2026-11-19 11:20
 * email : 1099420259@qq.com
 * description : 进程级更新状态机；持有官方更新 API、自动检查一次、红点、下载 / 校验 / 安装编排。
 *               注意：控制器独立于 AppSession，配对、改址、解绑都不得重建它；自动检查失败静默，下载状态不因弹窗关闭而取消
 */
package com.wangxingxing.clipbox.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlin.concurrent.Volatile

class UpdateController(
    private val scope: CoroutineScope,
    private val currentVersion: String,
    private val installer: UpdateInstaller?,
    createApi: () -> UpdateApi,
    private val autoCheckEnabled: suspend () -> Boolean,
) {

    private val api: UpdateApi = createApi()

    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private val _hasUnseenUpdate = MutableStateFlow(false)
    val hasUnseenUpdate: StateFlow<Boolean> = _hasUnseenUpdate.asStateFlow()

    private val _dialogVisible = MutableStateFlow(false)
    val dialogVisible: StateFlow<Boolean> = _dialogVisible.asStateFlow()

    private val checkLock = Mutex()

    @Volatile
    private var autoCheckDone = false

    private var checkJob: Job? = null
    private var downloadJob: Job? = null

    private var offer: Offer? = null

    /** 已发现的可安装版本；下载失败重试时复用，不重新检查 */
    private data class Offer(val descriptor: UpdateDescriptor, val platform: UpdatePlatform)

    /** 每次进程只触发一次自动检查；开关关闭时同样视为已完成，之后只留手动检查 */
    fun onAppStart(): Job? {
        if (autoCheckDone) return null
        autoCheckDone = true
        return scope.launch {
            if (!autoCheckEnabled()) return@launch
            runCheck(silent = true)
        }.also { checkJob = it }
    }

    fun checkManually(): Job {
        return scope.launch { runCheck(silent = false) }.also { checkJob = it }
    }

    /** 红点只存内存：打开弹窗即清除，检查结果本身保持不变 */
    fun onDialogOpened() {
        _hasUnseenUpdate.value = false
    }

    fun openDialog() {
        _dialogVisible.value = true
        onDialogOpened()
    }

    /** 只关闭弹层；下载、校验与 Ready 状态保持不变 */
    fun closeDialog() {
        _dialogVisible.value = false
    }

    /** 下载并校验当前 offer；已有下载在途时返回 null，不重复启动 */
    fun startDownload(): Job? {
        val current = _state.value
        if (current is UpdateUiState.Downloading || current is UpdateUiState.Verifying || current is UpdateUiState.Ready) return null
        val offer = offer ?: return null
        val installer = installer ?: return null
        downloadJob?.cancel()
        downloadJob = scope.launch {
            val totalBytes = offer.platform.sizeBytes ?: 0L
            _state.value = UpdateUiState.Downloading(offer.descriptor, offer.platform, 0L, totalBytes)
            try {
                val downloaded = installer.download(offer.platform) { downloadedBytes, reportedTotal ->
                    val snapshot = _state.value
                    if (snapshot is UpdateUiState.Downloading && snapshot.descriptor.version == offer.descriptor.version) {
                        _state.value = snapshot.copy(
                            downloadedBytes = downloadedBytes,
                            totalBytes = if (reportedTotal > 0) reportedTotal else snapshot.totalBytes,
                        )
                    }
                }
                if (!downloaded) {
                    _state.value = UpdateUiState.Failed(UpdateError.DOWNLOAD_FAILED, offer.descriptor)
                    return@launch
                }
                _state.value = UpdateUiState.Verifying(offer.descriptor, offer.platform)
                val verified = installer.verify(offer.platform.sha256.orEmpty())
                _state.value = if (verified) {
                    UpdateUiState.Ready(offer.descriptor, offer.platform)
                } else {
                    UpdateUiState.Failed(UpdateError.CHECKSUM_MISMATCH, offer.descriptor)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                // 安装器可能把取消转成 IO 异常；协程已取消时不得再写 Failed
                currentCoroutineContext().ensureActive()
                _state.value = UpdateUiState.Failed(UpdateError.DOWNLOAD_FAILED, offer.descriptor)
            }
        }
        return downloadJob
    }

    /** 取消下载并回到可再次下载的状态；Ready、空闲或没有 offer 时为空操作 */
    fun cancelDownload() {
        val job = downloadJob ?: return
        val current = offer ?: return
        val snapshot = _state.value
        if (snapshot !is UpdateUiState.Downloading && snapshot !is UpdateUiState.Verifying) return
        scope.launch {
            job.cancelAndJoin()
            try {
                installer?.cancel()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // 清理残留失败不阻塞状态回退
            }
            // 下载可能在取消前一刻完成；已完成的结果不能降级
            val latest = _state.value
            if (latest is UpdateUiState.Downloading || latest is UpdateUiState.Verifying) {
                _state.value = UpdateUiState.Available(current.descriptor, current.platform)
            }
        }
    }

    /** 用户点击安装后才调用；PermissionRequired / Unavailable 不改变 Ready 状态 */
    suspend fun installNow(): UpdateInstallResult {
        val ready = _state.value as? UpdateUiState.Ready ?: return UpdateInstallResult.Unavailable
        val installer = installer ?: return UpdateInstallResult.Unavailable
        val result = try {
            installer.install()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            UpdateInstallResult.Failed
        }
        when (result) {
            UpdateInstallResult.Started -> {
                offer = null
                _state.value = UpdateUiState.Idle
            }
            UpdateInstallResult.Failed ->
                _state.value = UpdateUiState.Failed(UpdateError.INSTALL_FAILED, ready.descriptor)
            UpdateInstallResult.PermissionRequired, UpdateInstallResult.Unavailable -> Unit
        }
        return result
    }

    /** 失败重试：下载类失败重走下载，其余重新检查 */
    fun retry(): Job? =
        when ((_state.value as? UpdateUiState.Failed)?.error) {
            UpdateError.DOWNLOAD_FAILED,
            UpdateError.CHECKSUM_MISMATCH,
            UpdateError.INSTALL_FAILED -> startDownload()
            UpdateError.OFFLINE,
            UpdateError.TIMEOUT,
            UpdateError.RATE_LIMITED,
            UpdateError.SERVICE_UNAVAILABLE,
            UpdateError.INVALID_RESPONSE -> checkManually()
            null -> null
        }

    suspend fun close() {
        checkJob?.cancelAndJoin()
        downloadJob?.cancelAndJoin()
        installer?.cancel()
        api.close()
    }

    private suspend fun runCheck(silent: Boolean) {
        if (!checkLock.tryLock()) return
        try {
            val previous = _state.value
            if (previous is UpdateUiState.Downloading || previous is UpdateUiState.Verifying) return
            _state.value = UpdateUiState.Checking
            when (val result = api.latest()) {
                is UpdateApiResult.Success -> applyDescriptor(result.descriptor, silent)
                is UpdateApiResult.Failure -> {
                    if (silent) {
                        _state.value = previous
                    } else {
                        _state.value = UpdateUiState.Failed(UpdateError.from(result.reason))
                    }
                }
            }
        } finally {
            checkLock.unlock()
        }
    }

    private fun applyDescriptor(descriptor: UpdateDescriptor, silent: Boolean) {
        when (val result = checkForUpdate(currentVersion, descriptor)) {
            is UpdateCheckResult.Latest -> {
                offer = null
                _hasUnseenUpdate.value = false
                _state.value = UpdateUiState.Latest(descriptor)
            }
            is UpdateCheckResult.Available -> {
                val platform = installablePlatform(descriptor)
                // 弹窗已打开时检查结果直接可见，不再点亮红点
                _hasUnseenUpdate.value = !_dialogVisible.value
                if (platform == null) {
                    offer = null
                    _state.value = UpdateUiState.Unsupported(descriptor)
                } else {
                    offer = Offer(descriptor, platform)
                    _state.value = UpdateUiState.Available(descriptor, platform)
                }
            }
            is UpdateCheckResult.Incomparable -> {
                if (silent) {
                    _state.value = UpdateUiState.Idle
                } else {
                    _state.value = UpdateUiState.Failed(UpdateError.INVALID_RESPONSE, descriptor)
                }
            }
        }
    }

    private fun installablePlatform(descriptor: UpdateDescriptor): UpdatePlatform? {
        val installer = installer ?: return null
        val platform = descriptor.platform(installer.platformId) ?: return null
        return platform.takeIf { it.available && it.file != null && it.sha256 != null && it.downloadUrl != null }
    }
}
