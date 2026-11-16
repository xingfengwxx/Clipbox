/**
 * author : 王星星
 * date : 2026-11-16 11:17
 * email : 1099420259@qq.com
 * description : 三端共享的根 Composable，装配主题与一级导航；页面内容由 NavHost 按路由注入
 */
package com.wangxingxing.clipbox

import androidx.compose.runtime.Composable
import androidx.navigation.compose.rememberNavController
import com.wangxingxing.clipbox.ui.navigation.ClipboxNavigationHost
import com.wangxingxing.clipbox.ui.theme.ClipboxTheme

@Composable
fun App() {
    ClipboxTheme {
        ClipboxNavigationHost(navController = rememberNavController())
    }
}
