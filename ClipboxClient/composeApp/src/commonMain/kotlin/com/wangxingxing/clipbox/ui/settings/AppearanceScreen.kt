/**
 * author : 王星星
 * date : 2026-11-17 15:42
 * email : 1099420259@qq.com
 * description : 外观与语言 S13 入口页；本卡只展示当前主题与语言并保留返回路径，三选一控件由 T-UI-09 / T-UI-10 填充
 */
package com.wangxingxing.clipbox.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wangxingxing.clipbox.resources.Res
import com.wangxingxing.clipbox.resources.appearance_back
import com.wangxingxing.clipbox.resources.appearance_current
import com.wangxingxing.clipbox.resources.appearance_kicker
import com.wangxingxing.clipbox.resources.appearance_note
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
    val themeLabel = stringResource(
        when (state.themeMode) {
            ThemeMode.SYSTEM -> Res.string.settings_theme_system
            ThemeMode.LIGHT -> Res.string.settings_theme_light
            ThemeMode.DARK -> Res.string.settings_theme_dark
        },
    )
    val languageLabel = when (state.language) {
        AppLanguage.ZH_HANS -> stringResource(Res.string.settings_language_hans)
        AppLanguage.ZH_TW -> stringResource(Res.string.settings_language_tw)
        AppLanguage.EN -> stringResource(Res.string.settings_language_en)
    }

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
        AppearanceValueRow(
            label = stringResource(Res.string.appearance_current),
            value = themeLabel,
        )
        AppearanceSectionLabel(stringResource(Res.string.appearance_section_language))
        AppearanceValueRow(
            label = stringResource(Res.string.appearance_current),
            value = languageLabel,
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

@Composable
private fun AppearanceValueRow(label: String, value: String) {
    val colors = ClipboxTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = ClipboxTokens.spacing.l, vertical = ClipboxTokens.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.ink,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = colors.ink2,
        )
    }
}
