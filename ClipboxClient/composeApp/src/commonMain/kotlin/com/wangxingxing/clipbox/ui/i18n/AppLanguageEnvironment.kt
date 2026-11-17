/**
 * author : 王星星
 * date : 2026-11-17 15:56
 * email : 1099420259@qq.com
 * description : 把应用语言映射成 Compose Resources 的 ResourceEnvironment；只影响资源查找，不修改系统 Locale。
 *               注意：简体中文复用默认 values 目录，因此 region 传空串；Compose Resources 会先按语言筛选，再回落到无区域条目。
 *               Compose Resources 1.9.3 把自定义环境标为库内部 API，依赖版本已锁定，故按内部 API 使用并抑制可见性检查
 */
@file:OptIn(InternalResourceApi::class)
@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package com.wangxingxing.clipbox.ui.i18n

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.wangxingxing.clipbox.settings.AppLanguage
import org.jetbrains.compose.resources.ComposeEnvironment
import org.jetbrains.compose.resources.DensityQualifier
import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.LanguageQualifier
import org.jetbrains.compose.resources.LocalComposeEnvironment
import org.jetbrains.compose.resources.RegionQualifier
import org.jetbrains.compose.resources.ResourceEnvironment
import org.jetbrains.compose.resources.ThemeQualifier

val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.ZH_HANS }

@Composable
fun ProvideAppLanguage(
    language: AppLanguage,
    content: @Composable () -> Unit,
) {
    // 资源里的 -dark 限定符必须跟随应用内主题，而不是系统外观；根主题已把最终背景色注入 MaterialTheme
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val density = LocalDensity.current
    val environment = remember(language, darkTheme, density.density) {
        FixedComposeEnvironment(
            ResourceEnvironment(
                language = LanguageQualifier(language.resourceLanguage),
                region = RegionQualifier(language.resourceRegion),
                theme = if (darkTheme) ThemeQualifier.DARK else ThemeQualifier.LIGHT,
                density = density.toResourceDensity(),
            ),
        )
    }
    CompositionLocalProvider(
        LocalComposeEnvironment provides environment,
        LocalAppLanguage provides language,
        content = content,
    )
}

private class FixedComposeEnvironment(
    private val environment: ResourceEnvironment,
) : ComposeEnvironment {
    @Composable
    override fun rememberEnvironment(): ResourceEnvironment = environment
}

private val AppLanguage.resourceLanguage: String
    get() = when (this) {
        AppLanguage.ZH_HANS, AppLanguage.ZH_TW -> "zh"
        AppLanguage.EN -> "en"
    }

private val AppLanguage.resourceRegion: String
    get() = when (this) {
        AppLanguage.ZH_HANS -> ""
        AppLanguage.ZH_TW -> "TW"
        AppLanguage.EN -> ""
    }

private fun Density.toResourceDensity(): DensityQualifier = when {
    density <= 0.75f -> DensityQualifier.LDPI
    density <= 1.0f -> DensityQualifier.MDPI
    density <= 1.5f -> DensityQualifier.HDPI
    density <= 2.0f -> DensityQualifier.XHDPI
    density <= 3.0f -> DensityQualifier.XXHDPI
    else -> DensityQualifier.XXXHDPI
}
