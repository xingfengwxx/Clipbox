/**
 * author : 王星星
 * date : 2026-11-17 16:20
 * email : 1099420259@qq.com
 * description : 五个工具页共用的 S15 骨架：返回、标题、100 KiB 上限、输入与字节计数、结果面板、复制与清空、错误区；
 *               复制只经 ClipboardService 写系统剪贴板，页面自身不写历史、不联网。输入超限时由调用方跳过计算并清空输出
 */
package com.wangxingxing.clipbox.ui.toolbox

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.wangxingxing.clipbox.platform.ClipboardService
import com.wangxingxing.clipbox.platform.ClipboardWriteResult
import com.wangxingxing.clipbox.resources.Res
import com.wangxingxing.clipbox.resources.clipboard_write_failed
import com.wangxingxing.clipbox.resources.tool_back
import com.wangxingxing.clipbox.resources.tool_clear
import com.wangxingxing.clipbox.resources.tool_copied
import com.wangxingxing.clipbox.resources.tool_copy
import com.wangxingxing.clipbox.resources.tool_input
import com.wangxingxing.clipbox.resources.tool_limit
import com.wangxingxing.clipbox.resources.tool_limit_badge
import com.wangxingxing.clipbox.resources.tool_output
import com.wangxingxing.clipbox.resources.tool_over_limit
import com.wangxingxing.clipbox.ui.navigation.NavIcons
import com.wangxingxing.clipbox.ui.theme.ClipboxShapes
import com.wangxingxing.clipbox.ui.theme.ClipboxTokens
import com.wangxingxing.clipbox.util.TextSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** 工具箱统一输入上限；按 UTF-8 字节而不是字符数计算 */
object ToolInputLimit {
    const val MAX_BYTES: Int = 102_400

    fun isWithin(text: String): Boolean = TextSize.utf8ByteCount(text) <= MAX_BYTES
}

/**
 * 后台统计字节数，避免 100 KiB 输入在每次重组时阻塞主线程。
 * 首帧用 length 下界做保守判断：length 不会超过 UTF-8 字节数，超长文本立刻按超限展示。
 */
@Composable
fun rememberInputByteCount(text: String): Int {
    val initial = if (text.length > ToolInputLimit.MAX_BYTES) ToolInputLimit.MAX_BYTES + 1 else 0
    val bytes by produceState(initialValue = initial, text) {
        value = withContext(Dispatchers.Default) { TextSize.utf8ByteCount(text) }
    }
    return bytes
}

@Composable
fun ToolScaffold(
    title: String,
    onBack: () -> Unit,
    input: String,
    onInputChange: (String) -> Unit,
    output: String,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    outputError: Boolean = false,
    outputStatus: String? = null,
    options: @Composable ColumnScope.() -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = ClipboxTokens.colors
    val spacing = ClipboxTokens.spacing
    val clipboardService = koinInject<ClipboardService>()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val copiedLabel = stringResource(Res.string.tool_copied)
    val writeFailedLabel = stringResource(Res.string.clipboard_write_failed)
    val inputBytes = rememberInputByteCount(input)
    val overLimit = inputBytes > ToolInputLimit.MAX_BYTES

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(Modifier.fillMaxSize()) {
            ToolTopBar(title = title, onBack = onBack)
            HorizontalDivider(thickness = 1.dp, color = colors.ink)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = spacing.l, vertical = spacing.m),
            ) {
                IoLabel(
                    label = stringResource(Res.string.tool_input),
                    trailing = stringResource(
                        Res.string.tool_limit,
                        TextSize.formatByteCount(inputBytes),
                    ),
                )
                BasicTextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = spacing.s)
                        .heightIn(min = 140.dp, max = 240.dp)
                        .background(colors.panel, ToolFieldShape)
                        .border(
                            1.dp,
                            if (overLimit) colors.danger else colors.line,
                            ToolFieldShape,
                        )
                        .padding(horizontal = spacing.m, vertical = spacing.m),
                    textStyle = ClipboxTokens.typography.mono.copy(
                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                        color = colors.ink,
                    ),
                    cursorBrush = SolidColor(colors.accent),
                )
                if (overLimit) {
                    Text(
                        text = stringResource(Res.string.tool_over_limit),
                        modifier = Modifier.padding(top = spacing.xs),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.danger,
                    )
                }
                options()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = spacing.m),
                    horizontalArrangement = Arrangement.spacedBy(spacing.s),
                ) {
                    actions()
                }
                Spacer(Modifier.height(spacing.l))
                IoLabel(
                    label = stringResource(Res.string.tool_output),
                    trailing = outputStatus,
                    trailingDanger = outputError,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = spacing.s)
                        .heightIn(min = 120.dp, max = 320.dp)
                        .background(colors.panel2, ToolFieldShape)
                        .border(1.dp, colors.line, ToolFieldShape)
                        .padding(horizontal = spacing.m, vertical = spacing.m),
                ) {
                    Text(
                        text = output,
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        style = ClipboxTokens.typography.mono.copy(
                            fontSize = MaterialTheme.typography.bodySmall.fontSize,
                            lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                            color = if (outputError) colors.danger else colors.ink,
                        ),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = spacing.m),
                    horizontalArrangement = Arrangement.spacedBy(spacing.s),
                ) {
                    ToolActionButton(
                        text = stringResource(Res.string.tool_copy),
                        enabled = output.isNotEmpty(),
                        onClick = {
                            scope.launch {
                                val message = when (clipboardService.writeText(output)) {
                                    ClipboardWriteResult.Success -> copiedLabel
                                    ClipboardWriteResult.Failed -> writeFailedLabel
                                }
                                snackbarHostState.showSnackbar(message)
                            }
                        },
                    )
                    ToolActionButton(
                        text = stringResource(Res.string.tool_clear),
                        onClick = {
                            onInputChange("")
                            onClear()
                        },
                    )
                }
                Spacer(Modifier.height(spacing.xl))
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(spacing.l),
        )
    }
}

