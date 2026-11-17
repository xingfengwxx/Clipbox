/**
 * author : 王星星
 * date : 2026-11-16 16:29
 * email : 1099420259@qq.com
 * description : 根主题：按主题模式计算深浅色，把两套 tokens 注入 Material 3 与 CompositionLocal。
 *               注意：SYSTEM 用 isSystemInDarkTheme 并在系统外观变化时重组；固定模式由设置驱动，不读系统值
 */
package com.wangxingxing.clipbox.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.wangxingxing.clipbox.settings.ThemeMode

@Composable
fun ClipboxTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (darkTheme) DarkClipboxColors else LightClipboxColors
    val colorScheme = remember(darkTheme) { colors.toColorScheme(darkTheme) }
    ApplySystemBarsAppearance(darkTheme)
    MaterialTheme(
        colorScheme = colorScheme,
        typography = DefaultClipboxTypography.material,
        shapes = ClipboxShapes,
    ) {
        CompositionLocalProvider(
            LocalClipboxColors provides colors,
            LocalClipboxSpacing provides ClipboxSpacing(),
            LocalClipboxRadii provides ClipboxRadii(),
            LocalClipboxTypography provides DefaultClipboxTypography,
            content = content,
        )
    }
}
