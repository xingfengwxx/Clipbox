/**
 * author : 王星星
 * date : 2026-11-17 13:40
 * email : 1099420259@qq.com
 * description : 二次确认对话框；危险色只用于确认按钮，取消不产生任何副作用，由调用方保证
 */
package com.wangxingxing.clipbox.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.wangxingxing.clipbox.resources.Res
import com.wangxingxing.clipbox.resources.history_cancel
import com.wangxingxing.clipbox.ui.theme.ClipboxTokens
import org.jetbrains.compose.resources.stringResource

@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = ClipboxTokens.colors

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.background,
        titleContentColor = colors.ink,
        textContentColor = colors.ink2,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = confirmText, color = colors.danger)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(Res.string.history_cancel),
                    color = colors.ink2,
                )
            }
        },
    )
}
