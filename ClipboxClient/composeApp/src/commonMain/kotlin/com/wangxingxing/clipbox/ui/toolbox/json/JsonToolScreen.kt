/**
 * author : 王星星
 * date : 2026-11-17 16:21
 * email : 1099420259@qq.com
 * description : JSON 工具页：格式化 / 压缩 / 校验三种操作，计算在 Dispatchers.Default，结果带行列表述；
 *               注意：请求带版本号，输入变化后旧结果直接丢弃，避免慢解析覆盖新输入
 */
package com.wangxingxing.clipbox.ui.toolbox.json

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
import com.wangxingxing.clipbox.resources.tool_format
import com.wangxingxing.clipbox.resources.tool_json
import com.wangxingxing.clipbox.resources.tool_json_error
import com.wangxingxing.clipbox.resources.tool_json_ok
import com.wangxingxing.clipbox.resources.tool_json_reason_brace
import com.wangxingxing.clipbox.resources.tool_json_reason_colon
import com.wangxingxing.clipbox.resources.tool_json_reason_early_end
import com.wangxingxing.clipbox.resources.tool_json_reason_quote
import com.wangxingxing.clipbox.resources.tool_json_reason_syntax
import com.wangxingxing.clipbox.resources.tool_minify
import com.wangxingxing.clipbox.resources.tool_validate
import com.wangxingxing.clipbox.toolbox.JsonErrorReason
import com.wangxingxing.clipbox.toolbox.JsonSyntaxError
import com.wangxingxing.clipbox.toolbox.JsonTool
import com.wangxingxing.clipbox.toolbox.JsonTransformResult
import com.wangxingxing.clipbox.ui.toolbox.ToolActionButton
import com.wangxingxing.clipbox.ui.toolbox.ToolInputLimit
import com.wangxingxing.clipbox.ui.toolbox.ToolScaffold
import com.wangxingxing.clipbox.ui.toolbox.rememberInputByteCount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

private enum class JsonAction { Format, Minify, Validate }

private sealed interface JsonOutputState {
    data object None : JsonOutputState
    data class Text(val value: String) : JsonOutputState
    data object Valid : JsonOutputState
    data class Invalid(val error: JsonSyntaxError) : JsonOutputState
}

@Composable
fun JsonToolRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var input by rememberSaveable { mutableStateOf("") }
    var outputState by remember { mutableStateOf<JsonOutputState>(JsonOutputState.None) }
    var requestVersion by remember { mutableIntStateOf(0) }
    val overLimit = rememberInputByteCount(input) > ToolInputLimit.MAX_BYTES
    val scope = rememberCoroutineScope()

    fun run(action: JsonAction) {
        val text = input
        val version = ++requestVersion
        scope.launch {
            val next = withContext(Dispatchers.Default) {
                when (action) {
                    JsonAction.Format -> JsonTool.format(text).toState()
                    JsonAction.Minify -> JsonTool.minify(text).toState()
                    JsonAction.Validate -> JsonTool.validate(text)
                        ?.let { JsonOutputState.Invalid(it) }
                        ?: JsonOutputState.Valid
                }
            }
            if (version == requestVersion) outputState = next
        }
    }

    val outputText = when (val current = outputState) {
        JsonOutputState.None -> ""
        is JsonOutputState.Text -> current.value
        JsonOutputState.Valid -> stringResource(Res.string.tool_json_ok)
        is JsonOutputState.Invalid -> jsonErrorText(current.error)
    }
    val status = when (outputState) {
        JsonOutputState.None -> null
        is JsonOutputState.Invalid -> stringResource(Res.string.history_state_failed)
        else -> stringResource(Res.string.tool_json_ok)
    }

    ToolScaffold(
        title = stringResource(Res.string.tool_json),
        onBack = onBack,
        input = input,
        onInputChange = {
            input = it
            outputState = JsonOutputState.None
        },
        output = if (overLimit) "" else outputText,
        onClear = { outputState = JsonOutputState.None },
        modifier = modifier,
        outputError = outputState is JsonOutputState.Invalid,
        outputStatus = if (overLimit) null else status,
    ) {
        ToolActionButton(
            text = stringResource(Res.string.tool_format),
            primary = true,
            enabled = !overLimit,
            onClick = { run(JsonAction.Format) },
        )
        ToolActionButton(
            text = stringResource(Res.string.tool_minify),
            enabled = !overLimit,
            onClick = { run(JsonAction.Minify) },
        )
        ToolActionButton(
            text = stringResource(Res.string.tool_validate),
            enabled = !overLimit,
            onClick = { run(JsonAction.Validate) },
        )
    }
}

private fun JsonTransformResult.toState(): JsonOutputState = when (this) {
    is JsonTransformResult.Success -> JsonOutputState.Text(output)
    is JsonTransformResult.Invalid -> JsonOutputState.Invalid(error)
}

@Composable
private fun jsonErrorText(error: JsonSyntaxError): String {
    val reason = when (error.reason) {
        JsonErrorReason.EarlyEnd -> stringResource(Res.string.tool_json_reason_early_end)
        JsonErrorReason.MissingCommaOrBrace -> stringResource(Res.string.tool_json_reason_brace)
        JsonErrorReason.MissingColon -> stringResource(Res.string.tool_json_reason_colon)
        JsonErrorReason.MissingQuotation -> stringResource(Res.string.tool_json_reason_quote)
        JsonErrorReason.UnexpectedToken -> stringResource(Res.string.tool_json_reason_syntax)
    }
    return stringResource(Res.string.tool_json_error, error.line, error.column, reason)
}
