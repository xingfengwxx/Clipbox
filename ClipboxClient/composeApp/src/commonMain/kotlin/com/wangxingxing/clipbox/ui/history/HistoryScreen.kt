/**
 * author : 王星星
 * date : 2026-11-16 18:05
 * email : 1099420259@qq.com
 * description : 历史页 S01 / S02 / S04：顶栏、同步状态条、已配对才显示的发送器、置顶与全部两组记录、未配对与已配对两种空态；只消费 HistoryUiState，不访问 Room 或 ApiService；上报列表顶部状态并消费 App 层的回顶请求；分享草稿只负责预填与发送后清除
 */
package com.wangxingxing.clipbox.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wangxingxing.clipbox.app.AppContainer
import com.wangxingxing.clipbox.clipboard.ClipboardController
import com.wangxingxing.clipbox.clipboard.ClipboardEvent
import com.wangxingxing.clipbox.model.Clip
import com.wangxingxing.clipbox.model.SyncState
import com.wangxingxing.clipbox.model.SyncStatus
import com.wangxingxing.clipbox.platform.ClipboardReadResult
import com.wangxingxing.clipbox.platform.ClipboardService
import com.wangxingxing.clipbox.platform.ClipboardWriteResult
import com.wangxingxing.clipbox.repository.ShareRepository
import com.wangxingxing.clipbox.resources.Res
import com.wangxingxing.clipbox.resources.app_name
import com.wangxingxing.clipbox.resources.clipboard_auto_paste_failed
import com.wangxingxing.clipbox.resources.clipboard_auto_send_too_large
import com.wangxingxing.clipbox.resources.clipboard_empty
import com.wangxingxing.clipbox.resources.clipboard_read_failed
import com.wangxingxing.clipbox.resources.clipboard_write_failed
import com.wangxingxing.clipbox.resources.history_copied
import com.wangxingxing.clipbox.resources.history_delete_body
import com.wangxingxing.clipbox.resources.history_delete_confirm
import com.wangxingxing.clipbox.resources.history_delete_title
import com.wangxingxing.clipbox.resources.history_empty_paired_body
import com.wangxingxing.clipbox.resources.history_empty_title
import com.wangxingxing.clipbox.resources.history_empty_unpaired_body
import com.wangxingxing.clipbox.resources.history_group_all
import com.wangxingxing.clipbox.resources.history_group_pinned
import com.wangxingxing.clipbox.resources.history_over_limit
import com.wangxingxing.clipbox.resources.history_pair
import com.wangxingxing.clipbox.resources.history_search_clear
import com.wangxingxing.clipbox.resources.history_search_count
import com.wangxingxing.clipbox.resources.history_search_empty_body
import com.wangxingxing.clipbox.resources.history_search_empty_title
import com.wangxingxing.clipbox.resources.history_search_placeholder
import com.wangxingxing.clipbox.resources.history_share_draft_replaced
import com.wangxingxing.clipbox.resources.history_share_offline
import com.wangxingxing.clipbox.resources.history_share_requires_sync
import com.wangxingxing.clipbox.resources.history_sync_paired
import com.wangxingxing.clipbox.resources.history_sync_unpaired
import com.wangxingxing.clipbox.resources.nav_settings
import com.wangxingxing.clipbox.resources.share_link_copied
import com.wangxingxing.clipbox.ui.components.ClipActionsSheet
import com.wangxingxing.clipbox.ui.components.ClipRow
import com.wangxingxing.clipbox.ui.components.ConfirmDialog
import com.wangxingxing.clipbox.ui.components.SyncStatusBar
import com.wangxingxing.clipbox.ui.navigation.NavIcons
import com.wangxingxing.clipbox.ui.share.ShareSheet
import com.wangxingxing.clipbox.ui.share.ShareStateHolder
import com.wangxingxing.clipbox.ui.theme.ClipboxTokens
import com.wangxingxing.clipbox.util.clipNowMillis
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private const val TIME_REFRESH_MILLIS = 30_000L

