/**
 * author : 王星星
 * date : 2026-11-17 16:20
 * email : 1099420259@qq.com
 * description : 工具箱 S15：五张工具卡与本地说明；未配对也可进入。工具详情占位页复用 ToolScaffold 验证输入、输出、复制与清空链路，真实计算由 T-UI-12…16 替换。
 *               注意：本页不依赖同步、数据库或网络，卡片只做本地导航
 */
package com.wangxingxing.clipbox.ui.toolbox

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wangxingxing.clipbox.resources.Res
import com.wangxingxing.clipbox.resources.tool_kicker
import com.wangxingxing.clipbox.resources.tool_local_note
import com.wangxingxing.clipbox.resources.tool_tag
import com.wangxingxing.clipbox.ui.navigation.TopLevelDestination
import com.wangxingxing.clipbox.ui.theme.ClipboxShapes
import com.wangxingxing.clipbox.ui.theme.ClipboxTokens
import org.jetbrains.compose.resources.stringResource

@Composable
fun ToolboxRoute(
    onOpenTool: (ToolId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ClipboxTokens.colors
    val spacing = ClipboxTokens.spacing
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        ToolboxTopBar()
        HorizontalDivider(thickness = 1.dp, color = colors.ink)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = spacing.l, end = spacing.l, top = spacing.m, bottom = spacing.l),
        ) {
            ToolId.entries.chunked(2).forEach { rowTools ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rowTools.forEach { tool ->
                        ToolCard(
                            tool = tool,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            onClick = { onOpenTool(tool) },
                        )
                    }
                    if (rowTools.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            LocalNoteCard()
        }
    }
}

@Composable
fun ToolDetailRoute(
    toolId: ToolId,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var input by rememberSaveable(toolId) { mutableStateOf("") }
    val overLimit = rememberInputByteCount(input) > ToolInputLimit.MAX_BYTES
    // 骨架阶段输入原样进入输出，用于验证复制与清空；各工具卡会替换为真实计算
    val output = if (overLimit) "" else input
    ToolScaffold(
        title = stringResource(toolId.title),
        onBack = onBack,
        input = input,
        onInputChange = { input = it },
        output = output,
        onClear = {},
        modifier = modifier,
    )
}

@Composable
private fun ToolboxTopBar() {
    val colors = ClipboxTokens.colors
    val spacing = ClipboxTokens.spacing
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = spacing.l, top = spacing.m, end = spacing.l, bottom = spacing.m),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(TopLevelDestination.Toolbox.label),
                style = MaterialTheme.typography.titleLarge,
                color = colors.ink,
            )
            Spacer(Modifier.width(spacing.s))
            Text(
                text = stringResource(Res.string.tool_tag),
                modifier = Modifier
                    .border(1.dp, colors.line, ClipboxShapes.small)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
                style = ClipboxTokens.typography.mono,
                color = colors.ink3,
            )
        }
        Spacer(Modifier.height(spacing.xs))
        Text(
            text = stringResource(Res.string.tool_kicker),
            style = ClipboxTokens.typography.mono,
            color = colors.ink3,
        )
    }
}

@Composable
private fun ToolCard(
    tool: ToolId,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ClipboxTokens.colors
    val spacing = ClipboxTokens.spacing
    Column(
        modifier = modifier
            .heightIn(min = 146.dp)
            .background(colors.panel)
            .border(1.dp, colors.line)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(spacing.m),
    ) {
        ToolIconBox(toolIcon(tool))
        Spacer(Modifier.height(spacing.xs))
        Text(
            text = stringResource(tool.title),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = colors.ink,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(tool.description),
            style = MaterialTheme.typography.bodySmall,
            color = colors.ink3,
        )
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(spacing.s))
        Text(
            text = stringResource(Res.string.tool_tag),
            modifier = Modifier
                .border(1.dp, colors.line, ClipboxShapes.small)
                .padding(horizontal = 5.dp, vertical = 1.dp),
            style = ClipboxTokens.typography.mono,
            color = colors.ink3,
        )
    }
}

@Composable
private fun ToolIconBox(icon: ImageVector) {
    val colors = ClipboxTokens.colors
    Box(
        modifier = Modifier
            .size(34.dp)
            .border(1.dp, colors.line),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun LocalNoteCard() {
    val colors = ClipboxTokens.colors
    val spacing = ClipboxTokens.spacing
    Text(
        text = stringResource(Res.string.tool_local_note),
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(
                    color = colors.line,
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f)),
                    ),
                    topLeft = Offset.Zero,
                    size = this.size,
                )
            }
            .padding(spacing.m),
        style = MaterialTheme.typography.bodySmall,
        color = colors.ink3,
    )
}

private fun toolIcon(tool: ToolId): ImageVector = when (tool) {
    ToolId.Json -> ToolIcons.Json
    ToolId.Base64 -> ToolIcons.Base64
    ToolId.Url -> ToolIcons.Url
    ToolId.Time -> ToolIcons.Time
    ToolId.Regex -> ToolIcons.Regex
}
