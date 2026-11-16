/**
 * author : 王星星
 * date : 2026-11-16 18:05
 * email : 1099420259@qq.com
 * description : 历史页发送器：多行输入、UTF-8 字节计数与 64 KiB 上限禁用；90% 预警与超限复制降级由 T-UI-04 补齐
 */
package com.wangxingxing.clipbox.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wangxingxing.clipbox.resources.Res
import com.wangxingxing.clipbox.resources.history_bytes_counter
import com.wangxingxing.clipbox.resources.history_composer_label
import com.wangxingxing.clipbox.resources.history_composer_local
import com.wangxingxing.clipbox.resources.history_composer_placeholder
import com.wangxingxing.clipbox.resources.history_over_limit
import com.wangxingxing.clipbox.resources.history_send
import com.wangxingxing.clipbox.ui.theme.ClipboxTokens
import com.wangxingxing.clipbox.util.TextSize
import org.jetbrains.compose.resources.stringResource

private val ComposerChromeHeight = 96.dp

@Composable
fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    maxHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = ClipboxTokens.colors
    val spacing = ClipboxTokens.spacing
    val typography = ClipboxTokens.typography
    val shape = RoundedCornerShape(ClipboxTokens.radii.button)
    val bytes = TextSize.utf8ByteCount(value)
    val overLimit = bytes > TextSize.MAX_CLIP_UTF8_BYTES
    val canSend = value.isNotBlank() && !overLimit
    // 输入区最多占屏幕 1/3，超出后由输入框内部滚动，保证列表始终可见
    val textMaxHeight = (maxHeight - ComposerChromeHeight).coerceAtLeast(72.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.panel, shape)
            .border(1.dp, colors.line, shape)
            .padding(spacing.m),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(Res.string.history_composer_label),
                style = typography.mono,
                color = colors.ink2,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(
                    Res.string.history_bytes_counter,
                    TextSize.formatByteCount(bytes),
                    TextSize.formatByteCount(TextSize.MAX_CLIP_UTF8_BYTES),
                ),
                style = typography.mono,
                color = if (overLimit) colors.danger else colors.ink3,
            )
        }
        Spacer(Modifier.height(6.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp, max = textMaxHeight),
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.ink),
            cursorBrush = SolidColor(colors.accent),
            decorationBox = { innerTextField ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.history_composer_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.ink3,
                        )
                    }
                    innerTextField()
                }
            },
        )
        Spacer(Modifier.height(spacing.s))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(
                    if (overLimit) Res.string.history_over_limit else Res.string.history_composer_local,
                ),
                style = typography.mono,
                color = if (overLimit) colors.danger else colors.ink3,
            )
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onSend,
                enabled = canSend,
                modifier = Modifier.height(44.dp),
                shape = shape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.onAccent,
                    disabledContainerColor = colors.panel2,
                    disabledContentColor = colors.ink2,
                ),
                contentPadding = PaddingValues(horizontal = spacing.l),
            ) {
                Text(
                    text = stringResource(Res.string.history_send),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
