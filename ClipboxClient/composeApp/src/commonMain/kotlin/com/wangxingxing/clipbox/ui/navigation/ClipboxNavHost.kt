/**
 * author : 王星星
 * date : 2026-11-16 16:29
 * email : 1099420259@qq.com
 * description : 应用级 NavHost：历史页与设置页接真实实现，工具箱暂为占位；配对页与外观页全屏入栈；分享草稿参数只透传给历史页
 */
package com.wangxingxing.clipbox.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.wangxingxing.clipbox.ui.history.HistoryRoute
import com.wangxingxing.clipbox.ui.pairing.PairingRoute
import com.wangxingxing.clipbox.ui.settings.AppearanceRoute
import com.wangxingxing.clipbox.ui.settings.SettingsRoute

const val PAIRING_ROUTE = "pairing"
const val APPEARANCE_ROUTE = "settings/appearance"

@Composable
fun ClipboxNavHost(
    navController: NavHostController,
    startDestination: String = TopLevelDestination.History.route,
    modifier: Modifier = Modifier,
    incomingShareDraft: String? = null,
    onShareDraftConsumed: () -> Unit = {},
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(PAIRING_ROUTE) {
            PairingRoute()
        }
        composable(TopLevelDestination.History.route) {
            HistoryRoute(
                onOpenSettings = { navController.switchTo(TopLevelDestination.Settings) },
                onPair = {
                    navController.navigate(PAIRING_ROUTE) {
                        launchSingleTop = true
                    }
                },
                incomingShareDraft = incomingShareDraft,
                onShareDraftConsumed = onShareDraftConsumed,
            )
        }
        composable(TopLevelDestination.Settings.route) {
            SettingsRoute(
                onOpenAppearance = {
                    navController.navigate(APPEARANCE_ROUTE) {
                        launchSingleTop = true
                    }
                },
                onPair = {
                    navController.navigate(PAIRING_ROUTE) {
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(APPEARANCE_ROUTE) {
            AppearanceRoute(onBack = { navController.popBackStack() })
        }
        TopLevelDestination.entries
            .filterNot { it == TopLevelDestination.History || it == TopLevelDestination.Settings }
            .forEach { destination ->
                composable(destination.route) {
                    PlaceholderPage(destination)
                }
            }
    }
}
