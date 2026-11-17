/**
 * author : 王星星
 * date : 2026-11-16 18:05
 * email : 1099420259@qq.com
 * description : 历史页只读状态；clips 在搜索时是防抖后的本地结果，分组只做视图拆分，不改变仓库排序
 */
package com.wangxingxing.clipbox.ui.history

import com.wangxingxing.clipbox.model.Clip
import com.wangxingxing.clipbox.model.SyncStatus

data class HistoryUiState(
    val isLoaded: Boolean = false,
    val paired: Boolean = false,
    val deviceId: String = "",
    val query: String = "",
    val searching: Boolean = false,
    val clips: List<Clip> = emptyList(),
    val syncStatus: SyncStatus = SyncStatus.Idle,
) {
    val pinnedClips: List<Clip> = clips.filter { it.pinned }
    val unpinnedClips: List<Clip> = clips.filterNot { it.pinned }
}

sealed interface HistoryEvent {
    data object Sent : HistoryEvent
    data object TooLarge : HistoryEvent
}
