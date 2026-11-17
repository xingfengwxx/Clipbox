/**
 * author : 王星星
 * date : 2026-11-17 13:40
 * email : 1099420259@qq.com
 * description : 记录操作面板：复制 / 置顶 / 生成分享 / 重试 / 删除；分享在未同步或离线时禁用并展示原因，删除只做入口，二次确认由调用方负责
 */
package com.wangxingxing.clipbox.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wangxingxing.clipbox.model.Clip
import com.wangxingxing.clipbox.model.SyncState
import com.wangxingxing.clipbox.resources.Res
import com.wangxingxing.clipbox.resources.history_act_copy
import com.wangxingxing.clipbox.resources.history_act_delete
import com.wangxingxing.clipbox.resources.history_act_pin
import com.wangxingxing.clipbox.resources.history_act_retry
import com.wangxingxing.clipbox.resources.history_act_share
import com.wangxingxing.clipbox.resources.history_act_unpin
import com.wangxingxing.clipbox.ui.theme.ClipboxTokens
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipActionsSheet(
    clip: Clip,
    shareDisabledReason: String?,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onTogglePin: () -> Unit,
    onShare: (() -> Unit)?,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = ClipboxTokens.colors
    val spacing = ClipboxTokens.spacing

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.background)
                .padding(bottom = spacing.l),
        ) {
            SheetAction(
                label = stringResource(Res.string.history_act_copy),
                onClick = onCopy,
            )
            SheetAction(
                label = stringResource(
                    if (clip.pinned) Res.string.history_act_unpin else Res.string.history_act_pin,
                ),
                onClick = onTogglePin,
            )
            SheetAction(
                label = stringResource(Res.string.history_act_share),
                supportingText = shareDisabledReason,
                enabled = onShare != null && shareDisabledReason == null,
                onClick = { onShare?.invoke() },
            )
            if (clip.syncState.isFailed) {
                SheetAction(
                    label = stringResource(Res.string.history_act_retry),
                    onClick = onRetry,
                )
            }
            SheetAction(
                label = stringResource(Res.string.history_act_delete),
                labelColor = colors.danger,
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun SheetAction(
    label: String,
    onClick: () -> Unit,
    supportingText: String? = null,
    enabled: Boolean = true,
    labelColor: Color? = null,
) {
    val colors = ClipboxTokens.colors
    val spacing = ClipboxTokens.spacing
    val contentColor = when {
        !enabled -> colors.ink3
        labelColor != null -> labelColor
        else -> colors.ink
    }

    HorizontalDivider(thickness = 1.dp, color = colors.line)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (supportingText == null) 52.dp else 64.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = spacing.l),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(top = if (supportingText == null) 15.dp else 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor,
        )
        if (supportingText != null) {
            Text(
                text = supportingText,
                modifier = Modifier.padding(top = 2.dp),
                style = ClipboxTokens.typography.mono,
                color = colors.ink3,
            )
        }
    }
}

private val SyncState.isFailed: Boolean
    get() = this == SyncState.FAILED_CREATE ||
        this == SyncState.FAILED_UPDATE ||
        this == SyncState.FAILED_DELETE
