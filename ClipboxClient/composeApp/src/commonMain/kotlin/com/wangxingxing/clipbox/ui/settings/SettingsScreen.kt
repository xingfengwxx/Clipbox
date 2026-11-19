/**
 * author : 王星星
 * date : 2026-11-17 15:36
 * email : 1099420259@qq.com
 * description : 设置页 S12 与解除配对 S14：服务器、设备、桌面剪贴板、外观、诊断、关于、更新、危险区分组；
 *               改址与解绑都带二次确认，诊断开关在 release 开启前提示日志范围。注意：页面只发意图，数据改动全部经 SettingsViewModel
 */
package com.wangxingxing.clipbox.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wangxingxing.clipbox.app.AppContainer
import com.wangxingxing.clipbox.repository.PairingCodeError
import com.wangxingxing.clipbox.resources.Res
import com.wangxingxing.clipbox.resources.app_name
import com.wangxingxing.clipbox.resources.clipboard_write_failed
import com.wangxingxing.clipbox.resources.code_dialog_body
import com.wangxingxing.clipbox.resources.code_dialog_close
import com.wangxingxing.clipbox.resources.code_dialog_copied
import com.wangxingxing.clipbox.resources.code_dialog_copy
import com.wangxingxing.clipbox.resources.code_dialog_expired
import com.wangxingxing.clipbox.resources.code_dialog_generating
import com.wangxingxing.clipbox.resources.code_dialog_regenerate
import com.wangxingxing.clipbox.resources.code_dialog_remaining
import com.wangxingxing.clipbox.resources.code_error_network
import com.wangxingxing.clipbox.resources.code_error_rate_limited
import com.wangxingxing.clipbox.resources.code_error_server
import com.wangxingxing.clipbox.resources.code_error_unauthorized
import com.wangxingxing.clipbox.resources.history_cancel
import com.wangxingxing.clipbox.resources.history_pair
import com.wangxingxing.clipbox.resources.pair_cooldown
import com.wangxingxing.clipbox.resources.pair_insecure_warning
import com.wangxingxing.clipbox.resources.settings_about_build
import com.wangxingxing.clipbox.resources.settings_about_storage
import com.wangxingxing.clipbox.resources.settings_about_storage_body
import com.wangxingxing.clipbox.resources.settings_about_transport
import com.wangxingxing.clipbox.resources.settings_about_transport_body
import com.wangxingxing.clipbox.resources.settings_about_version
import com.wangxingxing.clipbox.resources.settings_auto_paste
import com.wangxingxing.clipbox.resources.settings_auto_send
import com.wangxingxing.clipbox.resources.settings_auto_send_note
import com.wangxingxing.clipbox.resources.settings_auto_update_check
import com.wangxingxing.clipbox.resources.settings_auto_update_check_note
import com.wangxingxing.clipbox.resources.settings_change_server
import com.wangxingxing.clipbox.resources.settings_check_update
import com.wangxingxing.clipbox.resources.settings_check_update_action
import com.wangxingxing.clipbox.resources.settings_check_update_busy
import com.wangxingxing.clipbox.resources.settings_confirm_enable
import com.wangxingxing.clipbox.resources.settings_danger_change_body
import com.wangxingxing.clipbox.resources.settings_danger_change_title
import com.wangxingxing.clipbox.resources.settings_danger_confirm
import com.wangxingxing.clipbox.resources.settings_danger_unpair_body
import com.wangxingxing.clipbox.resources.settings_danger_unpair_title
import com.wangxingxing.clipbox.resources.settings_device_id
import com.wangxingxing.clipbox.resources.settings_device_name
import com.wangxingxing.clipbox.resources.settings_diag_confirm_body
import com.wangxingxing.clipbox.resources.settings_diag_confirm_title
import com.wangxingxing.clipbox.resources.settings_diagnostic
import com.wangxingxing.clipbox.resources.settings_diagnostic_note
import com.wangxingxing.clipbox.resources.settings_kicker
import com.wangxingxing.clipbox.resources.settings_language
import com.wangxingxing.clipbox.resources.settings_language_en
import com.wangxingxing.clipbox.resources.settings_language_hans
import com.wangxingxing.clipbox.resources.settings_language_tw
import com.wangxingxing.clipbox.resources.settings_last_sync
import com.wangxingxing.clipbox.resources.settings_last_sync_never
import com.wangxingxing.clipbox.resources.settings_paired
import com.wangxingxing.clipbox.resources.settings_pair_new_device
import com.wangxingxing.clipbox.resources.settings_pairing_status
import com.wangxingxing.clipbox.resources.settings_privacy_body
import com.wangxingxing.clipbox.resources.settings_privacy_title
import com.wangxingxing.clipbox.resources.settings_save
import com.wangxingxing.clipbox.resources.settings_section_about
import com.wangxingxing.clipbox.resources.settings_section_appearance
import com.wangxingxing.clipbox.resources.settings_section_clipboard
import com.wangxingxing.clipbox.resources.settings_section_danger
import com.wangxingxing.clipbox.resources.settings_section_device
import com.wangxingxing.clipbox.resources.settings_section_diagnostic
import com.wangxingxing.clipbox.resources.settings_section_server
import com.wangxingxing.clipbox.resources.settings_section_update
import com.wangxingxing.clipbox.resources.settings_server_invalid
import com.wangxingxing.clipbox.resources.settings_server_label
import com.wangxingxing.clipbox.resources.settings_server_saved
import com.wangxingxing.clipbox.resources.settings_sync_busy
import com.wangxingxing.clipbox.resources.settings_sync_failed
import com.wangxingxing.clipbox.resources.settings_sync_now
import com.wangxingxing.clipbox.resources.settings_sync_offline
import com.wangxingxing.clipbox.resources.settings_sync_success
import com.wangxingxing.clipbox.resources.settings_sync_unauthorized
import com.wangxingxing.clipbox.resources.settings_sync_unpaired
import com.wangxingxing.clipbox.resources.settings_theme
import com.wangxingxing.clipbox.resources.settings_theme_dark
import com.wangxingxing.clipbox.resources.settings_theme_light
import com.wangxingxing.clipbox.resources.settings_theme_system
import com.wangxingxing.clipbox.resources.settings_title
import com.wangxingxing.clipbox.resources.settings_unpair
import com.wangxingxing.clipbox.resources.settings_unpaired
import com.wangxingxing.clipbox.resources.settings_update_current
import com.wangxingxing.clipbox.resources.settings_update_date
import com.wangxingxing.clipbox.resources.settings_update_latest
import com.wangxingxing.clipbox.settings.AppLanguage
import com.wangxingxing.clipbox.settings.ServerUrlValidation
import com.wangxingxing.clipbox.settings.ServerUrlValidator
import com.wangxingxing.clipbox.settings.ThemeMode
import com.wangxingxing.clipbox.settings.isInsecureServerUrl
import com.wangxingxing.clipbox.ui.components.ConfirmDialog
import com.wangxingxing.clipbox.ui.components.SyncStatusBar
import com.wangxingxing.clipbox.ui.i18n.LocalAppLanguage
import com.wangxingxing.clipbox.ui.theme.ClipboxTokens
import com.wangxingxing.clipbox.update.UpdateDescriptor
import com.wangxingxing.clipbox.update.UpdateUiState
import com.wangxingxing.clipbox.update.descriptorOrNull
import com.wangxingxing.clipbox.update.hasUpdate
import com.wangxingxing.clipbox.util.clipNowMillis
import com.wangxingxing.clipbox.util.formatAbsoluteTime
import com.wangxingxing.clipbox.util.formatPublishedDate
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private sealed interface SettingsDialog {
    data object None : SettingsDialog
    data object ChangeServer : SettingsDialog
    data class ConfirmChangeServer(val url: String) : SettingsDialog
    data object ConfirmUnpair : SettingsDialog
    data object ConfirmAutoSend : SettingsDialog
    data object ConfirmDiagnostic : SettingsDialog
    data object PairingCode : SettingsDialog
}

