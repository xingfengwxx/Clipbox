/**
 * author : 王星星
 * date : 2026-11-19 11:20
 * email : 1099420259@qq.com
 * description : 更新弹窗的单一 UI 状态；由 UpdateController 以 StateFlow 推送，UI 只渲染不自行推断。
 *               注意：状态不提供忽略或强制分支；关闭弹窗不清除检查结果，红点单独由 hasUnseenUpdate 表示
 */
package com.wangxingxing.clipbox.update

sealed interface UpdateUiState {
    data object Idle : UpdateUiState

    data object Checking : UpdateUiState

    data class Latest(val descriptor: UpdateDescriptor) : UpdateUiState

    data class Available(
        val descriptor: UpdateDescriptor,
        val platform: UpdatePlatform,
    ) : UpdateUiState

    /** 有新版本但当前平台没有可安装产物（iOS 或 available=false），只展示说明 */
    data class Unsupported(val descriptor: UpdateDescriptor) : UpdateUiState

    data class Downloading(
        val descriptor: UpdateDescriptor,
        val platform: UpdatePlatform,
        val downloadedBytes: Long,
        val totalBytes: Long,
    ) : UpdateUiState

    data class Verifying(
        val descriptor: UpdateDescriptor,
        val platform: UpdatePlatform,
    ) : UpdateUiState

    data class Ready(
        val descriptor: UpdateDescriptor,
        val platform: UpdatePlatform,
    ) : UpdateUiState

    data class Failed(
        val error: UpdateError,
        val descriptor: UpdateDescriptor? = null,
    ) : UpdateUiState
}

enum class UpdateError {
    OFFLINE,
    TIMEOUT,
    RATE_LIMITED,
    SERVICE_UNAVAILABLE,
    INVALID_RESPONSE,
    DOWNLOAD_FAILED,
    CHECKSUM_MISMATCH,
    INSTALL_FAILED,
    ;

    companion object {
        fun from(failure: UpdateFailureReason): UpdateError = when (failure) {
            UpdateFailureReason.OFFLINE -> OFFLINE
            UpdateFailureReason.TIMEOUT -> TIMEOUT
            UpdateFailureReason.RATE_LIMITED -> RATE_LIMITED
            UpdateFailureReason.SERVICE_UNAVAILABLE -> SERVICE_UNAVAILABLE
            UpdateFailureReason.INVALID_RESPONSE -> INVALID_RESPONSE
        }
    }
}
