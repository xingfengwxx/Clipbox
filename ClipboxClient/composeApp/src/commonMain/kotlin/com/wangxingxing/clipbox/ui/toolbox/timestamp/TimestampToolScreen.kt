/**
 * author : 王星星
 * date : 2026-11-17 16:29
 * email : 1099420259@qq.com
 * description : 时间戳工具页：自动/秒/毫秒切换、填入当前时间、时间戳与日期字符串双向转换；
 *               输入变化即转换，计算在 Dispatchers.Default，请求带版本号丢弃过期结果；
 *               时区仅在共享层使用，本页只注入系统时区
 */
package com.wangxingxing.clipbox.ui.toolbox.timestamp

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
import com.wangxingxing.clipbox.resources.tool_auto
import com.wangxingxing.clipbox.resources.tool_iso
import com.wangxingxing.clipbox.resources.tool_local
import com.wangxingxing.clipbox.resources.tool_millis
import com.wangxingxing.clipbox.resources.tool_now
import com.wangxingxing.clipbox.resources.tool_seconds
import com.wangxingxing.clipbox.resources.tool_time
import com.wangxingxing.clipbox.resources.tool_time_error
import com.wangxingxing.clipbox.resources.tool_time_range
import com.wangxingxing.clipbox.resources.tool_utc
import com.wangxingxing.clipbox.toolbox.TimestampResult
import com.wangxingxing.clipbox.toolbox.TimestampTool
import com.wangxingxing.clipbox.toolbox.TimestampUnit
import com.wangxingxing.clipbox.ui.theme.ClipboxTokens
import com.wangxingxing.clipbox.ui.toolbox.ToolFlag
import com.wangxingxing.clipbox.ui.toolbox.ToolInputLimit
import com.wangxingxing.clipbox.ui.toolbox.ToolScaffold
import com.wangxingxing.clipbox.ui.toolbox.rememberInputByteCount
import com.wangxingxing.clipbox.util.clipNowMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

@Composable
fun TimestampToolRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var input by rememberSaveable { mutableStateOf("") }
    var unitIndex by rememberSaveable { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<TimestampResult?>(null) }
    var requestVersion by remember { mutableIntStateOf(0) }
    val overLimit = rememberInputByteCount(input) > ToolInputLimit.MAX_BYTES
    val scope = rememberCoroutineScope()
    val spacing = ClipboxTokens.spacing

    fun convert(text: String) {
        val version = ++requestVersion
        if (text.isBlank()) {
            result = null
            return
        }
        val unit = when (unitIndex) {
            1 -> TimestampUnit.Seconds
            2 -> TimestampUnit.Millis
            else -> TimestampUnit.Auto
        }
        scope.launch {
            val next = withContext(Dispatchers.Default) {
                TimestampTool.convert(text, unit)
            }
            if (version == requestVersion) result = next
        }
    }

    val localLabel = stringResource(Res.string.tool_local)
    val utcLabel = stringResource(Res.string.tool_utc)
    val isoLabel = stringResource(Res.string.tool_iso)
    val secondsLabel = stringResource(Res.string.tool_seconds)
    val millisLabel = stringResource(Res.string.tool_millis)
    val outputText = when (val current = result) {
        null -> ""
        is TimestampResult.DateTime -> listOf(
            "$localLabel  ${current.local}",
            "$utcLabel  ${current.utc}",
            "$isoLabel  ${current.iso}",
        ).joinToString("\n")

        is TimestampResult.Epoch -> listOf(
            "$secondsLabel  ${current.seconds}",
            "$millisLabel  ${current.millis}",
        ).joinToString("\n")

        is TimestampResult.Invalid -> if (current.outOfRange) {
            stringResource(Res.string.tool_time_range)
        } else {
            stringResource(Res.string.tool_time_error)
        }
    }

    ToolScaffold(
        title = stringResource(Res.string.tool_time),
        onBack = onBack,
        input = input,
        onInputChange = {
            input = it
            convert(it)
        },
        output = if (overLimit) "" else outputText,
        onClear = {
            requestVersion++
            result = null
        },
        modifier = modifier,
        outputError = result is TimestampResult.Invalid,
        outputStatus = if (result is TimestampResult.Invalid) {
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
                    text = stringResource(Res.string.tool_auto),
                    selected = unitIndex == 0,
                    onClick = {
                        unitIndex = 0
                        convert(input)
                    },
                )
                ToolFlag(
                    text = stringResource(Res.string.tool_seconds),
                    selected = unitIndex == 1,
                    onClick = {
                        unitIndex = 1
                        convert(input)
                    },
                )
                ToolFlag(
                    text = stringResource(Res.string.tool_millis),
                    selected = unitIndex == 2,
                    onClick = {
                        unitIndex = 2
                        convert(input)
                    },
                )
                ToolFlag(
                    text = stringResource(Res.string.tool_now),
                    selected = false,
                    onClick = {
                        val nowMillis = clipNowMillis()
                        val text = if (unitIndex == 2) {
                            nowMillis.toString()
                        } else {
                            (nowMillis / 1000).toString()
                        }
                        input = text
                        convert(text)
                    },
                )
            }
        },
    )
}
