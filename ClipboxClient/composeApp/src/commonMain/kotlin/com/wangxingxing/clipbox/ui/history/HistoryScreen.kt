/**
 * author : 王星星
 * date : 2026-11-16 18:05
 * email : 1099420259@qq.com
 * description : 历史页 S01 / S02：顶栏、已配对才显示的发送器、置顶与全部两组记录、未配对与已配对两种空态；只消费 HistoryUiState，不访问 Room 或 ApiService；分享草稿只负责预填与发送后清除
 */
package com.wangxingxing.clipbox.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wangxingxing.clipbox.di.LocalAppGraph
import com.wangxingxing.clipbox.model.Clip
import com.wangxingxing.clipbox.resources.Res
import com.wangxingxing.clipbox.resources.app_name
import com.wangxingxing.clipbox.resources.history_copied
import com.wangxingxing.clipbox.resources.history_empty_paired_body
import com.wangxingxing.clipbox.resources.history_empty_title
import com.wangxingxing.clipbox.resources.history_empty_unpaired_body
import com.wangxingxing.clipbox.resources.history_group_all
import com.wangxingxing.clipbox.resources.history_group_pinned
import com.wangxingxing.clipbox.resources.history_over_limit
import com.wangxingxing.clipbox.resources.history_pair
import com.wangxingxing.clipbox.resources.history_sync_paired
import com.wangxingxing.clipbox.resources.history_sync_unpaired
import com.wangxingxing.clipbox.resources.nav_settings
import com.wangxingxing.clipbox.ui.components.ClipRow
import com.wangxingxing.clipbox.ui.navigation.NavIcons
import com.wangxingxing.clipbox.ui.theme.ClipboxTokens
import com.wangxingxing.clipbox.util.clipNowMillis
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

private const val TIME_REFRESH_MILLIS = 30_000L

@Suppress("DEPRECATION")
@Composable
fun HistoryRoute(
    onOpenSettings: () -> Unit,
    onPair: () -> Unit,
    incomingShareDraft: String? = null,
    onShareDraftConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val graph = LocalAppGraph.current
    val scope = rememberCoroutineScope()
    val viewModel = remember(graph) {
        HistoryViewModel(
            clipRepository = graph.clipRepository,
            settingsRepository = graph.settingsRepository,
            outboxWorker = graph.outboxWorker,
            scope = scope,
        )
    }
    val state by viewModel.state.collectAsState()
    var draft by rememberSaveable { mutableStateOf("") }
    // 只有新的分享正文到达才覆盖草稿；null 表示没有待处理分享，不能清掉用户正在编辑的内容
    LaunchedEffect(incomingShareDraft) {
        if (!incomingShareDraft.isNullOrEmpty()) {
            draft = incomingShareDraft
        }
    }
    // 未配对分享按 REQ-PAIR-008 直达配对页；同一份草稿只自动跳一次，用户取消后不再强推
    var pairingPrompted by rememberSaveable(incomingShareDraft) { mutableStateOf(false) }
    LaunchedEffect(state.isLoaded, state.paired, incomingShareDraft) {
        if (!pairingPrompted && state.isLoaded && !state.paired && !incomingShareDraft.isNullOrEmpty()) {
            pairingPrompted = true
            onPair()
        }
    }
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    // CMP 1.9 的跨端剪贴板仍是 LocalClipboardManager；T-CORE-12 落地后收敛到剪贴板服务
    val clipboard = LocalClipboardManager.current
    val copiedMessage = stringResource(Res.string.history_copied)
    val overLimitMessage = stringResource(Res.string.history_over_limit)
    // 事件收集器只启动一次，用最新回调避免 Activity 重建后仍引用旧闭包
    val latestOnShareDraftConsumed by rememberUpdatedState(onShareDraftConsumed)

    LaunchedEffect(viewModel, copiedMessage, overLimitMessage) {
        viewModel.events.collect { event ->
            when (event) {
                HistoryEvent.Sent -> {
                    draft = ""
                    // 分享草稿在用户确认发送后清除；普通输入发送时清除空草稿同样安全
                    latestOnShareDraftConsumed()
                    // 列表为空时新记录本来就在顶部，无需滚动
                    if (state.clips.isNotEmpty()) listState.animateScrollToItem(0)
                }

                HistoryEvent.TooLarge -> snackbarHostState.showSnackbar(overLimitMessage)
            }
        }
    }

    Box(modifier.fillMaxSize()) {
        HistoryScreen(
            state = state,
            draft = draft,
            onDraftChange = { draft = it },
            onSend = { viewModel.send(draft) },
            onCopy = { text ->
                clipboard.setText(AnnotatedString(text))
                scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
            },
            onTogglePin = { clip -> viewModel.setPinned(clip.id, !clip.pinned) },
            onMore = {},
            onOpenSettings = onOpenSettings,
            onPair = onPair,
            listState = listState,
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(ClipboxTokens.spacing.l),
        )
    }
}

