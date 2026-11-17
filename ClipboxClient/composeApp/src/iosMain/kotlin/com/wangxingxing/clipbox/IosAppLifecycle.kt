/**
 * author : 王星星
 * date : 2026-11-17 14:02
 * email : 1099420259@qq.com
 * description : iOS 前后台生命周期与分享草稿桥：驱动同步运行时启停，并向 Compose 提供待预填的分享草稿。
 *               注意：前台恢复只由同一 WS 状态机先 REST 补拉再连接，重复 active 回调不会建立多条连接；
 *                     后台只断开 WS 与停止重连，不注册后台任务、不读取剪贴板、不触发推送；
 *                     草稿按 id + createdAt 去重，未配对时保留文件，仅在用户确认发送后由 consumeShareDraft 删除。
 */
package com.wangxingxing.clipbox

import com.wangxingxing.clipbox.di.iosAppGraph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object IosAppLifecycle {

    private val draftStore = ShareDraftStore()
    private val _incomingShareDraft = MutableStateFlow<ShareDraft?>(null)
    private var lastDeliveredDraftKey: String? = null

    /** 供 Compose 根节点观察的待预填草稿；null 表示当前没有新分享。 */
    internal val incomingShareDraft: StateFlow<ShareDraft?> = _incomingShareDraft.asStateFlow()

    /** scenePhase == .active：唤醒补拉与 WS，并检查分享扩展是否留下新草稿。 */
    fun onForeground() {
        iosAppGraph().syncCoordinator.onForeground()
        checkShareDraft()
    }

    /** clipbox://share 深链到达；与 active 触发的检查共用去重，重复深链不会重复预填。 */
    fun onOpenShareUrl() {
        checkShareDraft()
    }

    /** scenePhase == .background：立即断开 WS 并停止重连；.inactive 不调用，避免切换控制中心时断连。 */
    fun onBackground() {
        iosAppGraph().syncCoordinator.onBackground()
    }

    /** 用户确认发送后清除草稿文件与待预填状态；未配对时不调用，草稿继续保留。 */
    fun consumeShareDraft() {
        runCatching { draftStore.clear() }
        lastDeliveredDraftKey = null
        _incomingShareDraft.value = null
    }

    private fun checkShareDraft() {
        val draft = runCatching { draftStore.read() }.getOrNull() ?: return
        val key = "${draft.createdAt}:${draft.id}"
        if (key == lastDeliveredDraftKey) return
        lastDeliveredDraftKey = key
        _incomingShareDraft.value = draft
    }
}
