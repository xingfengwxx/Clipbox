/**
 * author : 王星星
 * date : 2026-11-16 16:29
 * email : 1099420259@qq.com
 * description : 自适应导航壳：<600dp 用底部三栏，≥600dp 用 200dp 侧栏；切换一级页时保存 / 恢复各自返回栈状态；配对页全屏不显示导航
 */
package com.wangxingxing.clipbox.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.wangxingxing.clipbox.ui.theme.ClipboxTokens
import org.jetbrains.compose.resources.stringResource

// 架构 §7.1：<600dp 底部导航，≥600dp 左侧 NavigationRail
private val WideLayoutMinWidth = 600.dp

@Composable
fun ClipboxNavigationHost(
    navController: NavHostController,
    startDestination: String = TopLevelDestination.History.route,
    onPaired: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    // NavHost 首次组合时返回栈可能尚未就绪，按起始路由判断可避免配对页闪出底栏
    val isPairing = currentRoute == PAIRING_ROUTE ||
        (currentRoute == null && startDestination == PAIRING_ROUTE)
    val selected = TopLevelDestination.fromRoute(currentRoute)
        ?: TopLevelDestination.History
    val colors = ClipboxTokens.colors

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        if (isPairing) {
            ClipboxNavHost(
                navController = navController,
                startDestination = startDestination,
                onPaired = onPaired,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (maxWidth >= WideLayoutMinWidth) {
            Row(Modifier.fillMaxSize()) {
                ClipboxNavigationRail(
                    selected = selected,
                    onSelect = { navController.switchTo(it) },
                )
                ClipboxNavHost(
                    navController = navController,
                    startDestination = startDestination,
                    onPaired = onPaired,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                ClipboxNavHost(
                    navController = navController,
                    startDestination = startDestination,
                    onPaired = onPaired,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
                ClipboxBottomBar(
                    selected = selected,
                    onSelect = { navController.switchTo(it) },
                )
            }
        }
    }
}

private fun NavHostController.switchTo(destination: TopLevelDestination) {
    navigate(destination.route) {
        // 一级页共用起始页：切换时保存当前页状态，返回时恢复输入与滚动位置
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun ClipboxBottomBar(
    selected: TopLevelDestination,
    onSelect: (TopLevelDestination) -> Unit,
) {
    val colors = ClipboxTokens.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background),
    ) {
        HorizontalDivider(thickness = 1.dp, color = colors.ink)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
        ) {
            TopLevelDestination.entries.forEachIndexed { index, destination ->
                if (index > 0) {
                    VerticalDivider(
                        modifier = Modifier.fillMaxHeight(),
                        thickness = 1.dp,
                        color = colors.line,
                    )
                }
                BottomBarItem(
                    destination = destination,
                    selected = destination == selected,
                    onSelect = onSelect,
                )
            }
        }
    }
}

@Composable
private fun RowScope.BottomBarItem(
    destination: TopLevelDestination,
    selected: Boolean,
    onSelect: (TopLevelDestination) -> Unit,
) {
    val colors = ClipboxTokens.colors
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .background(if (selected) colors.panel else Color.Transparent)
            .clickable { onSelect(destination) }
            .drawBehind {
                if (selected) {
                    drawRect(
                        color = colors.accent,
                        size = Size(size.width, 2.dp.toPx()),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(destination.label),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) colors.ink else colors.ink3,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

@Composable
private fun ClipboxNavigationRail(
    selected: TopLevelDestination,
    onSelect: (TopLevelDestination) -> Unit,
) {
    val colors = ClipboxTokens.colors
    Column(
        modifier = Modifier
            .width(200.dp)
            .fillMaxHeight()
            .background(colors.background)
            .drawBehind {
                val x = size.width - 0.5.dp.toPx()
                drawLine(
                    color = colors.line,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .padding(horizontal = 16.dp, vertical = 20.dp),
    ) {
        TopLevelDestination.entries.forEach { destination ->
            RailItem(
                destination = destination,
                selected = destination == selected,
                onSelect = onSelect,
            )
        }
    }
}

@Composable
private fun RailItem(
    destination: TopLevelDestination,
    selected: Boolean,
    onSelect: (TopLevelDestination) -> Unit,
) {
    val colors = ClipboxTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(if (selected) colors.panel else Color.Transparent)
            .clickable { onSelect(destination) }
            .drawBehind {
                if (selected) {
                    drawRect(
                        color = colors.accent,
                        size = Size(2.dp.toPx(), size.height),
                    )
                }
            }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = destination.icon,
            contentDescription = null,
            tint = if (selected) colors.ink else colors.ink2,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(destination.label),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) colors.ink else colors.ink2,
        )
    }
}