@Composable
fun SettingsRoute(
    onOpenAppearance: () -> Unit,
    onPair: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SettingsViewModel = koinViewModel()
    val container = koinInject<AppContainer>()
    val state by viewModel.state.collectAsState()
    val updateState by container.updateController.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf<SettingsDialog>(SettingsDialog.None) }

    val serverSavedMessage = stringResource(Res.string.settings_server_saved)
    val syncSuccessMessage = stringResource(Res.string.settings_sync_success)
    val syncFailedMessage = stringResource(Res.string.settings_sync_failed)
    val syncOfflineMessage = stringResource(Res.string.settings_sync_offline)
    val syncUnpairedMessage = stringResource(Res.string.settings_sync_unpaired)
    val syncUnauthorizedMessage = stringResource(Res.string.settings_sync_unauthorized)
    val codeCopiedMessage = stringResource(Res.string.code_dialog_copied)
    val codeCopyFailedMessage = stringResource(Res.string.clipboard_write_failed)

    LaunchedEffect(
        viewModel,
        serverSavedMessage,
        syncSuccessMessage,
        syncFailedMessage,
        syncOfflineMessage,
        syncUnpairedMessage,
        syncUnauthorizedMessage,
        codeCopiedMessage,
        codeCopyFailedMessage,
    ) {
        viewModel.events.collect { event ->
            val message = when (event) {
                SettingsEvent.ServerUrlSaved -> serverSavedMessage
                SettingsEvent.SyncSucceeded -> syncSuccessMessage
                SettingsEvent.SyncFailed -> syncFailedMessage
                SettingsEvent.SyncOffline -> syncOfflineMessage
                SettingsEvent.SyncUnpaired -> syncUnpairedMessage
                SettingsEvent.SyncUnauthorized -> syncUnauthorizedMessage
                SettingsEvent.PairingCodeCopied -> codeCopiedMessage
                SettingsEvent.PairingCodeCopyFailed -> codeCopyFailedMessage
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    Box(modifier.fillMaxSize()) {
        SettingsScreen(
            state = state,
            onOpenAppearance = onOpenAppearance,
            onPair = onPair,
            onSyncNow = viewModel::syncNow,
            onAutoPasteChange = viewModel::setAutoPaste,
            onAutoSendChange = { enabled ->
                if (enabled) dialog = SettingsDialog.ConfirmAutoSend else viewModel.setAutoClipboardSend(false)
            },
            onDiagnosticChange = { enabled ->
                if (enabled && !state.isDebugBuild) {
                    dialog = SettingsDialog.ConfirmDiagnostic
                } else {
                    viewModel.setDiagnosticLogging(enabled)
                }
            },
            onChangeServer = { dialog = SettingsDialog.ChangeServer },
            onUnpair = { dialog = SettingsDialog.ConfirmUnpair },
            onPairNewDevice = {
                dialog = SettingsDialog.PairingCode
                viewModel.generatePairingCode()
            },
            updateDescriptor = updateState.descriptorOrNull(),
            updateAvailable = updateState.hasUpdate(),
            updateChecking = updateState is UpdateUiState.Checking,
            onAutoUpdateCheckChange = viewModel::setAutoUpdateCheck,
            onCheckUpdate = {
                container.updateController.openDialog()
                // 已有检查结果时只打开弹层，避免 Ready 文件被一次重新检查作废
                if (updateState is UpdateUiState.Idle || updateState is UpdateUiState.Latest) {
                    container.updateController.checkManually()
                }
            },
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(ClipboxTokens.spacing.l),
        )
    }

    when (val current = dialog) {
        SettingsDialog.None -> Unit

        SettingsDialog.ChangeServer -> ServerAddressDialog(
            initial = state.serverUrl,
            onSave = { url ->
                // 已配对且地址确实变化时才进入二次确认；未配对直接保存并回到设置页
                if (state.paired && url != state.serverUrl) {
                    dialog = SettingsDialog.ConfirmChangeServer(url)
                } else {
                    dialog = SettingsDialog.None
                    viewModel.saveServerUrl(url)
                }
            },
            onDismiss = { dialog = SettingsDialog.None },
        )

        is SettingsDialog.ConfirmChangeServer -> ConfirmDialog(
            title = stringResource(Res.string.settings_danger_change_title),
            body = stringResource(Res.string.settings_danger_change_body),
            confirmText = stringResource(Res.string.settings_danger_confirm),
            onConfirm = {
                dialog = SettingsDialog.None
                viewModel.saveServerUrl(current.url)
            },
            onDismiss = { dialog = SettingsDialog.None },
        )

        SettingsDialog.ConfirmUnpair -> ConfirmDialog(
            title = stringResource(Res.string.settings_danger_unpair_title),
            body = stringResource(Res.string.settings_danger_unpair_body),
            confirmText = stringResource(Res.string.settings_danger_confirm),
            onConfirm = {
                dialog = SettingsDialog.None
                viewModel.unpair()
            },
            onDismiss = { dialog = SettingsDialog.None },
        )

        SettingsDialog.ConfirmAutoSend -> ConfirmDialog(
            title = stringResource(Res.string.settings_privacy_title),
            body = stringResource(Res.string.settings_privacy_body),
            confirmText = stringResource(Res.string.settings_confirm_enable),
            onConfirm = {
                dialog = SettingsDialog.None
                viewModel.setAutoClipboardSend(true)
            },
            onDismiss = { dialog = SettingsDialog.None },
        )

        SettingsDialog.ConfirmDiagnostic -> ConfirmDialog(
            title = stringResource(Res.string.settings_diag_confirm_title),
            body = stringResource(Res.string.settings_diag_confirm_body),
            confirmText = stringResource(Res.string.settings_confirm_enable),
            onConfirm = {
                dialog = SettingsDialog.None
                viewModel.setDiagnosticLogging(true)
            },
            onDismiss = { dialog = SettingsDialog.None },
        )

        SettingsDialog.PairingCode -> PairingCodeDialog(
            state = state,
            onGenerate = viewModel::generatePairingCode,
            onCopy = viewModel::copyPairingCode,
            onDismiss = {
                dialog = SettingsDialog.None
                viewModel.clearPairingCode()
            },
            onClear = viewModel::clearPairingCode,
        )
    }
}

@Composable
private fun SettingsScreen(
    state: SettingsUiState,
    onOpenAppearance: () -> Unit,
    onPair: () -> Unit,
    onSyncNow: () -> Unit,
    onAutoPasteChange: (Boolean) -> Unit,
    onAutoSendChange: (Boolean) -> Unit,
    onDiagnosticChange: (Boolean) -> Unit,
    onChangeServer: () -> Unit,
    onUnpair: () -> Unit,
    onPairNewDevice: () -> Unit,
    updateDescriptor: UpdateDescriptor?,
    updateAvailable: Boolean,
    updateChecking: Boolean,
    onAutoUpdateCheckChange: (Boolean) -> Unit,
    onCheckUpdate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ClipboxTokens.colors
    val spacing = ClipboxTokens.spacing
    val themeLabel = stringResource(
        when (state.themeMode) {
            ThemeMode.SYSTEM -> Res.string.settings_theme_system
            ThemeMode.LIGHT -> Res.string.settings_theme_light
            ThemeMode.DARK -> Res.string.settings_theme_dark
        },
    )
    val languageLabel = when (state.language) {
        AppLanguage.ZH_HANS -> stringResource(Res.string.settings_language_hans)
        AppLanguage.ZH_TW -> stringResource(Res.string.settings_language_tw)
        AppLanguage.EN -> stringResource(Res.string.settings_language_en)
    }
    val lastSyncText = state.lastSyncAt?.let { formatAbsoluteTime(it, LocalAppLanguage.current) }
        ?: stringResource(Res.string.settings_last_sync_never)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        SettingsTopBar()
        HorizontalDivider(thickness = 1.dp, color = colors.ink)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = spacing.xl),
        ) {
            item { SectionLabel(stringResource(Res.string.settings_section_server)) }
            item {
                SettingRow(
                    label = stringResource(Res.string.settings_server_label),
                    value = state.serverUrl,
                    mono = true,
                )
            }
            if (isInsecureServerUrl(state.serverUrl)) {
                item { HintRow(stringResource(Res.string.pair_insecure_warning), warn = true) }
            }
            item {
                SyncStatusBar(
                    status = state.syncStatus,
                    paired = state.paired,
                    onRetry = onSyncNow,
                )
            }
            item {
                SettingRow(
                    label = stringResource(Res.string.settings_last_sync),
                    value = lastSyncText,
                    mono = true,
                )
            }
            item {
                ActionRow(
                    label = stringResource(Res.string.settings_sync_now),
                    enabled = state.paired && !state.isSyncing,
                    loading = state.isSyncing,
                    onClick = onSyncNow,
                )
            }

            item { SectionLabel(stringResource(Res.string.settings_section_device)) }
            item {
                SettingRow(
                    label = stringResource(Res.string.settings_device_name),
                    value = state.deviceName.ifEmpty { "—" },
                )
            }
            item {
                SettingRow(
                    label = stringResource(Res.string.settings_device_id),
                    value = state.deviceId.take(DEVICE_ID_DISPLAY_LENGTH).ifEmpty { "—" },
                    mono = true,
                )
            }
            item {
                SettingRow(
                    label = stringResource(Res.string.settings_pairing_status),
                    value = stringResource(
                        if (state.paired) Res.string.settings_paired else Res.string.settings_unpaired,
                    ),
                    valueColor = if (state.paired) colors.ok else colors.ink3,
                )
            }
            // 未配对设备也能生成配对码，入口不按配对状态隐藏。
            item {
                ChevronRow(
                    label = stringResource(Res.string.settings_pair_new_device),
                    value = null,
                    onClick = onPairNewDevice,
                )
            }

            if (state.isDesktop) {
                item { SectionLabel(stringResource(Res.string.settings_section_clipboard)) }
                item {
                    SwitchRow(
                        label = stringResource(Res.string.settings_auto_paste),
                        checked = state.autoPaste,
                        onCheckedChange = onAutoPasteChange,
                    )
                }
                item {
                    SwitchRow(
                        label = stringResource(Res.string.settings_auto_send),
                        checked = state.autoClipboardSend,
                        onCheckedChange = onAutoSendChange,
                    )
                }
                item { HintRow(stringResource(Res.string.settings_auto_send_note)) }
            }

            item { SectionLabel(stringResource(Res.string.settings_section_appearance)) }
            item {
                ChevronRow(
                    label = stringResource(Res.string.settings_theme),
                    value = themeLabel,
                    onClick = onOpenAppearance,
                )
            }
            item {
                ChevronRow(
                    label = stringResource(Res.string.settings_language),
                    value = languageLabel,
                    onClick = onOpenAppearance,
                )
            }

            item { SectionLabel(stringResource(Res.string.settings_section_diagnostic)) }
            item {
                SwitchRow(
                    label = stringResource(Res.string.settings_diagnostic),
                    checked = state.diagnosticLoggingEnabled,
                    onCheckedChange = onDiagnosticChange,
                )
            }
            item { HintRow(stringResource(Res.string.settings_diagnostic_note)) }

            item { SectionLabel(stringResource(Res.string.settings_section_about)) }
            item {
                SettingRow(
                    label = stringResource(Res.string.settings_about_version),
                    value = state.versionName,
                    mono = true,
                )
            }
            item {
                SettingRow(
                    label = stringResource(Res.string.settings_about_build),
                    value = state.buildType,
                    mono = true,
                )
            }
            item {
                SettingRow(
                    label = stringResource(Res.string.settings_about_storage),
                    value = null,
                )
            }
            item { HintRow(stringResource(Res.string.settings_about_storage_body)) }
            item {
                SettingRow(
                    label = stringResource(Res.string.settings_about_transport),
                    value = null,
                )
            }
            item { HintRow(stringResource(Res.string.settings_about_transport_body), warn = true) }

            item { SectionLabel(stringResource(Res.string.settings_section_update)) }
            item {
                SettingRow(
                    label = stringResource(Res.string.settings_update_current),
                    value = state.versionName,
                    mono = true,
                )
            }
            item {
                SettingRow(
                    label = stringResource(Res.string.settings_update_latest),
                    value = updateDescriptor?.version ?: "—",
                    mono = true,
                    valueColor = if (updateAvailable) colors.accent else colors.ink2,
                )
            }
            item {
                SettingRow(
                    label = stringResource(Res.string.settings_update_date),
                    value = updateDescriptor?.publishedAt
                        ?.let { formatPublishedDate(it, LocalAppLanguage.current) }
                        ?: "—",
                    mono = true,
                )
            }
            item {
                SwitchRow(
                    label = stringResource(Res.string.settings_auto_update_check),
                    checked = state.autoUpdateCheck,
                    onCheckedChange = onAutoUpdateCheckChange,
                )
            }
            item { HintRow(stringResource(Res.string.settings_auto_update_check_note)) }
            item {
                ActionRow(
                    label = stringResource(Res.string.settings_check_update),
                    enabled = !updateChecking,
                    loading = updateChecking,
                    onClick = onCheckUpdate,
                    actionLabel = Res.string.settings_check_update_action,
                    busyLabel = Res.string.settings_check_update_busy,
                )
            }

            item { SectionLabel(stringResource(Res.string.settings_section_danger)) }
            item {
                ChevronRow(
                    label = stringResource(Res.string.settings_change_server),
                    value = null,
                    danger = true,
                    onClick = onChangeServer,
                )
            }
            if (state.paired) {
                item {
                    ChevronRow(
                        label = stringResource(Res.string.settings_unpair),
                        value = null,
                        danger = true,
                        onClick = onUnpair,
                    )
                }
            } else {
                item {
                    ChevronRow(
                        label = stringResource(Res.string.history_pair),
                        value = null,
                        onClick = onPair,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsTopBar() {
    val colors = ClipboxTokens.colors
    val spacing = ClipboxTokens.spacing
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = spacing.l, top = spacing.m, end = spacing.l, bottom = spacing.s),
    ) {
        Text(
            text = stringResource(Res.string.settings_title),
            style = MaterialTheme.typography.titleLarge,
            color = colors.ink,
        )
        Spacer(Modifier.height(spacing.xs))
        Text(
            text = stringResource(Res.string.settings_kicker),
            style = ClipboxTokens.typography.mono,
            color = colors.ink3,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(
            start = ClipboxTokens.spacing.l,
            top = ClipboxTokens.spacing.xl,
            end = ClipboxTokens.spacing.l,
            bottom = ClipboxTokens.spacing.s,
        ),
        style = ClipboxTokens.typography.mono,
        color = ClipboxTokens.colors.ink3,
    )
}

@Composable
private fun SettingRow(
    label: String,
    value: String?,
    mono: Boolean = false,
    valueColor: androidx.compose.ui.graphics.Color = ClipboxTokens.colors.ink2,
) {
    val colors = ClipboxTokens.colors
    val spacing = ClipboxTokens.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = spacing.l, vertical = spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.ink,
        )
        Spacer(Modifier.weight(1f))
        if (value != null) {
            Text(
                text = value,
                style = if (mono) ClipboxTokens.typography.mono else MaterialTheme.typography.bodySmall,
                color = valueColor,
            )
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = ClipboxTokens.colors
    val spacing = ClipboxTokens.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = spacing.l, vertical = spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.ink,
        )
        Spacer(Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onAccent,
                checkedTrackColor = colors.accent,
                uncheckedThumbColor = colors.ink2,
                uncheckedTrackColor = colors.panel2,
                uncheckedBorderColor = colors.line,
            ),
        )
    }
}

@Composable
private fun ActionRow(
    label: String,
    enabled: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
    actionLabel: StringResource = Res.string.settings_sync_now,
    busyLabel: StringResource = Res.string.settings_sync_busy,
) {
    val colors = ClipboxTokens.colors
    val spacing = ClipboxTokens.spacing
    val shape = RoundedCornerShape(ClipboxTokens.radii.button)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.l, vertical = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.ink,
        )
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .background(if (enabled || loading) colors.panel2 else colors.panel, shape)
                .border(1.dp, colors.line, shape)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = spacing.m),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(if (loading) busyLabel else actionLabel),
                style = MaterialTheme.typography.labelMedium,
                color = if (enabled) colors.ink else colors.ink3,
            )
        }
    }
}