@Composable
fun HistoryRoute(
    onOpenSettings: () -> Unit,
    onPair: () -> Unit,
    incomingShareDraft: String? = null,
    onShareDraftConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val viewModel: HistoryViewModel = koinViewModel()
    val scope = rememberCoroutineScope()
    val viewport = koinInject<HistoryViewportState>()
    val shareRepository = koinInject<ShareRepository>()
    val clipboardService = koinInject<ClipboardService>()
    val clipboardController = koinInject<ClipboardController>()
    val isDesktop = koinInject<AppContainer>().isDesktop
    val state by viewModel.state.collectAsState()
    var selectedClip by remember { mutableStateOf<Clip?>(null) }
    var clipToDelete by remember { mutableStateOf<Clip?>(null) }
    var draft by rememberSaveable { mutableStateOf("") }
    // 每次分享草稿预填或粘贴后递增，驱动发送器请求焦点
    var composerFocusToken by remember { mutableStateOf(0) }
    // 未配对分享按 REQ-PAIR-008 直达配对页；同一份草稿只自动跳一次，用户取消后不再强推
    var pairingPrompted by rememberSaveable(incomingShareDraft) { mutableStateOf(false) }
    LaunchedEffect(state.isLoaded, state.paired, incomingShareDraft) {
        if (!pairingPrompted && state.isLoaded && !state.paired && !incomingShareDraft.isNullOrEmpty()) {
            pairingPrompted = true
            onPair()
        }
    }
    val listState = rememberLazyListState()
    // 顶部状态只由历史页上报；App 层据此决定是否弹新记录提示，避免猜测滚动位置
    val atTop by remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
    }
    LaunchedEffect(atTop) { viewport.updateAtTop(atTop) }
    LaunchedEffect(viewport.scrollToTopVersion) {
        // 回顶请求只消费一次；普通返回历史页时由 rememberSaveable 恢复原滚动位置
        if (viewport.consumeScrollToTopRequest()) listState.animateScrollToItem(0)
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val shareHolder = remember(shareRepository, scope) { ShareStateHolder(shareRepository, scope) }
    val shareState = shareHolder.state
    // 读写统一走平台剪贴板服务；移动端只在用户显式点击复制/粘贴时访问
    val copiedMessage = stringResource(Res.string.history_copied)
    val copyFailedMessage = stringResource(Res.string.clipboard_write_failed)
    val pasteFailedMessage = stringResource(Res.string.clipboard_read_failed)
    val clipboardEmptyMessage = stringResource(Res.string.clipboard_empty)
    val autoPasteFailedMessage = stringResource(Res.string.clipboard_auto_paste_failed)
    val autoSendTooLargeMessage = stringResource(Res.string.clipboard_auto_send_too_large)
    val overLimitMessage = stringResource(Res.string.history_over_limit)
    val deleteTitle = stringResource(Res.string.history_delete_title)
    val deleteBody = stringResource(Res.string.history_delete_body)
    val deleteConfirm = stringResource(Res.string.history_delete_confirm)
    val shareRequiresSync = stringResource(Res.string.history_share_requires_sync)
    val shareOffline = stringResource(Res.string.history_share_offline)
    val shareLinkCopied = stringResource(Res.string.share_link_copied)
    val shareDraftReplaced = stringResource(Res.string.history_share_draft_replaced)
    // 只有新的分享正文到达才覆盖草稿；null 表示没有待处理分享，不能清掉用户正在编辑的内容
    LaunchedEffect(incomingShareDraft) {
        if (!incomingShareDraft.isNullOrEmpty()) {
            // 新分享覆盖已有输入时提示一次；同一草稿的重复到达已在平台层按 id + createdAt 去重
            if (draft.isNotEmpty() && draft != incomingShareDraft) {
                scope.launch { snackbarHostState.showSnackbar(shareDraftReplaced) }
            }
            draft = incomingShareDraft
            composerFocusToken++
        }
    }
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

    val copyText: (String) -> Unit = { text ->
        scope.launch {
            when (clipboardService.writeText(text)) {
                ClipboardWriteResult.Success -> snackbarHostState.showSnackbar(copiedMessage)
                ClipboardWriteResult.Failed -> snackbarHostState.showSnackbar(copyFailedMessage)
            }
        }
    }
    // 自动粘贴失败与自动发送超限只做提示，不重试写入、不修改记录
    LaunchedEffect(clipboardController, autoPasteFailedMessage, autoSendTooLargeMessage) {
        clipboardController.events.collect { event ->
            when (event) {
                ClipboardEvent.AutoPasteFailed ->
                    snackbarHostState.showSnackbar(autoPasteFailedMessage)

                ClipboardEvent.AutoSendTooLarge ->
                    snackbarHostState.showSnackbar(autoSendTooLargeMessage)
            }
        }
    }
    // 移动端显式粘贴：只填充输入框，不自动发送；有草稿时追加到末尾避免覆盖用户输入
    val onPaste: (() -> Unit)? = if (isDesktop) {
        null
    } else {
        {
            scope.launch {
                when (val result = clipboardService.readText()) {
                    is ClipboardReadResult.Success -> {
                        draft = if (draft.isEmpty()) result.text else draft + "\n" + result.text
                        composerFocusToken++
                    }

                    ClipboardReadResult.Empty ->
                        snackbarHostState.showSnackbar(clipboardEmptyMessage)

                    ClipboardReadResult.Failed ->
                        snackbarHostState.showSnackbar(pasteFailedMessage)
                }
            }
        }
    }

    Box(modifier.fillMaxSize()) {
        HistoryScreen(
            state = state,
            draft = draft,
            onDraftChange = { draft = it },
            onQueryChange = viewModel::setQuery,
            onSend = { viewModel.send(draft) },
            onCopy = copyText,
            onPaste = onPaste,
            onTogglePin = { clip -> viewModel.setPinned(clip.id, !clip.pinned) },
            onMore = { selectedClip = it },
            onSyncNow = viewModel::syncNow,
            onOpenSettings = onOpenSettings,
            onPair = onPair,
            listState = listState,
            focusRequestToken = composerFocusToken,
        )
        selectedClip?.let { clip ->
            val shareReason = when {
                clip.syncState != SyncState.SYNCED -> shareRequiresSync
                state.syncStatus is SyncStatus.Offline ||
                    state.syncStatus is SyncStatus.Unauthorized -> shareOffline

                else -> null
            }
            ClipActionsSheet(
                clip = clip,
                shareDisabledReason = shareReason,
                onDismiss = { selectedClip = null },
                onCopy = {
                    selectedClip = null
                    copyText(clip.content)
                },
                onTogglePin = {
                    selectedClip = null
                    viewModel.setPinned(clip.id, !clip.pinned)
                },
                // 分享面板负责生成与展示链接，未同步/离线的禁用原因由 operation sheet 判定
                onShare = {
                    selectedClip = null
                    shareHolder.open(clip.id)
                },
                onRetry = {
                    selectedClip = null
                    viewModel.retry(clip.id)
                },
                onDelete = {
                    selectedClip = null
                    clipToDelete = clip
                },
            )
        }
        clipToDelete?.let { clip ->
            ConfirmDialog(
                title = deleteTitle,
                body = deleteBody,
                confirmText = deleteConfirm,
                onConfirm = {
                    clipToDelete = null
                    viewModel.delete(clip.id)
                },
                onDismiss = { clipToDelete = null },
            )
        }
        if (shareState.visible) {
            ShareSheet(
                state = shareState,
                syncStatus = state.syncStatus,
                onSelectTier = shareHolder::selectTier,
                onGenerate = shareHolder::generate,
                onBack = shareHolder::backToOptions,
                onCopyLink = { url ->
                    // 先收起弹层，Snackbar 才不会被弹层遮挡；结果保留，可再次打开查看
                    shareHolder.dismiss()
                    scope.launch {
                        when (clipboardService.writeText(url)) {
                            ClipboardWriteResult.Success ->
                                snackbarHostState.showSnackbar(shareLinkCopied)

                            ClipboardWriteResult.Failed ->
                                snackbarHostState.showSnackbar(copyFailedMessage)
                        }
                    }
                },
                onDismiss = shareHolder::dismiss,
            )
        }
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
    onQueryChange: (String) -> Unit,
    onSend: () -> Unit,
    onCopy: (String) -> Unit,
    onPaste: (() -> Unit)?,
    onTogglePin: (Clip) -> Unit,
    onMore: (Clip) -> Unit,
    onSyncNow: () -> Unit,
    onOpenSettings: () -> Unit,
    onPair: () -> Unit,
    listState: LazyListState,
    focusRequestToken: Int = 0,
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
        SyncStatusBar(
            status = state.syncStatus,
            paired = state.paired,
            onRetry = onSyncNow,
        )
        SearchBar(
            query = state.query,
            resultCount = if (state.searching) state.clips.size else null,
            onQueryChange = onQueryChange,
        )
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
                            onCopyInstead = onCopy,
                            onPaste = onPaste,
                            maxHeight = composerMaxHeight,
                            focusRequestToken = focusRequestToken,
                            modifier = Modifier.padding(
                                start = spacing.l,
                                top = spacing.l,
                                end = spacing.l,
                            ),
                        )
                    }
                    when {
                        state.searching && state.clips.isEmpty() -> SearchEmptyState(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        )

                        state.clips.isEmpty() -> EmptyState(
                            paired = state.paired,
                            onPair = onPair,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        )

                        else -> ClipList(
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
private fun SearchBar(
    query: String,
    resultCount: Int?,
    onQueryChange: (String) -> Unit,
) {
    val colors = ClipboxTokens.colors
    val spacing = ClipboxTokens.spacing
    val typography = ClipboxTokens.typography
    val shape = RoundedCornerShape(ClipboxTokens.radii.button)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = spacing.l, top = spacing.s, end = spacing.l)
            .background(colors.panel, shape)
            .border(1.dp, colors.line, shape)
            .padding(horizontal = spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 10.dp),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.ink),
            cursorBrush = SolidColor(colors.accent),
            decorationBox = { innerTextField ->
                Box {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.history_search_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.ink3,
                        )
                    }
                    innerTextField()
                }
            },
        )
        if (resultCount != null) {
            Text(
                text = pluralStringResource(Res.plurals.history_search_count, resultCount, resultCount),
                style = typography.mono,
                color = colors.ink3,
            )
            Spacer(Modifier.width(spacing.s))
            Box(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable { onQueryChange("") }
                    .padding(horizontal = spacing.xs),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(Res.string.history_search_clear),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.ink2,
                )
            }
        }
    }
}

@Composable
private fun SearchEmptyState(modifier: Modifier = Modifier) {
    val colors = ClipboxTokens.colors
    val spacing = ClipboxTokens.spacing
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.history_search_empty_title),
                style = MaterialTheme.typography.titleMedium,
                color = colors.ink,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(spacing.s))
            Text(
                text = stringResource(Res.string.history_search_empty_body),
                style = MaterialTheme.typography.bodySmall,
                color = colors.ink2,
                textAlign = TextAlign.Center,
            )
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
                GroupHeader(
                    pluralStringResource(
                        Res.plurals.history_group_pinned,
                        state.pinnedClips.size,
                        state.pinnedClips.size,
                    ),
                )
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
                GroupHeader(
                    pluralStringResource(
                        Res.plurals.history_group_all,
                        state.unpinnedClips.size,
                        state.unpinnedClips.size,
                    ),
                )
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
