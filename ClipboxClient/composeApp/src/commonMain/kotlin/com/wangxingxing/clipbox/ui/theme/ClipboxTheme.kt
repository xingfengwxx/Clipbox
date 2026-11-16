/**
 * author : 王星星
 * date : 2026-11-16 16:29
 * email : 1099420259@qq.com
 * description : 根主题：把浅 / 深两套 tokens 注入 Material 3 与 CompositionLocal；本卡只跟随系统外观，主题模式切换由 T-UI-09 接入
 */
package com.wangxingxing.clipbox.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember

@Composable
fun ClipboxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkClipboxColors else LightClipboxColors
    val colorScheme = remember(darkTheme) { colors.toColorScheme(darkTheme) }
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
