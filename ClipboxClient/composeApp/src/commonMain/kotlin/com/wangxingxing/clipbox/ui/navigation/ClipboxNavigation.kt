/**
 * author : 王星星
 * date : 2026-11-16 16:29
 * email : 1099420259@qq.com
 * description : 自适应导航壳：<600dp 用底部三栏，≥600dp 用 200dp 侧栏；切换一级页时保存 / 恢复各自返回栈状态；配对页、外观页与工具详情全屏不显示导航；分享草稿参数原样透传；App 层观察本地记录并决定是否弹出新记录 Snackbar
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import com.wangxingxing.clipbox.model.SyncStatus
import com.wangxingxing.clipbox.repository.ClipRepository
import com.wangxingxing.clipbox.resources.Res
import com.wangxingxing.clipbox.resources.history_received_new
import com.wangxingxing.clipbox.resources.history_view_new
import com.wangxingxing.clipbox.settings.SettingsRepository
import com.wangxingxing.clipbox.sync.SyncStatusHolder
import com.wangxingxing.clipbox.ui.history.HistoryViewportState
import com.wangxingxing.clipbox.ui.theme.ClipboxTokens
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

// 架构 §7.1：<600dp 底部导航，≥600dp 左侧 NavigationRail
private val WideLayoutMinWidth = 600.dp
// 宽屏只约束正文宽度，避免 iPad 横屏与桌面大窗口把列表和表单拉成整屏
private val WideContentMaxWidth = 880.dp
private val BottomBarHeight = 64.dp

// 同一批新记录在窗口内只提示一次；提示展示 3 秒后自动消失
private const val HINT_BATCH_MILLIS = 500L
private const val HINT_DURATION_MILLIS = 3_000L

@Composable
fun ClipboxNavigationHost(
    navController: NavHostController,
    startDestination: String = TopLevelDestination.History.route,
    modifier: Modifier = Modifier,
    incomingShareDraft: String? = null,
    onShareDraftConsumed: () -> Unit = {},
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    // NavHost 首次组合时返回栈可能尚未就绪，按起始路由判断可避免全屏页闪出导航条
    val isFullScreen = currentRoute == PAIRING_ROUTE ||
        currentRoute == APPEARANCE_ROUTE ||
        currentRoute?.startsWith(TOOL_DETAIL_PREFIX) == true ||
        (currentRoute == null && startDestination == PAIRING_ROUTE)
    val selected = TopLevelDestination.fromRoute(currentRoute)
        ?: TopLevelDestination.History
    val colors = ClipboxTokens.colors
    val viewport = koinInject<HistoryViewportState>()
    val syncStatusHolder = koinInject<SyncStatusHolder>()
    val clipRepository = koinInject<ClipRepository>()
    val settingsRepository = koinInject<SettingsRepository>()
    val snackbarHostState = remember { SnackbarHostState() }
    val receivedNewLabel = stringResource(Res.string.history_received_new)
    val viewLabel = stringResource(Res.string.history_view_new)
    // 提示延迟 500ms 展示，路由与顶部状态必须在展示与点击时取最新值
    val currentRouteState = rememberUpdatedState(currentRoute)

    LaunchedEffect(clipRepository, settingsRepository) {
        // 同批到达只提示一次；缓冲满时丢弃旧触发，新事件替换旧提示而不堆叠
        val hintTriggers = MutableSharedFlow<Unit>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
        launch {
            hintTriggers.debounce(HINT_BATCH_MILLIS).collect {
                val onHistoryAtTop = currentRouteState.value == TopLevelDestination.History.route &&
                    viewport.isAtTop
                // 重同步会整体重建列表，不算新记录
                if (onHistoryAtTop || syncStatusHolder.status.value is SyncStatus.Resyncing) {
                    return@collect
                }
                snackbarHostState.currentSnackbarData?.dismiss()
                val result = withTimeoutOrNull(HINT_DURATION_MILLIS) {
                    snackbarHostState.showSnackbar(
                        message = receivedNewLabel,
                        actionLabel = viewLabel,
                    )
                }
                if (result == SnackbarResult.ActionPerformed) {
                    navController.switchTo(TopLevelDestination.History)
                    viewport.requestScrollToTop()
                }
            }
        }

        // 只统计新增记录，并排除本机发送与首次加载；列表整体缩减视为重同步基线
        val knownIds = mutableSetOf<String>()
        var firstEmission = true
        combine(
            clipRepository.observeClips(),
            settingsRepository.settings,
        ) { clips, settings -> clips to settings.deviceId.orEmpty() }
            .collect { (clips, selfDeviceId) ->
                val ids = clips.mapTo(mutableSetOf()) { it.id }
                if (firstEmission) {
                    firstEmission = false
                    knownIds.addAll(ids)
                    return@collect
                }
                val added = clips.filter { it.id !in knownIds }
                val reset = knownIds.isNotEmpty() && ids.size < knownIds.size
                knownIds.clear()
                knownIds.addAll(ids)
                if (reset || added.isEmpty()) return@collect
                if (added.any { it.sourceDeviceId != selfDeviceId }) hintTriggers.tryEmit(Unit)
            }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        if (isFullScreen) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter,
            ) {
                ClipboxNavHost(
                    navController = navController,
                    startDestination = startDestination,
                    modifier = Modifier
                        .widthIn(max = WideContentMaxWidth)
                        .fillMaxSize(),
                    incomingShareDraft = incomingShareDraft,
                    onShareDraftConsumed = onShareDraftConsumed,
                )
            }
        } else if (maxWidth >= WideLayoutMinWidth) {
            Row(Modifier.fillMaxSize()) {
                ClipboxNavigationRail(
                    selected = selected,
                    onSelect = { navController.switchTo(it) },
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    ClipboxNavHost(
                        navController = navController,
                        startDestination = startDestination,
                        modifier = Modifier
                            .widthIn(max = WideContentMaxWidth)
                            .fillMaxSize(),
                        incomingShareDraft = incomingShareDraft,
                        onShareDraftConsumed = onShareDraftConsumed,
                    )
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                ClipboxNavHost(
                    navController = navController,
                    startDestination = startDestination,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    incomingShareDraft = incomingShareDraft,
                    onShareDraftConsumed = onShareDraftConsumed,
                )
                ClipboxBottomBar(
                    selected = selected,
                    onSelect = { navController.switchTo(it) },
                )
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    start = ClipboxTokens.spacing.l,
                    end = ClipboxTokens.spacing.l,
                    bottom = if (isFullScreen || maxWidth >= WideLayoutMinWidth) {
                        ClipboxTokens.spacing.l
                    } else {
                        BottomBarHeight + ClipboxTokens.spacing.l
                    },
                ),
        )
    }
}

internal fun NavHostController.switchTo(destination: TopLevelDestination) {
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
                .height(BottomBarHeight),
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
