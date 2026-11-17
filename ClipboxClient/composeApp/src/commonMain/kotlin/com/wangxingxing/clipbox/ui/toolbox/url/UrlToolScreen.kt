/**
 * author : 王星星
 * date : 2026-11-17 16:27
 * email : 1099420259@qq.com
 * description : URL 工具页：组件 / 整串模式与 `+` 空格开关，编码与解码；非法百分号序列给出位置提示；
 *               计算在 Dispatchers.Default，请求带版本号丢弃过期结果
 */
package com.wangxingxing.clipbox.ui.toolbox.url

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.wangxingxing.clipbox.resources.Res
import com.wangxingxing.clipbox.resources.history_state_failed
import com.wangxingxing.clipbox.resources.tool_decode
import com.wangxingxing.clipbox.resources.tool_encode
import com.wangxingxing.clipbox.resources.tool_url
import com.wangxingxing.clipbox.resources.tool_url_component
import com.wangxingxing.clipbox.resources.tool_url_error
import com.wangxingxing.clipbox.resources.tool_url_plus
import com.wangxingxing.clipbox.resources.tool_url_whole
import com.wangxingxing.clipbox.toolbox.UrlResult
import com.wangxingxing.clipbox.toolbox.UrlTool
import com.wangxingxing.clipbox.ui.theme.ClipboxTokens
import com.wangxingxing.clipbox.ui.toolbox.ToolActionButton
import com.wangxingxing.clipbox.ui.toolbox.ToolFlag
import com.wangxingxing.clipbox.ui.toolbox.ToolInputLimit
import com.wangxingxing.clipbox.ui.toolbox.ToolScaffold
import com.wangxingxing.clipbox.ui.toolbox.rememberInputByteCount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

private sealed interface UrlOutputState {
    data object None : UrlOutputState
    data class Text(val value: String) : UrlOutputState
    data class Invalid(val position: Int) : UrlOutputState
}

@Composable
fun UrlToolRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var input by rememberSaveable { mutableStateOf("") }
    var wholeUrl by rememberSaveable { mutableStateOf(false) }
    var plusAsSpace by rememberSaveable { mutableStateOf(false) }
    var outputState by remember { mutableStateOf<UrlOutputState>(UrlOutputState.None) }
    var requestVersion by remember { mutableIntStateOf(0) }
    val overLimit = rememberInputByteCount(input) > ToolInputLimit.MAX_BYTES
    val scope = rememberCoroutineScope()
    val spacing = ClipboxTokens.spacing

    fun run(encode: Boolean) {
        val text = input
        val whole = wholeUrl
        val plus = plusAsSpace
        val version = ++requestVersion
        scope.launch {
            val next = withContext(Dispatchers.Default) {
                if (encode) {
                    UrlOutputState.Text(
                        UrlTool.encode(text, wholeUrl = whole, plusAsSpace = plus),
                    )
                } else {
                    when (val result = UrlTool.decode(text, plusAsSpace = plus)) {
                        is UrlResult.Success -> UrlOutputState.Text(result.text)
                        is UrlResult.InvalidPercent -> UrlOutputState.Invalid(result.position)
                    }
                }
            }
            if (version == requestVersion) outputState = next
        }
    }

    val outputText = when (val current = outputState) {
        UrlOutputState.None -> ""
        is UrlOutputState.Text -> current.value
        is UrlOutputState.Invalid ->
            stringResource(Res.string.tool_url_error, current.position)
    }

    ToolScaffold(
        title = stringResource(Res.string.tool_url),
        onBack = onBack,
        input = input,
        onInputChange = {
            input = it
            outputState = UrlOutputState.None
        },
        output = if (overLimit) "" else outputText,
        onClear = { outputState = UrlOutputState.None },
        modifier = modifier,
        outputError = outputState is UrlOutputState.Invalid,
        outputStatus = if (outputState is UrlOutputState.Invalid) {
            stringResource(Res.string.history_state_failed)
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
                    text = stringResource(Res.string.tool_url_component),
                    selected = !wholeUrl,
                    onClick = { wholeUrl = false },
                )
                ToolFlag(
                    text = stringResource(Res.string.tool_url_whole),
                    selected = wholeUrl,
                    onClick = { wholeUrl = true },
                )
                ToolFlag(
                    text = stringResource(Res.string.tool_url_plus),
                    selected = plusAsSpace,
                    onClick = { plusAsSpace = !plusAsSpace },
                )
            }
        },
    ) {
        ToolActionButton(
            text = stringResource(Res.string.tool_decode),
            primary = true,
            enabled = !overLimit,
            onClick = { run(encode = false) },
        )
        ToolActionButton(
            text = stringResource(Res.string.tool_encode),
            enabled = !overLimit,
            onClick = { run(encode = true) },
        )
    }
}
