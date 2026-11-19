/**
 * author : 王星星
 * date : 2026-11-16 17:17
 * email : 1099420259@qq.com
 * description : 配对页 UI 状态；可提交性由码长（空或 6 位）、设备名、提交中与冷却期共同派生，避免界面与 ViewModel 各写一套判断
 */
package com.wangxingxing.clipbox.ui.pairing

import com.wangxingxing.clipbox.repository.PAIRING_CODE_LENGTH
import com.wangxingxing.clipbox.repository.PairingError

data class PairingUiState(
    val serverUrl: String = "",
    val code: String = "",
    val deviceName: String = "",
    val isSubmitting: Boolean = false,
    val cooldownSeconds: Int = 0,
    val error: PairingError? = null,
    val insecure: Boolean = false,
) {
    // 空配对码表示空库首次初始化，因此只允许空码或完整 6 位码提交
    val canSubmit: Boolean
        get() = !isSubmitting &&
            cooldownSeconds == 0 &&
            serverUrl.isNotBlank() &&
            (code.isEmpty() || code.length == PAIRING_CODE_LENGTH) &&
            deviceName.trim().isNotEmpty()
}
