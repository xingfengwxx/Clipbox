/**
 * author : 王星星
 * date : 2026-11-16 16:29
 * email : 1099420259@qq.com
 * description : 三端共享的设计 tokens：11 个色彩角色、间距、圆角与字体；十六进制色值只允许出现在本包，页面必须经 ClipboxTokens 取色
 */
package com.wangxingxing.clipbox.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
data class ClipboxColors(
    val background: Color,
    val panel: Color,
    val panel2: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val line: Color,
    val accent: Color,
    val ok: Color,
    val warn: Color,
    val danger: Color,
    /** accent 上的配套文字色，来自 brand-spec 的按钮规范，不属于设计稿的 11 个角色。 */
    val onAccent: Color,
)

val LightClipboxColors = ClipboxColors(
    background = Color(0xFFF3F0E8),
    panel = Color(0xFFFBF9F3),
    panel2 = Color(0xFFEAE6DA),
    ink = Color(0xFF191712),
    ink2 = Color(0xFF5B5446),
    ink3 = Color(0xFF6B6455),
    line = Color(0xFFD0CABA),
    accent = Color(0xFFA8430F),
    ok = Color(0xFF2F6B45),
    warn = Color(0xFF7A4E0C),
    danger = Color(0xFFA03024),
    onAccent = Color(0xFFFFF8F0),
)

val DarkClipboxColors = ClipboxColors(
    background = Color(0xFF17150F),
    panel = Color(0xFF1E1B14),
    panel2 = Color(0xFF262218),
    ink = Color(0xFFEDE9DC),
    ink2 = Color(0xFFB7B0A0),
    ink3 = Color(0xFF9A9384),
    line = Color(0xFF3A352B),
    accent = Color(0xFFE8823F),
    ok = Color(0xFF7ED6A4),
    warn = Color(0xFFE0B05A),
    danger = Color(0xFFF08A75),
    onAccent = Color(0xFF17150F),
)

// 未映射容器落回品牌面板色，避免 M3 组件（对话框 / Snackbar / 输入框）出现基线紫色
internal fun ClipboxColors.toColorScheme(dark: Boolean): ColorScheme {
    val scheme = if (dark) darkColorScheme() else lightColorScheme()
    return scheme.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = panel2,
        onPrimaryContainer = ink,
        inversePrimary = accent,
        secondary = ink2,
        onSecondary = panel,
        secondaryContainer = panel2,
        onSecondaryContainer = ink,
        tertiary = ink3,
        onTertiary = panel,
        tertiaryContainer = panel2,
        onTertiaryContainer = ink,
        background = background,
        onBackground = ink,
        surface = panel,
        onSurface = ink,
        surfaceVariant = panel2,
        onSurfaceVariant = ink2,
        surfaceTint = accent,
        inverseSurface = ink,
        inverseOnSurface = background,
        error = danger,
        onError = onAccent,
        errorContainer = panel2,
        onErrorContainer = danger,
        outline = line,
        outlineVariant = panel2,
        scrim = Color.Black,
        surfaceBright = panel,
        surfaceDim = background,
        surfaceContainerLowest = background,
        surfaceContainerLow = panel,
        surfaceContainer = panel,
        surfaceContainerHigh = panel2,
        surfaceContainerHighest = panel2,
    )
}

/** 间距刻度固定为 4 / 8 / 12 / 16 / 24 / 32。 */
@Immutable
data class ClipboxSpacing(
    val xs: Dp = 4.dp,
    val s: Dp = 8.dp,
    val m: Dp = 12.dp,
    val l: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
)

/** 按钮 3dp；弹层 6–10dp，统一取 8dp。 */
@Immutable
data class ClipboxRadii(
    val button: Dp = 3.dp,
    val overlay: Dp = 8.dp,
)

val ClipboxShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(3.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(10.dp),
    extraLarge = RoundedCornerShape(10.dp),
)

@Immutable
class ClipboxTypography(
    val material: Typography,
    /** 设备名 / 时间 / 字节数 / 同步状态的等宽签名样式。 */
    val mono: TextStyle,
)

val DefaultClipboxTypography = ClipboxTypography(
    material = Typography(
        titleLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            fontSize = 27.sp,
            lineHeight = 30.sp,
            letterSpacing = (-0.675).sp,
        ),
        titleMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp,
            lineHeight = 24.sp,
        ),
        bodyLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 25.sp,
        ),
        bodyMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 15.sp,
            lineHeight = 23.sp,
        ),
        bodySmall = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 22.sp,
        ),
        labelLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        ),
        labelMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        ),
        labelSmall = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        ),
    ),
    mono = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)

val LocalClipboxColors = staticCompositionLocalOf { LightClipboxColors }
val LocalClipboxSpacing = staticCompositionLocalOf { ClipboxSpacing() }
val LocalClipboxRadii = staticCompositionLocalOf { ClipboxRadii() }
val LocalClipboxTypography = staticCompositionLocalOf { DefaultClipboxTypography }

object ClipboxTokens {

    val colors: ClipboxColors
        @Composable @ReadOnlyComposable get() = LocalClipboxColors.current

    val spacing: ClipboxSpacing
        @Composable @ReadOnlyComposable get() = LocalClipboxSpacing.current

    val radii: ClipboxRadii
        @Composable @ReadOnlyComposable get() = LocalClipboxRadii.current

    val typography: ClipboxTypography
        @Composable @ReadOnlyComposable get() = LocalClipboxTypography.current
}
