/**
 * author : 王星星
 * date : 2026-11-17 16:31
 * email : 1099420259@qq.com
 * description : 正则工具页：正则表达式输入、忽略大小写 / 多行 / 点匹配换行三个标志、实时匹配；
 *               结果面板按匹配区间给原文加背景高亮，并列出每处起止位置与编号分组，复制内容为纯文本报告。
 *               注意：匹配在 Dispatchers.Default 执行，输入为空或超过 100 KiB 时不计算；高亮只加样式，不改原文
 */
package com.wangxingxing.clipbox.ui.toolbox.regex

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import com.wangxingxing.clipbox.resources.Res
import com.wangxingxing.clipbox.resources.history_state_failed
import com.wangxingxing.clipbox.resources.tool_groups
import com.wangxingxing.clipbox.resources.tool_pattern
import com.wangxingxing.clipbox.resources.tool_regex
import com.wangxingxing.clipbox.resources.tool_regex_dotall
import com.wangxingxing.clipbox.resources.tool_regex_ignore_case
import com.wangxingxing.clipbox.resources.tool_regex_invalid
import com.wangxingxing.clipbox.resources.tool_regex_matches
import com.wangxingxing.clipbox.resources.tool_regex_multiline
import com.wangxingxing.clipbox.resources.tool_regex_no_match
import com.wangxingxing.clipbox.toolbox.RegexMatch
import com.wangxingxing.clipbox.toolbox.RegexResult
import com.wangxingxing.clipbox.toolbox.RegexTool
import com.wangxingxing.clipbox.ui.theme.ClipboxShapes
import com.wangxingxing.clipbox.ui.theme.ClipboxTokens
import com.wangxingxing.clipbox.ui.toolbox.ToolFlag
import com.wangxingxing.clipbox.ui.toolbox.ToolInputLimit
import com.wangxingxing.clipbox.ui.toolbox.ToolScaffold
import com.wangxingxing.clipbox.ui.toolbox.rememberInputByteCount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun RegexToolRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pattern by rememberSaveable { mutableStateOf("") }
    var source by rememberSaveable { mutableStateOf("") }
    var ignoreCase by rememberSaveable { mutableStateOf(false) }
    var multiline by rememberSaveable { mutableStateOf(false) }
    var dotMatchesAll by rememberSaveable { mutableStateOf(false) }
    var result by remember { mutableStateOf<RegexResult?>(null) }
    val overLimit = rememberInputByteCount(source) > ToolInputLimit.MAX_BYTES
    val colors = ClipboxTokens.colors
    val spacing = ClipboxTokens.spacing
    val fieldShape = ClipboxShapes.small
    val monoSmall = ClipboxTokens.typography.mono.copy(
        fontSize = MaterialTheme.typography.bodySmall.fontSize,
        lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
    )

    LaunchedEffect(pattern, source, ignoreCase, multiline, dotMatchesAll, overLimit) {
        if (pattern.isEmpty() || source.isEmpty() || overLimit) {
            result = null
            return@LaunchedEffect
        }
        result = withContext(Dispatchers.Default) {
            RegexTool.search(
                pattern = pattern,
                text = source,
                ignoreCase = ignoreCase,
                multiline = multiline,
                dotMatchesAll = dotMatchesAll,
            )
        }
    }

    val matchResult = result as? RegexResult.Matches
    val matchCount = matchResult?.matches?.size ?: 0
    val countLabel = pluralStringResource(Res.plurals.tool_regex_matches, matchCount, matchCount)
    val groupsLabel = stringResource(Res.string.tool_groups)
    val noMatchLabel = stringResource(Res.string.tool_regex_no_match)
    val invalidLabel = stringResource(Res.string.tool_regex_invalid)
    val failedLabel = stringResource(Res.string.history_state_failed)
    val report = remember(matchResult, countLabel, groupsLabel) {
        matchResult?.let { buildReport(it.matches, countLabel, groupsLabel) }.orEmpty()
    }
    val highlightColor = colors.warn.copy(alpha = 0.28f)
    val highlighted = remember(matchResult, source, highlightColor) {
        matchResult?.let { highlightMatches(source, it.matches, highlightColor) }
    }

    val output = when (val current = result) {
        null -> ""
        is RegexResult.Matches -> report
        RegexResult.NoMatch -> noMatchLabel
        is RegexResult.Invalid -> if (current.reason.isBlank()) {
            invalidLabel
        } else {
            "$invalidLabel\n${current.reason}"
        }
    }

    ToolScaffold(
        title = stringResource(Res.string.tool_regex),
        onBack = onBack,
        input = source,
        onInputChange = { source = it },
        output = if (overLimit) "" else output,
        onClear = { result = null },
        modifier = modifier,
        outputError = result is RegexResult.NoMatch || result is RegexResult.Invalid,
        outputStatus = when {
            matchResult != null -> countLabel
            result is RegexResult.NoMatch || result is RegexResult.Invalid -> failedLabel
            else -> null
        },
        header = {
            Text(
                text = stringResource(Res.string.tool_pattern),
                style = ClipboxTokens.typography.mono,
                color = colors.ink2,
            )
            BasicTextField(
                value = pattern,
                onValueChange = { pattern = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = spacing.s, bottom = spacing.m)
                    .background(colors.panel, fieldShape)
                    .border(1.dp, colors.line, fieldShape)
                    .padding(horizontal = spacing.m, vertical = spacing.m),
                singleLine = true,
                textStyle = monoSmall.copy(color = colors.ink),
                cursorBrush = SolidColor(colors.accent),
            )
        },
        outputContent = if (highlighted != null) {
            {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
                    Text(text = highlighted, style = monoSmall, color = colors.ink)
                    Text(text = report, style = monoSmall, color = colors.ink2)
                }
            }
        } else {
            null
        },
        options = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = spacing.m),
                horizontalArrangement = Arrangement.spacedBy(spacing.s),
            ) {
                ToolFlag(
                    text = stringResource(Res.string.tool_regex_ignore_case),
                    selected = ignoreCase,
                    onClick = { ignoreCase = !ignoreCase },
                )
                ToolFlag(
                    text = stringResource(Res.string.tool_regex_multiline),
                    selected = multiline,
                    onClick = { multiline = !multiline },
                )
                ToolFlag(
                    text = stringResource(Res.string.tool_regex_dotall),
                    selected = dotMatchesAll,
                    onClick = { dotMatchesAll = !dotMatchesAll },
                )
            }
        },
    )
}

private fun buildReport(
    matches: List<RegexMatch>,
    countLabel: String,
    groupsLabel: String,
): String = buildString {
    append(countLabel)
    matches.forEachIndexed { index, match ->
        append('\n')
        append('#').append(index + 1).append("  ")
        append(displayRange(match.range)).append("  ")
        append(match.value)
        if (match.groups.isNotEmpty()) {
            append('\n').append(groupsLabel).append(' ')
            match.groups.forEachIndexed { groupIndex, group ->
                if (groupIndex > 0) append("  ")
                append(group.index).append('=')
                append(group.value.orEmpty())
            }
        }
    }
}

/** 展示用 1 基闭区间；空匹配的结束位会小于起始位，提示其长度为 0 */
private fun displayRange(range: IntRange): String = "${range.first + 1}–${range.last + 1}"

private fun highlightMatches(
    source: String,
    matches: List<RegexMatch>,
    color: Color,
): AnnotatedString = buildAnnotatedString {
    append(source)
    matches.forEach { match ->
        if (!match.range.isEmpty()) {
            addStyle(SpanStyle(background = color), match.range.first, match.range.last + 1)
        }
    }
}
