/**
 * author : 王星星
 * date : 2026-11-16 16:29
 * email : 1099420259@qq.com
 * description : 应用级 NavHost：历史页接真实实现，工具箱与设置暂为占位；配对页全屏入栈，配对成功回退到历史页
 */
package com.wangxingxing.clipbox.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.wangxingxing.clipbox.ui.history.HistoryRoute
import com.wangxingxing.clipbox.ui.pairing.PairingRoute

const val PAIRING_ROUTE = "pairing"

@Composable
fun ClipboxNavHost(
    navController: NavHostController,
    startDestination: String = TopLevelDestination.History.route,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(PAIRING_ROUTE) {
            PairingRoute(onPaired = { navController.popBackStack() })
        }
        composable(TopLevelDestination.History.route) {
            HistoryRoute(
                onOpenSettings = { navController.switchTo(TopLevelDestination.Settings) },
                onPair = {
                    navController.navigate(PAIRING_ROUTE) {
                        launchSingleTop = true
                    }
                },
            )
        }
        TopLevelDestination.entries
            .filterNot { it == TopLevelDestination.History }
            .forEach { destination ->
                composable(destination.route) {
                    PlaceholderPage(destination)
                }
            }
    }
}
