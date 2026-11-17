/**
 * author : 王星星
 * date : 2026-11-17 15:46
 * email : 1099420259@qq.com
 * description : 系统栏外观钩子：根主题解析出深浅色后交给平台实现，Android 据此切换状态栏/导航栏图标明暗，其余平台无系统栏可调
 */
package com.wangxingxing.clipbox.ui.theme

import androidx.compose.runtime.Composable

@Composable
expect fun ApplySystemBarsAppearance(darkTheme: Boolean)
