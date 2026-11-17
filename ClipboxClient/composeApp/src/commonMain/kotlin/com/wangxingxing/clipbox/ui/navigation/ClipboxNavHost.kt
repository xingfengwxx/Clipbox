/**
 * author : 王星星
 * date : 2026-11-16 16:29
 * email : 1099420259@qq.com
 * description : 应用级 NavHost：历史、工具箱（含工具详情）、设置页接真实实现；配对页、外观页与工具详情全屏入栈；分享草稿参数只透传给历史页
 */
package com.wangxingxing.clipbox.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.wangxingxing.clipbox.ui.history.HistoryRoute
import com.wangxingxing.clipbox.ui.pairing.PairingRoute
import com.wangxingxing.clipbox.ui.settings.AppearanceRoute
import com.wangxingxing.clipbox.ui.settings.SettingsRoute
import com.wangxingxing.clipbox.ui.toolbox.ToolDetailRoute
import com.wangxingxing.clipbox.ui.toolbox.ToolId
import com.wangxingxing.clipbox.ui.toolbox.ToolboxRoute

const val PAIRING_ROUTE = "pairing"
const val APPEARANCE_ROUTE = "settings/appearance"
const val TOOL_DETAIL_PREFIX = "toolbox/"
const val TOOL_DETAIL_ROUTE = "toolbox/{toolId}"

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
        composable(TopLevelDestination.Toolbox.route) {
            ToolboxRoute(
                onOpenTool = { tool ->
                    navController.navigate(TOOL_DETAIL_PREFIX + tool.routeSegment) {
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(
            route = TOOL_DETAIL_ROUTE,
            arguments = listOf(navArgument("toolId") { type = NavType.StringType }),
        ) { entry ->
            val tool = ToolId.fromSegment(entry.arguments?.getString("toolId"))
            if (tool == null) {
                // 未知工具 id 不渲染空页，直接退回工具箱
                navController.popBackStack()
            } else {
                ToolDetailRoute(toolId = tool, onBack = { navController.popBackStack() })
            }
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
    }
}