@Composable
private fun HistoryScreen(
    state: HistoryUiState,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onCopy: (String) -> Unit,
    onTogglePin: (Clip) -> Unit,
    onMore: (Clip) -> Unit,
    onOpenSettings: () -> Unit,
    onPair: () -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val colors = ClipboxTokens.colors
    val spacing = ClipboxTokens.spacing

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        HistoryTopBar(paired = state.paired, onOpenSettings = onOpenSettings)
        HorizontalDivider(thickness = 1.dp, color = colors.ink)
        when {
            !state.isLoaded -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = colors.accent)
            }

            else -> BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                val nowMillis by produceState(initialValue = clipNowMillis()) {
                    while (true) {
                        delay(TIME_REFRESH_MILLIS)
                        value = clipNowMillis()
                    }
                }
                // 布局作用域标记不允许在 ColumnScope 内隐式读取 maxHeight，先取值再进 Column
                val composerMaxHeight = maxHeight / 3
                Column(Modifier.fillMaxSize()) {
                    if (state.paired) {
                        Composer(
                            value = draft,
                            onValueChange = onDraftChange,
                            onSend = onSend,
                            maxHeight = composerMaxHeight,
                            modifier = Modifier.padding(
                                start = spacing.l,
                                top = spacing.l,
                                end = spacing.l,
                            ),
                        )
                    }
                    if (state.clips.isEmpty()) {
                        EmptyState(
                            paired = state.paired,
                            onPair = onPair,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        )
                    } else {
                        ClipList(
                            state = state,
                            nowMillis = nowMillis,
                            listState = listState,
                            onCopy = onCopy,
                            onTogglePin = onTogglePin,
                            onMore = onMore,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryTopBar(
    paired: Boolean,
    onOpenSettings: () -> Unit,
) {
    val colors = ClipboxTokens.colors
    val spacing = ClipboxTokens.spacing
    val typography = ClipboxTokens.typography
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(start = spacing.l, end = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.app_name),
            style = MaterialTheme.typography.titleMedium,
            color = colors.ink,
        )
        Spacer(Modifier.width(spacing.m))
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(if (paired) colors.ok else colors.ink3, CircleShape),
        )
        Spacer(Modifier.width(spacing.s))
        Text(
            text = stringResource(
                if (paired) Res.string.history_sync_paired else Res.string.history_sync_unpaired,
            ),
            style = typography.mono,
            color = colors.ink2,
        )
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(48.dp)
                .clickable(onClick = onOpenSettings),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = NavIcons.Settings,
                contentDescription = stringResource(Res.string.nav_settings),
                tint = colors.ink2,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun ClipList(
    state: HistoryUiState,
    nowMillis: Long,
    listState: LazyListState,
    onCopy: (String) -> Unit,
    onTogglePin: (Clip) -> Unit,
    onMore: (Clip) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ClipboxTokens.colors
    val spacing = ClipboxTokens.spacing
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(bottom = spacing.l),
    ) {
        if (state.pinnedClips.isNotEmpty()) {
            item(key = "group_pinned") {
                GroupHeader(stringResource(Res.string.history_group_pinned, state.pinnedClips.size))
            }
            items(state.pinnedClips, key = { it.id }) { clip ->
                ClipRow(
                    clip = clip,
                    isLocalDevice = clip.isLocal(state.deviceId),
                    nowMillis = nowMillis,
                    onCopy = { onCopy(clip.content) },
                    onTogglePin = { onTogglePin(clip) },
                    onMore = { onMore(clip) },
                )
                HorizontalDivider(thickness = 1.dp, color = colors.line)
            }
        }
        if (state.unpinnedClips.isNotEmpty()) {
            item(key = "group_all") {
                GroupHeader(stringResource(Res.string.history_group_all, state.unpinnedClips.size))
            }
            items(state.unpinnedClips, key = { it.id }) { clip ->
                ClipRow(
                    clip = clip,
                    isLocalDevice = clip.isLocal(state.deviceId),
                    nowMillis = nowMillis,
                    onCopy = { onCopy(clip.content) },
                    onTogglePin = { onTogglePin(clip) },
                    onMore = { onMore(clip) },
                )
                HorizontalDivider(thickness = 1.dp, color = colors.line)
            }
        }
    }
}

private fun Clip.isLocal(deviceId: String): Boolean =
    deviceId.isNotEmpty() && sourceDeviceId == deviceId

@Composable
private fun GroupHeader(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(
            start = ClipboxTokens.spacing.l,
            top = ClipboxTokens.spacing.l,
            end = ClipboxTokens.spacing.l,
            bottom = ClipboxTokens.spacing.s,
        ),
        style = ClipboxTokens.typography.mono,
        color = ClipboxTokens.colors.ink3,
    )
}