@Composable
private fun ChevronRow(
    label: String,
    value: String?,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = ClipboxTokens.colors
    val spacing = ClipboxTokens.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.l, vertical = spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (danger) colors.danger else colors.ink,
        )
        Spacer(Modifier.weight(1f))
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = colors.ink2,
            )
            Spacer(Modifier.width(spacing.s))
        }
        Text(
            text = "›",
            style = MaterialTheme.typography.bodyMedium,
            color = if (danger) colors.danger else colors.ink3,
        )
    }
}

@Composable
private fun HintRow(text: String, warn: Boolean = false) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = ClipboxTokens.spacing.l,
                top = ClipboxTokens.spacing.xs,
                end = ClipboxTokens.spacing.l,
                bottom = ClipboxTokens.spacing.s,
            ),
        style = MaterialTheme.typography.bodySmall,
        color = if (warn) ClipboxTokens.colors.warn else ClipboxTokens.colors.ink3,
    )
}

@Composable
private fun ServerAddressDialog(
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = ClipboxTokens.colors
    val spacing = ClipboxTokens.spacing
    var draft by rememberSaveable(initial) { mutableStateOf(initial) }
    var invalid by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(ClipboxTokens.radii.button)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.background,
        titleContentColor = colors.ink,
        textContentColor = colors.ink2,
        title = { Text(stringResource(Res.string.settings_change_server)) },
        text = {
            Column {
                Text(
                    text = stringResource(Res.string.settings_server_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.ink2,
                )
                Spacer(Modifier.padding(top = spacing.xs))
                BasicTextField(
                    value = draft,
                    onValueChange = {
                        draft = it
                        invalid = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.panel, shape)
                        .border(1.dp, if (invalid) colors.danger else colors.line, shape)
                        .padding(horizontal = spacing.m, vertical = spacing.m),
                    singleLine = true,
                    textStyle = ClipboxTokens.typography.mono.copy(color = colors.ink),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                )
                if (invalid) {
                    Text(
                        text = stringResource(Res.string.settings_server_invalid),
                        modifier = Modifier.padding(top = spacing.xs),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.danger,
                    )
                } else if (isInsecureServerUrl(draft)) {
                    Text(
                        text = stringResource(Res.string.pair_insecure_warning),
                        modifier = Modifier.padding(top = spacing.xs),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.warn,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when (val validation = ServerUrlValidator.validate(draft)) {
                        is ServerUrlValidation.Invalid -> invalid = true
                        is ServerUrlValidation.Valid -> onSave(validation.url)
                    }
                },
            ) {
                Text(text = stringResource(Res.string.settings_save), color = colors.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(Res.string.history_cancel), color = colors.ink2)
            }
        },
    )
}

@Composable
private fun PairingCodeDialog(
    state: SettingsUiState,
    onGenerate: () -> Unit,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
) {
    val colors = ClipboxTokens.colors
    val spacing = ClipboxTokens.spacing
    val expiresAt = state.pairingCodeExpiresAt
    var remainingSeconds by remember(expiresAt) { mutableStateOf(secondsUntil(expiresAt)) }

    // 弹层被销毁（关闭或页面切换）时立刻清空内存中的明文码
    DisposableEffect(Unit) { onDispose(onClear) }

    LaunchedEffect(expiresAt) {
        while (true) {
            remainingSeconds = secondsUntil(expiresAt)
            if (remainingSeconds <= 0) break
            delay(1_000)
        }
    }

    val regenerateLabel = when {
        state.isGeneratingCode -> stringResource(Res.string.code_dialog_generating)
        state.codeCooldownSeconds > 0 -> pluralStringResource(
            Res.plurals.pair_cooldown,
            state.codeCooldownSeconds,
            state.codeCooldownSeconds,
        )

        else -> stringResource(Res.string.code_dialog_regenerate)
    }
    val code = state.pairingCode

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.background,
        titleContentColor = colors.ink,
        textContentColor = colors.ink2,
        title = { Text(stringResource(Res.string.settings_pair_new_device)) },
        text = {
            Column {
                Text(
                    text = stringResource(Res.string.code_dialog_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.ink2,
                )
                Spacer(Modifier.height(spacing.l))
                if (code == null) {
                    Text(
                        text = if (state.isGeneratingCode) {
                            stringResource(Res.string.code_dialog_generating)
                        } else {
                            "—"
                        },
                        modifier = Modifier.fillMaxWidth(),
                        style = ClipboxTokens.typography.mono,
                        color = colors.ink2,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    Text(
                        text = code,
                        modifier = Modifier.fillMaxWidth(),
                        style = ClipboxTokens.typography.mono.copy(
                            fontSize = 32.sp,
                            lineHeight = 38.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 8.sp,
                            textAlign = TextAlign.Center,
                        ),
                        color = if (remainingSeconds > 0) colors.ink else colors.ink3,
                    )
                    Spacer(Modifier.height(spacing.xs))
                    Text(
                        text = if (remainingSeconds > 0) {
                            stringResource(
                                Res.string.code_dialog_remaining,
                                formatCountdown(remainingSeconds),
                            )
                        } else {
                            stringResource(Res.string.code_dialog_expired)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        style = ClipboxTokens.typography.mono,
                        color = if (remainingSeconds > 0) colors.ink2 else colors.warn,
                        textAlign = TextAlign.Center,
                    )
                    if (remainingSeconds > 0) {
                        TextButton(
                            onClick = onCopy,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) {
                            Text(
                                text = stringResource(Res.string.code_dialog_copy),
                                color = colors.accent,
                            )
                        }
                    }
                }
                state.codeError?.let { error ->
                    Spacer(Modifier.height(spacing.s))
                    Text(
                        text = stringResource(error.messageResource()),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.danger,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onGenerate, enabled = state.canGenerateCode) {
                Text(
                    text = regenerateLabel,
                    color = if (state.canGenerateCode) colors.accent else colors.ink3,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(Res.string.code_dialog_close), color = colors.ink2)
            }
        },
    )
}

// 错误分类到文案的唯一映射点，界面不拼接服务端 message
private fun PairingCodeError.messageResource(): StringResource = when (this) {
    PairingCodeError.Unauthorized -> Res.string.code_error_unauthorized
    PairingCodeError.Network -> Res.string.code_error_network
    PairingCodeError.Server -> Res.string.code_error_server
    is PairingCodeError.RateLimited -> Res.string.code_error_rate_limited
}

private fun secondsUntil(expiresAt: Long?): Int {
    if (expiresAt == null) return 0
    return ((expiresAt - clipNowMillis()).coerceAtLeast(0L) / 1_000).toInt()
}

private fun formatCountdown(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

private const val DEVICE_ID_DISPLAY_LENGTH = 8
