/**
 * author : 王星星
 * date : 2026-11-16 17:20
 * email : 1099420259@qq.com
 * description : 配对页 S10 / S11A / S11B：未加密警示、6 位码与设备名输入、错误分类提示与 429 冷却；
 *               配对码留空时自动完成空库初始化；服务器地址只来自构建配置
 */
package com.wangxingxing.clipbox.ui.pairing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wangxingxing.clipbox.app.AppContainer
import com.wangxingxing.clipbox.repository.PAIRING_CODE_LENGTH
import com.wangxingxing.clipbox.repository.PairingError
import com.wangxingxing.clipbox.resources.Res
import com.wangxingxing.clipbox.resources.pair_code_label
import com.wangxingxing.clipbox.resources.pair_cooldown
import com.wangxingxing.clipbox.resources.pair_description
import com.wangxingxing.clipbox.resources.pair_device_label
import com.wangxingxing.clipbox.resources.pair_error_already_initialized
import com.wangxingxing.clipbox.resources.pair_error_code_expired
import com.wangxingxing.clipbox.resources.pair_error_code_used
import com.wangxingxing.clipbox.resources.pair_error_device_name
import com.wangxingxing.clipbox.resources.pair_error_invalid_code
import com.wangxingxing.clipbox.resources.pair_error_network
import com.wangxingxing.clipbox.resources.pair_error_rate_limited
import com.wangxingxing.clipbox.resources.pair_error_server
import com.wangxingxing.clipbox.resources.pair_error_server_url
import com.wangxingxing.clipbox.resources.pair_expired_hint
import com.wangxingxing.clipbox.resources.pair_insecure_warning
import com.wangxingxing.clipbox.resources.pair_kicker
import com.wangxingxing.clipbox.resources.pair_note
import com.wangxingxing.clipbox.resources.pair_rate_hint
import com.wangxingxing.clipbox.resources.pair_server_label
import com.wangxingxing.clipbox.resources.pair_submit
import com.wangxingxing.clipbox.resources.pair_submitting
import com.wangxingxing.clipbox.resources.pair_title
import com.wangxingxing.clipbox.ui.theme.ClipboxTokens
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PairingRoute(modifier: Modifier = Modifier) {
    val viewModel: PairingViewModel = koinViewModel()
    val container = koinInject<AppContainer>()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel, container) {
        // 配对成功后设置里已有新令牌与地址，必须重建会话；导航随会话版本变化重置到历史页
        viewModel.pairedEvents.collect { container.refreshSession() }
    }

    PairingScreen(
        state = state,
        onServerUrlChange = viewModel::onServerUrlChange,
        onCodeChange = viewModel::onCodeChange,
        onDeviceNameChange = viewModel::onDeviceNameChange,
        onSubmit = viewModel::submit,
        modifier = modifier,
    )
}

@Composable
private fun PairingScreen(
    state: PairingUiState,
    onServerUrlChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onDeviceNameChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ClipboxTokens.colors
    val spacing = ClipboxTokens.spacing
    val radii = ClipboxTokens.radii
    val typography = ClipboxTokens.typography

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        if (state.insecure) {
            InsecureBanner()
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = spacing.xl, vertical = spacing.xl),
            ) {
                Text(
                    text = stringResource(Res.string.pair_kicker),
                    style = typography.mono,
                    color = colors.ink2,
                )
                Spacer(Modifier.height(spacing.xs))
                Text(
                    text = stringResource(Res.string.pair_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.ink,
                )
                Spacer(Modifier.height(spacing.s))
                Text(
                    text = stringResource(Res.string.pair_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.ink2,
                )

                Spacer(Modifier.height(spacing.xl))
                FieldLabel(stringResource(Res.string.pair_server_label))
                Spacer(Modifier.height(6.dp))
                PairingTextField(
                    value = state.serverUrl,
                    onValueChange = onServerUrlChange,
                    placeholder = "",
                    keyboardType = KeyboardType.Uri,
                    textStyle = typography.mono.copy(fontSize = 14.sp, color = colors.ink),
                )

                Spacer(Modifier.height(spacing.l))
                FieldLabel(stringResource(Res.string.pair_code_label))
                Spacer(Modifier.height(6.dp))
                PairingCodeField(
                    code = state.code,
                    onCodeChange = onCodeChange,
                )

                Spacer(Modifier.height(spacing.l))
                FieldLabel(stringResource(Res.string.pair_device_label))
                Spacer(Modifier.height(6.dp))
                PairingTextField(
                    value = state.deviceName,
                    onValueChange = onDeviceNameChange,
                    placeholder = "",
                    keyboardType = KeyboardType.Text,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.ink),
                )

                if (state.error != null) {
                    Spacer(Modifier.height(spacing.l))
                    ErrorPanel(state.error)
                }

                Spacer(Modifier.height(spacing.xl))
                PairingSubmitButton(state = state, onSubmit = onSubmit)

                if (state.error is PairingError.RateLimited) {
                    Spacer(Modifier.height(spacing.m))
                    Text(
                        text = stringResource(Res.string.pair_rate_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.ink2,
                    )
                }

                Spacer(Modifier.height(spacing.l))
                Text(
                    text = stringResource(Res.string.pair_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.ink3,
                )
                Spacer(Modifier.height(spacing.xxl))
            }
        }
    }
}

