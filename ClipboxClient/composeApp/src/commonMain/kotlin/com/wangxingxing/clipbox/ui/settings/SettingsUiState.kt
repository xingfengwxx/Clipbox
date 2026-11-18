/**
 * author : 王星星
 * date : 2026-11-17 15:32
 * email : 1099420259@qq.com
 * description : 设置页 S12 的只读快照；全部值来自设置持久层与同步内核，页面不自行推导同步状态。
 *               配对码只存在于本状态的内存中，关闭弹层即清空
 */
package com.wangxingxing.clipbox.ui.settings

import com.wangxingxing.clipbox.model.SyncStatus
import com.wangxingxing.clipbox.repository.PairingCodeError
import com.wangxingxing.clipbox.settings.AppLanguage
import com.wangxingxing.clipbox.settings.ThemeMode

data class SettingsUiState(
    val loaded: Boolean = false,
    val paired: Boolean = false,
    val serverUrl: String = "",
    val deviceName: String = "",
    val deviceId: String = "",
    val autoPaste: Boolean = true,
    val autoClipboardSend: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.ZH_HANS,
    val diagnosticLoggingEnabled: Boolean = false,
    val isDesktop: Boolean = false,
    val isDebugBuild: Boolean = false,
    val versionName: String = "",
    val buildType: String = "",
    val syncStatus: SyncStatus = SyncStatus.Idle,
    val lastSyncAt: Long? = null,
    val isSyncing: Boolean = false,
    val pairingCode: String? = null,
    val pairingCodeExpiresAt: Long? = null,
    val isGeneratingCode: Boolean = false,
    val codeError: PairingCodeError? = null,
    val codeCooldownSeconds: Int = 0,
) {
    val canGenerateCode: Boolean
        get() = !isGeneratingCode && codeCooldownSeconds == 0
}

sealed interface SettingsEvent {
    data object ServerUrlSaved : SettingsEvent
    data object SyncSucceeded : SettingsEvent
    data object SyncFailed : SettingsEvent
    data object SyncOffline : SettingsEvent
    data object SyncUnpaired : SettingsEvent
    data object SyncUnauthorized : SettingsEvent
    data object PairingCodeCopied : SettingsEvent
    data object PairingCodeCopyFailed : SettingsEvent
}
