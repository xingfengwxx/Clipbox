/**
 * author : 王星星
 * date : 2026-11-16 16:29
 * email : 1099420259@qq.com
 * description : 应用级 NavHost，本卡只注册三个一级目的地；toolbox/{toolId} 与 pairing 等二级路由由后续卡片在此扩展
 */
package com.wangxingxing.clipbox.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

@Composable
fun ClipboxNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = TopLevelDestination.History.route,
        modifier = modifier,
    ) {
        TopLevelDestination.entries.forEach { destination ->
            composable(destination.route) {
                PlaceholderPage(destination)
            }
        }
    }
}