@Composable
private fun InsecureBanner() {
    val colors = ClipboxTokens.colors
    val spacing = ClipboxTokens.spacing
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.panel2),
    ) {
        Text(
            text = stringResource(Res.string.pair_insecure_warning),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.l, vertical = spacing.m),
            style = MaterialTheme.typography.labelLarge,
            color = colors.warn,
        )
        HorizontalDivider(thickness = 1.dp, color = colors.line)
    }
}

@Composable
private fun FieldLabel(text: String) {
    val colors = ClipboxTokens.colors
    Text(
        text = text,
        style = ClipboxTokens.typography.mono,
        color = colors.ink2,
    )
}

@Composable
private fun PairingCodeField(
    code: String,
    onCodeChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ClipboxTokens.colors
    val spacing = ClipboxTokens.spacing
    val shape = RoundedCornerShape(ClipboxTokens.radii.button)
    var focused by remember { mutableStateOf(false) }

    BasicTextField(
        value = code,
        onValueChange = onCodeChange,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused },
        // 字符由下方方格绘制，输入框本体只负责聚焦、键盘与数字过滤后的取值
        textStyle = TextStyle(color = Color.Transparent),
        cursorBrush = SolidColor(Color.Transparent),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        decorationBox = { innerTextField ->
            Box {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.s),
                ) {
                    repeat(PAIRING_CODE_LENGTH) { index ->
                        val char = code.getOrNull(index)
                        val active = focused && index == code.length.coerceAtMost(PAIRING_CODE_LENGTH - 1)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .background(colors.panel, shape)
                                .border(1.dp, if (active) colors.accent else colors.line, shape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (char != null) {
                                Text(
                                    text = char.toString(),
                                    style = ClipboxTokens.typography.mono.copy(
                                        fontSize = 24.sp,
                                        lineHeight = 28.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                    color = colors.ink,
                                )
                            }
                        }
                    }
                }
                innerTextField()
            }
        },
    )
}

@Composable
private fun PairingTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val colors = ClipboxTokens.colors
    val shape = RoundedCornerShape(ClipboxTokens.radii.button)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .background(colors.panel, shape)
            .border(1.dp, colors.line, shape)
            .padding(horizontal = 13.dp, vertical = 12.dp),
        textStyle = textStyle,
        singleLine = true,
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(
                        text = placeholder,
                        style = textStyle.copy(color = colors.ink3),
                    )
                }
                innerTextField()
            }
        },
    )
}

@Composable
private fun ErrorPanel(error: PairingError) {
    val colors = ClipboxTokens.colors
    val spacing = ClipboxTokens.spacing
    val shape = RoundedCornerShape(ClipboxTokens.radii.button)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.danger, shape)
            .padding(horizontal = 13.dp, vertical = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(error.messageResource()),
                style = MaterialTheme.typography.bodySmall,
                color = colors.danger,
            )
            Text(
                text = "!",
                style = ClipboxTokens.typography.mono,
                color = colors.danger,
                modifier = Modifier.padding(start = spacing.xs),
            )
        }
        if (error == PairingError.CodeExpired) {
            Spacer(Modifier.height(spacing.xs))
            Text(
                text = stringResource(Res.string.pair_expired_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.danger,
            )
        }
    }
}

@Composable
private fun PairingSubmitButton(
    state: PairingUiState,
    onSubmit: () -> Unit,
) {
    val colors = ClipboxTokens.colors
    val label = when {
        state.isSubmitting -> stringResource(Res.string.pair_submitting)
        state.cooldownSeconds > 0 -> pluralStringResource(
            Res.plurals.pair_cooldown,
            state.cooldownSeconds,
            state.cooldownSeconds,
        )

        else -> stringResource(Res.string.pair_submit)
    }
    Button(
        onClick = onSubmit,
        enabled = state.canSubmit,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(ClipboxTokens.radii.button),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.accent,
            contentColor = colors.onAccent,
            disabledContainerColor = colors.panel2,
            disabledContentColor = colors.ink2,
        ),
        contentPadding = PaddingValues(horizontal = ClipboxTokens.spacing.l),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

// 错误码到文案的唯一映射点，界面不自行拼装服务端 message
private fun PairingError.messageResource(): StringResource = when (this) {
    PairingError.InvalidCode -> Res.string.pair_error_invalid_code
    PairingError.CodeExpired -> Res.string.pair_error_code_expired
    PairingError.CodeUsed -> Res.string.pair_error_code_used
    PairingError.DeviceName -> Res.string.pair_error_device_name
    PairingError.Network -> Res.string.pair_error_network
    PairingError.ServerUrl -> Res.string.pair_error_server_url
    PairingError.Server -> Res.string.pair_error_server
    PairingError.AlreadyInitialized -> Res.string.pair_error_already_initialized
    is PairingError.RateLimited -> Res.string.pair_error_rate_limited
}
