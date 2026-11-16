/**
 * author : 王星星
 * date : 2026-11-16 16:29
 * email : 1099420259@qq.com
 * description : 应用级 NavHost：三个一级目的地加配对页；配对页全屏展示，成功后由上层重建导航图回到历史页
 */
package com.wangxingxing.clipbox.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.wangxingxing.clipbox.ui.pairing.PairingRoute

const val PAIRING_ROUTE = "pairing"

@Composable
fun ClipboxNavHost(
    navController: NavHostController,
    startDestination: String = TopLevelDestination.History.route,
    onPaired: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(PAIRING_ROUTE) {
            PairingRoute(onPaired = onPaired)
        }
        TopLevelDestination.entries.forEach { destination ->
            composable(destination.route) {
                PlaceholderPage(destination)
            }
        }
    }
}