@Composable
private fun EmptyState(
    paired: Boolean,
    onPair: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ClipboxTokens.colors
    val spacing = ClipboxTokens.spacing
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            EmptyArt()
            Spacer(Modifier.height(spacing.xl))
            Text(
                text = stringResource(Res.string.history_empty_title),
                style = MaterialTheme.typography.titleMedium,
                color = colors.ink,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(spacing.s))
            Text(
                text = stringResource(
                    if (paired) {
                        Res.string.history_empty_paired_body
                    } else {
                        Res.string.history_empty_unpaired_body
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = colors.ink2,
                textAlign = TextAlign.Center,
            )
            if (!paired) {
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onPair,
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(ClipboxTokens.radii.button),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.ink,
                        contentColor = colors.background,
                    ),
                    contentPadding = PaddingValues(horizontal = spacing.xl),
                ) {
                    Text(
                        text = stringResource(Res.string.history_pair),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

// 设计稿 EmptyArt 188×124：手机 / 笔记本 / 手机线稿与虚线连接，颜色只取 ink3
@Composable
private fun EmptyArt() {
    val ink = ClipboxTokens.colors.ink3
    Canvas(modifier = Modifier.size(width = 188.dp, height = 124.dp)) {
        val scale = size.width / 188f
        val strokeWidth = 1.3.dp.toPx()
        val shape = Stroke(width = strokeWidth)

        fun outline(x: Float, y: Float, w: Float, h: Float, radius: Float) {
            drawRoundRect(
                color = ink.copy(alpha = 0.55f),
                topLeft = Offset(x * scale, y * scale),
                size = Size(w * scale, h * scale),
                cornerRadius = CornerRadius(radius * scale),
                style = shape,
            )
        }

        fun bar(x: Float, y: Float, w: Float, h: Float, alpha: Float) {
            drawRoundRect(
                color = ink.copy(alpha = alpha),
                topLeft = Offset(x * scale, y * scale),
                size = Size(w * scale, h * scale),
                cornerRadius = CornerRadius(h * scale / 2f),
                style = Fill,
            )
        }

        outline(18.5f, 26.5f, 44f, 70f, 7f)
        bar(30f, 33.5f, 21f, 3f, 0.35f)
        bar(47f, 75f, 7f, 3f, 0.35f)
        outline(122.5f, 38.5f, 52f, 38f, 4f)
        bar(131f, 47f, 23f, 3f, 0.35f)
        bar(131f, 55f, 33f, 3f, 0.22f)
        outline(154f, 80f, 25f, 30f, 5f)

        drawLine(
            color = ink.copy(alpha = 0.55f),
            start = Offset(68f * scale, 60f * scale),
            end = Offset(116f * scale, 60f * scale),
            strokeWidth = strokeWidth,
            pathEffect = PathEffect.dashPathEffect(
                floatArrayOf(3.dp.toPx(), 5.dp.toPx()),
            ),
        )
        drawRoundRect(
            color = ink.copy(alpha = 0.8f),
            topLeft = Offset(83f * scale, 54.5f * scale),
            size = Size(11f * scale, 11f * scale),
            cornerRadius = CornerRadius(1.5f * scale),
            style = Fill,
        )
        drawRoundRect(
            color = ink.copy(alpha = 0.4f),
            topLeft = Offset(83f * scale, 54.5f * scale),
            size = Size(11f * scale, 11f * scale),
            cornerRadius = CornerRadius(1.5f * scale),
            style = Stroke(width = 1.dp.toPx()),
        )

        val sparkle = Stroke(width = 1.15.dp.toPx(), cap = StrokeCap.Round)
        val sparkleColor = ink.copy(alpha = 0.3f)
        for ((start, end) in sparkleLines()) {
            drawLine(
                color = sparkleColor,
                start = Offset(start.x * scale, start.y * scale),
                end = Offset(end.x * scale, end.y * scale),
                strokeWidth = sparkle.width,
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun sparkleLines() = listOf(
    Offset(72f, 45f) to Offset(68f, 51f),
    Offset(63f, 51f) to Offset(59f, 51f),
    Offset(112f, 75f) to Offset(116f, 81f),
    Offset(121f, 81f) to Offset(125f, 81f),
)
