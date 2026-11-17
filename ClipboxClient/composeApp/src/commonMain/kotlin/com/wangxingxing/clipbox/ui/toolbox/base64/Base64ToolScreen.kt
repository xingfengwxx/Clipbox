/**
 * author : 王星星
 * date : 2026-11-17 16:27
 * email : 1099420259@qq.com
 * description : Base64 工具页：标准 / URL-safe 开关、编码与解码；解码容错两种字母表与空白，失败给出统一提示；
 *               计算在 Dispatchers.Default，请求带版本号丢弃过期结果
 */
package com.wangxingxing.clipbox.ui.toolbox.base64

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
import com.wangxingxing.clipbox.resources.tool_base64
import com.wangxingxing.clipbox.resources.tool_base64_error
import com.wangxingxing.clipbox.resources.tool_decode
import com.wangxingxing.clipbox.resources.tool_encode
import com.wangxingxing.clipbox.resources.tool_url_safe
import com.wangxingxing.clipbox.toolbox.Base64Result
import com.wangxingxing.clipbox.toolbox.Base64Tool
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

private sealed interface Base64OutputState {
    data object None : Base64OutputState
    data class Text(val value: String) : Base64OutputState
    data object Invalid : Base64OutputState
}

@Composable
fun Base64ToolRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var input by rememberSaveable { mutableStateOf("") }
    var urlSafe by rememberSaveable { mutableStateOf(false) }
    var outputState by remember { mutableStateOf<Base64OutputState>(Base64OutputState.None) }
    var requestVersion by remember { mutableIntStateOf(0) }
    val overLimit = rememberInputByteCount(input) > ToolInputLimit.MAX_BYTES
    val scope = rememberCoroutineScope()
    val spacing = ClipboxTokens.spacing

    fun run(encode: Boolean) {
        val text = input
        val safe = urlSafe
        val version = ++requestVersion
        scope.launch {
            val next = withContext(Dispatchers.Default) {
                if (encode) {
                    Base64OutputState.Text(Base64Tool.encode(text, urlSafe = safe))
                } else {
                    when (val result = Base64Tool.decode(text)) {
                        is Base64Result.Success -> Base64OutputState.Text(result.text)
                        Base64Result.Invalid -> Base64OutputState.Invalid
                    }
                }
            }
            if (version == requestVersion) outputState = next
        }
    }

    val outputText = when (val current = outputState) {
        Base64OutputState.None -> ""
        is Base64OutputState.Text -> current.value
        Base64OutputState.Invalid -> stringResource(Res.string.tool_base64_error)
    }

    ToolScaffold(
        title = stringResource(Res.string.tool_base64),
        onBack = onBack,
        input = input,
        onInputChange = {
            input = it
            outputState = Base64OutputState.None
        },
        output = if (overLimit) "" else outputText,
        onClear = { outputState = Base64OutputState.None },
        modifier = modifier,
        outputError = outputState is Base64OutputState.Invalid,
        outputStatus = if (outputState is Base64OutputState.Invalid) {
            stringResource(Res.string.history_state_failed)
        } else {
            null
        },
        options = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = spacing.m),
            ) {
                ToolFlag(
                    text = stringResource(Res.string.tool_url_safe),
                    selected = urlSafe,
                    onClick = { urlSafe = !urlSafe },
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