@Composable
fun ToolActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = ClipboxTokens.colors
    val spacing = ClipboxTokens.spacing
    Text(
        text = text,
        modifier = modifier
            .heightIn(min = 48.dp)
            .background(
                when {
                    !enabled -> colors.panel
                    primary -> colors.accent
                    else -> colors.panel2
                },
                ToolFieldShape,
            )
            .border(
                1.dp,
                if (!enabled) colors.line else if (primary) colors.accent else colors.line,
                ToolFieldShape,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = spacing.l, vertical = spacing.m),
        style = MaterialTheme.typography.labelLarge,
        color = when {
            !enabled -> colors.ink3
            primary -> colors.onAccent
            else -> colors.ink
        },
    )
}

/** 工具页的模式开关（URL-safe、秒/毫秒、正则标志等），选中态用品牌墨色反白。 */
@Composable
fun ToolFlag(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ClipboxTokens.colors
    val spacing = ClipboxTokens.spacing
    Text(
        text = text,
        modifier = modifier
            .heightIn(min = 48.dp)
            .background(if (selected) colors.ink else colors.panel, ToolFieldShape)
            .border(1.dp, if (selected) colors.ink else colors.line, ToolFieldShape)
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.m, vertical = spacing.m),
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) colors.background else colors.ink2,
    )
}

@Composable
private fun ToolTopBar(title: String, onBack: () -> Unit) {
    val colors = ClipboxTokens.colors
    val spacing = ClipboxTokens.spacing
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = spacing.l, top = spacing.xs, end = spacing.l, bottom = spacing.s),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clickable(onClick = onBack),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = NavIcons.Back,
                contentDescription = null,
                tint = colors.ink2,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(Res.string.tool_back),
                style = MaterialTheme.typography.bodySmall,
                color = colors.ink2,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = colors.ink,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(Res.string.tool_limit_badge),
                modifier = Modifier
                    .border(1.dp, colors.line, ToolFieldShape)
                    .padding(horizontal = spacing.s, vertical = 2.dp),
                style = ClipboxTokens.typography.mono,
                color = colors.ink3,
            )
        }
    }
}

@Composable
private fun IoLabel(label: String, trailing: String?, trailingDanger: Boolean = false) {
    val colors = ClipboxTokens.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = ClipboxTokens.typography.mono,
            color = colors.ink2,
        )
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            Text(
                text = trailing,
                style = ClipboxTokens.typography.mono,
                color = if (trailingDanger) colors.danger else colors.ink2,
            )
        }
    }
}

private val ToolFieldShape = ClipboxShapes.small
