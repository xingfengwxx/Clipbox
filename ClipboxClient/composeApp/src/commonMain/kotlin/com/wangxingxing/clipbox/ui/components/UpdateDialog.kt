/**
 * author : 王星星
 * date : 2026-11-19 13:08
 * email : 1099420259@qq.com
 * description : 根级更新弹窗；按 UpdateUiState 覆盖检查、最新、可下载、风险确认、下载、校验、可安装、失败与平台不支持九种展示。
 *               注意：弹层不新增路由，关闭只收起界面；下载与 Ready 状态保持，iOS 永远不出现下载或安装按钮
 */
package com.wangxingxing.clipbox.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wangxingxing.clipbox.resources.Res
import com.wangxingxing.clipbox.resources.history_cancel
import com.wangxingxing.clipbox.resources.update_dialog_available_title
import com.wangxingxing.clipbox.resources.update_dialog_cancel_download
import com.wangxingxing.clipbox.resources.update_dialog_checking_body
import com.wangxingxing.clipbox.resources.update_dialog_checking_title
import com.wangxingxing.clipbox.resources.update_dialog_close
import com.wangxingxing.clipbox.resources.update_dialog_download
import com.wangxingxing.clipbox.resources.update_dialog_download_progress
import com.wangxingxing.clipbox.resources.update_dialog_downloading_title
import com.wangxingxing.clipbox.resources.update_dialog_failed_title
import com.wangxingxing.clipbox.resources.update_dialog_install
import com.wangxingxing.clipbox.resources.update_dialog_kicker
import com.wangxingxing.clipbox.resources.update_dialog_know
import com.wangxingxing.clipbox.resources.update_dialog_later
import com.wangxingxing.clipbox.resources.update_dialog_latest_body
import com.wangxingxing.clipbox.resources.update_dialog_latest_title
import com.wangxingxing.clipbox.resources.update_dialog_meta_line
import com.wangxingxing.clipbox.resources.update_dialog_notes
import com.wangxingxing.clipbox.resources.update_dialog_ready_body
import com.wangxingxing.clipbox.resources.update_dialog_ready_note
import com.wangxingxing.clipbox.resources.update_dialog_ready_title
import com.wangxingxing.clipbox.resources.update_dialog_retry
import com.wangxingxing.clipbox.resources.update_dialog_sha_match
import com.wangxingxing.clipbox.resources.update_dialog_unsupported_ios
import com.wangxingxing.clipbox.resources.update_dialog_unsupported_other
import com.wangxingxing.clipbox.resources.update_dialog_verifying_body
import com.wangxingxing.clipbox.resources.update_dialog_verifying_title
import com.wangxingxing.clipbox.resources.update_dialog_version_line
import com.wangxingxing.clipbox.resources.update_error_checksum_mismatch
import com.wangxingxing.clipbox.resources.update_error_download_failed
import com.wangxingxing.clipbox.resources.update_error_install_failed
import com.wangxingxing.clipbox.resources.update_error_invalid_response
import com.wangxingxing.clipbox.resources.update_error_offline
import com.wangxingxing.clipbox.resources.update_error_rate_limited
import com.wangxingxing.clipbox.resources.update_error_service_unavailable
import com.wangxingxing.clipbox.resources.update_error_timeout
import com.wangxingxing.clipbox.resources.update_install_permission_required
import com.wangxingxing.clipbox.resources.update_install_unavailable
import com.wangxingxing.clipbox.resources.update_platform_android
import com.wangxingxing.clipbox.resources.update_platform_ios
import com.wangxingxing.clipbox.resources.update_platform_linux
import com.wangxingxing.clipbox.resources.update_platform_macos
import com.wangxingxing.clipbox.resources.update_platform_windows
import com.wangxingxing.clipbox.resources.update_risk_continue
import com.wangxingxing.clipbox.resources.update_risk_footnote
import com.wangxingxing.clipbox.resources.update_risk_http_body
import com.wangxingxing.clipbox.resources.update_risk_http_title
import com.wangxingxing.clipbox.resources.update_risk_tag_http
import com.wangxingxing.clipbox.resources.update_risk_tag_unsigned
import com.wangxingxing.clipbox.resources.update_risk_title
import com.wangxingxing.clipbox.resources.update_risk_unsigned_body
import com.wangxingxing.clipbox.resources.update_risk_unsigned_title
import com.wangxingxing.clipbox.ui.i18n.LocalAppLanguage
import com.wangxingxing.clipbox.ui.theme.ClipboxTokens
import com.wangxingxing.clipbox.update.UpdateController
import com.wangxingxing.clipbox.update.UpdateDescriptor
import com.wangxingxing.clipbox.update.UpdateError
import com.wangxingxing.clipbox.update.UpdateInstallResult
import com.wangxingxing.clipbox.update.UpdatePlatform
import com.wangxingxing.clipbox.update.UpdatePlatformId
import com.wangxingxing.clipbox.update.UpdateUiState
import com.wangxingxing.clipbox.update.descriptorOrNull
import com.wangxingxing.clipbox.util.TextSize
import com.wangxingxing.clipbox.util.formatPublishedDate
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

