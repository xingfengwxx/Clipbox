/**
 * author : 王星星
 * date : 2026-11-16 11:17
 * email : 1099420259@qq.com
 * description : 三端共享的根 Composable，装配临时依赖图、主题与一级导航；未配对以配对页为起点，配对完成后重建导航图进入历史页
 */
package com.wangxingxing.clipbox

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.wangxingxing.clipbox.di.LocalAppGraph
import com.wangxingxing.clipbox.di.rememberAppGraph
import com.wangxingxing.clipbox.ui.navigation.ClipboxNavigationHost
import com.wangxingxing.clipbox.ui.navigation.PAIRING_ROUTE
import com.wangxingxing.clipbox.ui.navigation.TopLevelDestination
import com.wangxingxing.clipbox.ui.theme.ClipboxTheme
import com.wangxingxing.clipbox.ui.theme.ClipboxTokens

@Composable
fun App() {
    val appGraph = rememberAppGraph()
    ClipboxTheme {
        CompositionLocalProvider(LocalAppGraph provides appGraph) {
            var paired by remember { mutableStateOf<Boolean?>(null) }
            LaunchedEffect(appGraph) {
                paired = !appGraph.settingsRepository.current().deviceToken.isNullOrEmpty()
            }
            val colors = ClipboxTokens.colors
            when (paired) {
                // 读取配对状态前只铺背景色，避免先闪一帧历史页
                null -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.background),
                )

                else -> {
                    val startDestination = if (paired == true) {
                        TopLevelDestination.History.route
                    } else {
                        PAIRING_ROUTE
                    }
                    // 配对成功后 key 变化会重建 NavHost，让历史页成为新图起点，未配对路由不残留在返回栈
                    key(startDestination) {
                        ClipboxNavigationHost(
                            navController = rememberNavController(),
                            startDestination = startDestination,
                            onPaired = { paired = true },
                        )
                    }
                }
            }
        }
    }
}
