/**
 * author : 王星星
 * date : 2026-11-17 15:46
 * email : 1099420259@qq.com
 * description : Android 系统栏外观实现：按根主题的深浅色切换状态栏与导航栏图标明暗，保证图标在 App 背景上的对比度
 *               注意：系统栏本身保持透明（edge-to-edge），只改图标外观，不改系统主题
 */
package com.wangxingxing.clipbox.ui.theme

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
actual fun ApplySystemBarsAppearance(darkTheme: Boolean) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                // true 表示用深色图标，因此深色主题下取反
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
}