private const val PROGRESS_SEGMENTS = 32

@Composable
fun UpdateDialog(
    controller: UpdateController,
    currentVersion: String,
    isIos: Boolean,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsState()
    val language = LocalAppLanguage.current
    val versionKey = state.descriptorOrNull()?.version.orEmpty()
    var riskConfirm by rememberSaveable(versionKey) { mutableStateOf(false) }
    var installNotice by remember(versionKey) { mutableStateOf<UpdateInstallResult?>(null) }
    val scope = rememberCoroutineScope()

    // 离开当前页面状态后，风险页与安装提示必须复位，避免下一次流程沿用旧提示
    LaunchedEffect(state) {
        if (state !is UpdateUiState.Available) riskConfirm = false
        if (state !is UpdateUiState.Ready) installNotice = null
    }

    when (val current = state) {
        UpdateUiState.Idle, UpdateUiState.Checking -> UpdateDialogFrame(
            modifier = modifier,
            title = stringResource(Res.string.update_dialog_checking_title),
            confirmLabel = stringResource(Res.string.update_dialog_close),
            onConfirm = controller::closeDialog,
            onDismissRequest = controller::closeDialog,
        ) {
            Text(
                text = stringResource(Res.string.update_dialog_checking_body),
                style = MaterialTheme.typography.bodySmall,
                color = ClipboxTokens.colors.ink2,
            )
        }

        is UpdateUiState.Latest -> {
            val published = formatPublishedDate(current.descriptor.publishedAt, language)
            UpdateDialogFrame(
                modifier = modifier,
                title = stringResource(Res.string.update_dialog_latest_title),
                confirmLabel = stringResource(Res.string.update_dialog_close),
                onConfirm = controller::closeDialog,
                onDismissRequest = controller::closeDialog,
            ) {
                Text(
                    text = stringResource(Res.string.update_dialog_latest_body, currentVersion),
                    style = MaterialTheme.typography.bodySmall,
                    color = ClipboxTokens.colors.ink2,
                )
                if (published != null) {
                    MonoLine(published)
                }
            }
        }

        is UpdateUiState.Available -> {
            if (riskConfirm) {
                RiskContent(
                    modifier = modifier,
                    platform = current.platform,
                    onContinue = {
                        riskConfirm = false
                        controller.startDownload()
                    },
                    onCancel = { riskConfirm = false },
                    onDismissRequest = controller::closeDialog,
                )
            } else {
                AvailableContent(
                    modifier = modifier,
                    descriptor = current.descriptor,
                    platform = current.platform,
                    currentVersion = currentVersion,
                    onDownload = { riskConfirm = true },
                    onLater = controller::closeDialog,
                    onDismissRequest = controller::closeDialog,
                )
            }
        }

        is UpdateUiState.Downloading -> UpdateDialogFrame(
            modifier = modifier,
            title = stringResource(Res.string.update_dialog_downloading_title),
            confirmLabel = stringResource(Res.string.update_dialog_cancel_download),
            onConfirm = controller::cancelDownload,
            dismissLabel = stringResource(Res.string.update_dialog_close),
            onDismiss = controller::closeDialog,
            onDismissRequest = controller::closeDialog,
        ) {
            SegmentProgress(current.downloadedBytes, current.totalBytes)
            Spacer(Modifier.height(ClipboxTokens.spacing.m))
            Text(
                text = stringResource(
                    Res.string.update_dialog_download_progress,
                    TextSize.formatFileSize(current.downloadedBytes),
                    if (current.totalBytes > 0) TextSize.formatFileSize(current.totalBytes) else "—",
                ),
                style = ClipboxTokens.typography.mono,
                color = ClipboxTokens.colors.ink2,
            )
        }

        is UpdateUiState.Verifying -> UpdateDialogFrame(
            modifier = modifier,
            title = stringResource(Res.string.update_dialog_verifying_title),
            confirmLabel = stringResource(Res.string.update_dialog_close),
            onConfirm = controller::closeDialog,
            onDismissRequest = controller::closeDialog,
        ) {
            Text(
                text = stringResource(Res.string.update_dialog_verifying_body),
                style = MaterialTheme.typography.bodySmall,
                color = ClipboxTokens.colors.ink2,
            )
            current.platform.sha256?.let { MonoLine(it) }
        }

        is UpdateUiState.Ready -> UpdateDialogFrame(
            modifier = modifier,
            title = stringResource(Res.string.update_dialog_ready_title),
            confirmLabel = stringResource(Res.string.update_dialog_install),
            onConfirm = {
                scope.launch {
                    when (val result = controller.installNow()) {
                        UpdateInstallResult.Started -> controller.closeDialog()
                        UpdateInstallResult.PermissionRequired,
                        UpdateInstallResult.Unavailable -> installNotice = result

                        UpdateInstallResult.Failed -> Unit
                    }
                }
            },
            dismissLabel = stringResource(Res.string.update_dialog_later),
            onDismiss = controller::closeDialog,
            onDismissRequest = controller::closeDialog,
        ) {
            Text(
                text = stringResource(Res.string.update_dialog_ready_body),
                style = MaterialTheme.typography.bodySmall,
                color = ClipboxTokens.colors.ink2,
            )
            current.platform.file?.let { MonoLine(it) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ClipboxTokens.spacing.s),
            ) {
                current.platform.sizeBytes?.let {
                    Text(
                        text = TextSize.formatFileSize(it),
                        style = ClipboxTokens.typography.mono,
                        color = ClipboxTokens.colors.ink2,
                    )
                }
                Text(
                    text = stringResource(Res.string.update_dialog_sha_match),
                    style = ClipboxTokens.typography.mono,
                    color = ClipboxTokens.colors.ok,
                )
            }
            Spacer(Modifier.height(ClipboxTokens.spacing.s))
            Text(
                text = stringResource(Res.string.update_dialog_ready_note),
                style = MaterialTheme.typography.bodySmall,
                color = ClipboxTokens.colors.ink3,
            )
            installNotice?.let { notice ->
                Spacer(Modifier.height(ClipboxTokens.spacing.s))
                Text(
                    text = stringResource(
                        if (notice == UpdateInstallResult.PermissionRequired) {
                            Res.string.update_install_permission_required
                        } else {
                            Res.string.update_install_unavailable
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = ClipboxTokens.colors.warn,
                )
            }
        }

        is UpdateUiState.Failed -> UpdateDialogFrame(
            modifier = modifier,
            title = stringResource(Res.string.update_dialog_failed_title),
            confirmLabel = stringResource(Res.string.update_dialog_retry),
            onConfirm = { controller.retry() },
            dismissLabel = stringResource(Res.string.update_dialog_close),
            onDismiss = controller::closeDialog,
            onDismissRequest = controller::closeDialog,
        ) {
            Text(
                text = current.error.label(),
                style = MaterialTheme.typography.bodyMedium,
                color = ClipboxTokens.colors.danger,
            )
            current.descriptor?.let { descriptor ->
                Spacer(Modifier.height(ClipboxTokens.spacing.s))
                Text(
                    text = stringResource(
                        Res.string.update_dialog_version_line,
                        currentVersion,
                        descriptor.version,
                    ),
                    style = ClipboxTokens.typography.mono,
                    color = ClipboxTokens.colors.ink2,
                )
                formatPublishedDate(descriptor.publishedAt, language)?.let { MonoLine(it) }
            }
        }

        is UpdateUiState.Unsupported -> {
            val published = formatPublishedDate(current.descriptor.publishedAt, language)
            UpdateDialogFrame(
                modifier = modifier,
                title = stringResource(Res.string.update_dialog_available_title),
                confirmLabel = stringResource(Res.string.update_dialog_know),
                onConfirm = controller::closeDialog,
                onDismissRequest = controller::closeDialog,
            ) {
                Text(
                    text = stringResource(
                        Res.string.update_dialog_kicker,
                        current.descriptor.version,
                    ),
                    style = ClipboxTokens.typography.mono,
                    color = ClipboxTokens.colors.ink3,
                )
                Text(
                    text = stringResource(
                        Res.string.update_dialog_version_line,
                        currentVersion,
                        current.descriptor.version,
                    ),
                    style = ClipboxTokens.typography.mono.copy(fontWeight = FontWeight.Medium),
                    color = ClipboxTokens.colors.accent,
                )
                if (published != null) {
                    MonoLine(published)
                }
                Spacer(Modifier.height(ClipboxTokens.spacing.m))
                Text(
                    text = stringResource(
                        if (isIos) {
                            Res.string.update_dialog_unsupported_ios
                        } else {
                            Res.string.update_dialog_unsupported_other
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = ClipboxTokens.colors.ink2,
                )
            }
        }
    }
}

@Composable
private fun AvailableContent(
    modifier: Modifier,
    descriptor: UpdateDescriptor,
    platform: UpdatePlatform,
    currentVersion: String,
    onDownload: () -> Unit,
    onLater: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val language = LocalAppLanguage.current
    val published = formatPublishedDate(descriptor.publishedAt, language) ?: "—"
    val size = TextSize.formatFileSize(platform.sizeBytes ?: 0)
    val notes = descriptor.notes.forLanguage(language)

    UpdateDialogFrame(
        modifier = modifier,
        title = stringResource(Res.string.update_dialog_available_title),
        confirmLabel = stringResource(Res.string.update_dialog_download),
        onConfirm = onDownload,
        dismissLabel = stringResource(Res.string.update_dialog_later),
        onDismiss = onLater,
        onDismissRequest = onDismissRequest,
    ) {
        Text(
            text = stringResource(Res.string.update_dialog_kicker, descriptor.version),
            style = ClipboxTokens.typography.mono,
            color = ClipboxTokens.colors.ink3,
        )
        Text(
            text = stringResource(Res.string.update_dialog_version_line, currentVersion, descriptor.version),
            style = ClipboxTokens.typography.mono.copy(fontWeight = FontWeight.Medium),
            color = ClipboxTokens.colors.accent,
        )
        Text(
            text = stringResource(
                Res.string.update_dialog_meta_line,
                published,
                platform.platformLabel(),
                size,
            ),
            style = ClipboxTokens.typography.mono,
            color = ClipboxTokens.colors.ink2,
        )
        Spacer(Modifier.height(ClipboxTokens.spacing.m))
        Text(
            text = stringResource(Res.string.update_dialog_notes),
            style = ClipboxTokens.typography.mono,
            color = ClipboxTokens.colors.ink3,
        )
        Spacer(Modifier.height(ClipboxTokens.spacing.xs))
        Text(
            text = notes,
            modifier = Modifier
                .heightIn(max = 220.dp)
                .verticalScroll(rememberScrollState()),
            style = MaterialTheme.typography.bodySmall,
            color = ClipboxTokens.colors.ink2,
        )
    }
}

@Composable
private fun RiskContent(
    modifier: Modifier,
    platform: UpdatePlatform,
    onContinue: () -> Unit,
    onCancel: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val insecure = platform.downloadUrl?.startsWith("http://") == true

    UpdateDialogFrame(
        modifier = modifier,
        title = stringResource(Res.string.update_risk_title),
        confirmLabel = stringResource(Res.string.update_risk_continue),
        onConfirm = onContinue,
        dismissLabel = stringResource(Res.string.history_cancel),
        onDismiss = onCancel,
        onDismissRequest = onDismissRequest,
    ) {
        if (insecure) {
            RiskBlock(
                tag = stringResource(Res.string.update_risk_tag_http),
                title = stringResource(Res.string.update_risk_http_title),
                body = stringResource(Res.string.update_risk_http_body),
            )
            Spacer(Modifier.height(ClipboxTokens.spacing.m))
        }
        RiskBlock(
            tag = stringResource(Res.string.update_risk_tag_unsigned),
            title = stringResource(Res.string.update_risk_unsigned_title),
            body = stringResource(Res.string.update_risk_unsigned_body),
        )
        Spacer(Modifier.height(ClipboxTokens.spacing.m))
        Text(
            text = stringResource(Res.string.update_risk_footnote),
            style = MaterialTheme.typography.bodySmall,
            color = ClipboxTokens.colors.ink3,
        )
    }
}

@Composable
private fun RiskBlock(tag: String, title: String, body: String) {
    val colors = ClipboxTokens.colors
    val shape = RoundedCornerShape(ClipboxTokens.radii.button)
    Row(horizontalArrangement = Arrangement.spacedBy(ClipboxTokens.spacing.s)) {
        Text(
            text = tag,
            modifier = Modifier
                .border(1.dp, colors.warn, shape)
                .padding(horizontal = ClipboxTokens.spacing.s, vertical = 2.dp),
            style = ClipboxTokens.typography.mono,
            color = colors.warn,
        )
    }
    Spacer(Modifier.height(ClipboxTokens.spacing.xs))
    Text(
        text = title,
        style = MaterialTheme.typography.bodyMedium,
        color = colors.ink,
    )
    Text(
        text = body,
        style = MaterialTheme.typography.bodySmall,
        color = colors.ink2,
    )
}

@Composable
private fun SegmentProgress(downloadedBytes: Long, totalBytes: Long) {
    val colors = ClipboxTokens.colors
    if (totalBytes <= 0) {
        // 服务端未给出总大小时无法画确定刻度，退化为平台不定态进度
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
            color = colors.accent,
            trackColor = colors.panel2,
        )
        return
    }
    val filled = ((downloadedBytes.toDouble() / totalBytes) * PROGRESS_SEGMENTS)
        .toInt()
        .coerceIn(0, PROGRESS_SEGMENTS)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(PROGRESS_SEGMENTS) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(8.dp)
                    .background(if (index < filled) colors.accent else colors.panel2),
            )
        }
    }
}

