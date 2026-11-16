/**
 * author : 王星星
 * date : 2026-11-16 17:17
 * email : 1099420259@qq.com
 * description : 配对页 UI 状态；可提交性由码长、设备名、提交中与冷却期共同派生，避免界面与 ViewModel 各写一套判断
 */
package com.wangxingxing.clipbox.ui.pairing

import com.wangxingxing.clipbox.repository.PAIRING_CODE_LENGTH
import com.wangxingxing.clipbox.repository.PairingError

data class PairingUiState(
    val code: String = "",
    val deviceName: String = "",
    val isSubmitting: Boolean = false,
    val cooldownSeconds: Int = 0,
    val error: PairingError? = null,
    val insecure: Boolean = false,
) {
    val canSubmit: Boolean
        get() = !isSubmitting &&
            cooldownSeconds == 0 &&
            code.length == PAIRING_CODE_LENGTH &&
            deviceName.trim().isNotEmpty()
}
