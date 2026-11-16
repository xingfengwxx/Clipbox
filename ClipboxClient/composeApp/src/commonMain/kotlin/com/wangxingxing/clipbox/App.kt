/**
 * author : 王星星
 * date : 2026-11-16 11:17
 * email : 1099420259@qq.com
 * description : 三端共享的根 Composable，装配临时依赖图、主题与一级导航；始终以历史页为起点，未配对由历史空态进入配对页
 */
package com.wangxingxing.clipbox

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.compose.rememberNavController
import com.wangxingxing.clipbox.di.LocalAppGraph
import com.wangxingxing.clipbox.di.rememberAppGraph
import com.wangxingxing.clipbox.ui.navigation.ClipboxNavigationHost
import com.wangxingxing.clipbox.ui.theme.ClipboxTheme

@Composable
fun App() {
    val appGraph = rememberAppGraph()
    ClipboxTheme {
        CompositionLocalProvider(LocalAppGraph provides appGraph) {
            ClipboxNavigationHost(navController = rememberNavController())
        }
    }
}