@Composable
private fun UpdateDialogFrame(
    modifier: Modifier,
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    dismissLabel: String? = null,
    onDismiss: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = ClipboxTokens.colors

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        containerColor = colors.background,
        titleContentColor = colors.ink,
        textContentColor = colors.ink2,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column {
                content()
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = confirmLabel, color = colors.accent)
            }
        },
        dismissButton = {
            if (dismissLabel != null && onDismiss != null) {
                TextButton(onClick = onDismiss) {
                    Text(text = dismissLabel, color = colors.ink2)
                }
            }
        },
    )
}

@Composable
private fun MonoLine(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(top = ClipboxTokens.spacing.xs),
        style = ClipboxTokens.typography.mono,
        color = ClipboxTokens.colors.ink3,
    )
}

@Composable
private fun UpdatePlatform.platformLabel(): String {
    val resource = when (UpdatePlatformId.fromWire(id)) {
        UpdatePlatformId.ANDROID -> Res.string.update_platform_android
        UpdatePlatformId.IOS -> Res.string.update_platform_ios
        UpdatePlatformId.MACOS -> Res.string.update_platform_macos
        UpdatePlatformId.WINDOWS -> Res.string.update_platform_windows
        UpdatePlatformId.LINUX -> Res.string.update_platform_linux
        null -> null
    }
    return resource?.let { stringResource(it) } ?: id
}

// 错误码到文案的唯一映射点，弹窗不展示服务端原始 message
@Composable
private fun UpdateError.label(): String = stringResource(
    when (this) {
        UpdateError.OFFLINE -> Res.string.update_error_offline
        UpdateError.TIMEOUT -> Res.string.update_error_timeout
        UpdateError.RATE_LIMITED -> Res.string.update_error_rate_limited
        UpdateError.SERVICE_UNAVAILABLE -> Res.string.update_error_service_unavailable
        UpdateError.INVALID_RESPONSE -> Res.string.update_error_invalid_response
        UpdateError.DOWNLOAD_FAILED -> Res.string.update_error_download_failed
        UpdateError.CHECKSUM_MISMATCH -> Res.string.update_error_checksum_mismatch
        UpdateError.INSTALL_FAILED -> Res.string.update_error_install_failed
    },
)
