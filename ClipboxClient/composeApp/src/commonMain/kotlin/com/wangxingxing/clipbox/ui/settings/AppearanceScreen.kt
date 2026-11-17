/**
 * author : 王星星
 * date : 2026-11-17 15:42
 * email : 1099420259@qq.com
 * description : 外观与语言 S13：主题与语言三选一分段立即写入设置；语言切换只重载资源，不重建导航或丢失输入
 */
package com.wangxingxing.clipbox.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.wangxingxing.clipbox.resources.Res
import com.wangxingxing.clipbox.resources.appearance_back
import com.wangxingxing.clipbox.resources.appearance_current
import com.wangxingxing.clipbox.resources.appearance_kicker
import com.wangxingxing.clipbox.resources.appearance_note
import com.wangxingxing.clipbox.resources.appearance_preview_body
import com.wangxingxing.clipbox.resources.appearance_preview_device
import com.wangxingxing.clipbox.resources.appearance_preview_label
import com.wangxingxing.clipbox.resources.appearance_preview_time
import com.wangxingxing.clipbox.resources.appearance_section_language
import com.wangxingxing.clipbox.resources.appearance_section_theme
import com.wangxingxing.clipbox.resources.appearance_title
import com.wangxingxing.clipbox.resources.settings_language_en
import com.wangxingxing.clipbox.resources.settings_language_hans
import com.wangxingxing.clipbox.resources.settings_language_tw
import com.wangxingxing.clipbox.resources.settings_theme_dark
import com.wangxingxing.clipbox.resources.settings_theme_light
import com.wangxingxing.clipbox.resources.settings_theme_system
import com.wangxingxing.clipbox.settings.AppLanguage
import com.wangxingxing.clipbox.settings.ThemeMode
import com.wangxingxing.clipbox.ui.navigation.NavIcons
import com.wangxingxing.clipbox.ui.theme.ClipboxTokens
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AppearanceRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SettingsViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    val colors = ClipboxTokens.colors
    val spacing = ClipboxTokens.spacing
    // 选项文案全部走资源，切换语言后分段控件同步更新
    val themeOptions = listOf(
        ThemeMode.SYSTEM to stringResource(Res.string.settings_theme_system),
        ThemeMode.LIGHT to stringResource(Res.string.settings_theme_light),
        ThemeMode.DARK to stringResource(Res.string.settings_theme_dark),
    )
    val languageOptions = listOf(
        AppLanguage.ZH_HANS to stringResource(Res.string.settings_language_hans),
        AppLanguage.ZH_TW to stringResource(Res.string.settings_language_tw),
        AppLanguage.EN to stringResource(Res.string.settings_language_en),
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = spacing.xs, top = spacing.s, end = spacing.l, bottom = spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = NavIcons.Back,
                    contentDescription = stringResource(Res.string.appearance_back),
                    tint = colors.ink2,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(spacing.s))
            Column {
                Text(
                    text = stringResource(Res.string.appearance_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.ink,
                )
                Text(
                    text = stringResource(Res.string.appearance_kicker),
                    style = ClipboxTokens.typography.mono,
                    color = colors.ink3,
                )
            }
        }
        HorizontalDivider(thickness = 1.dp, color = colors.ink)

        AppearanceSectionLabel(stringResource(Res.string.appearance_section_theme))
        AppearanceSegment(
            options = themeOptions,
            selected = state.themeMode,
            onSelect = viewModel::setThemeMode,
        )
        AppearancePreviewCard(
            deviceName = state.deviceName.ifBlank { stringResource(Res.string.appearance_preview_device) },
        )
        AppearanceSectionLabel(stringResource(Res.string.appearance_section_language))
        AppearanceSegment(
            options = languageOptions,
            selected = state.language,
            onSelect = viewModel::setLanguage,
        )
        Text(
            text = stringResource(Res.string.appearance_note),
            modifier = Modifier.padding(
                start = spacing.l,
                top = spacing.xl,
                end = spacing.l,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = colors.ink3,
        )
    }
}

@Composable
private fun <T> AppearanceSegment(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    val colors = ClipboxTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ClipboxTokens.spacing.l)
            .height(48.dp)
            .clip(RoundedCornerShape(3.dp))
            .border(1.dp, colors.line, RoundedCornerShape(3.dp)),
    ) {
        options.forEachIndexed { index, (value, label) ->
            if (index > 0) {
                Box(
                    Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(colors.line)
                )
            }
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (isSelected) colors.ink else colors.panel)
                    .selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        onClick = { onSelect(value) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) colors.background else colors.ink2,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun AppearancePreviewCard(deviceName: String) {
    val colors = ClipboxTokens.colors
    val spacing = ClipboxTokens.spacing
    val mono = ClipboxTokens.typography.mono
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = spacing.l, top = spacing.m, end = spacing.l)
            .border(1.dp, colors.line)
            .background(colors.panel)
            .padding(spacing.m),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(Res.string.appearance_preview_label),
                style = mono,
                color = colors.ink3,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(Res.string.appearance_current),
                modifier = Modifier
                    .border(1.dp, colors.accent, RoundedCornerShape(2.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
                style = mono,
                color = colors.accent,
            )
        }
        Text(
            text = stringResource(Res.string.appearance_preview_body),
            modifier = Modifier.padding(top = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.ink,
        )
        HorizontalDivider(
            modifier = Modifier.padding(top = 10.dp),
            thickness = 1.dp,
            color = colors.line,
        )
        Row(modifier = Modifier.padding(top = spacing.s)) {
            Text(text = deviceName, style = mono, color = colors.ink3)
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(Res.string.appearance_preview_time),
                style = mono,
                color = colors.ink3,
            )
        }
    }
}

@Composable
private fun AppearanceSectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(
            start = ClipboxTokens.spacing.l,
            top = ClipboxTokens.spacing.xl,
            end = ClipboxTokens.spacing.l,
            bottom = ClipboxTokens.spacing.s,
        ),
        style = ClipboxTokens.typography.mono,
        color = ClipboxTokens.colors.ink3,
    )
}
