/**
 * author : 王星星
 * date : 2026-11-16 18:05
 * email : 1099420259@qq.com
 * description : 历史页只读状态；数据全部来自 Room 观察流，分组只做视图拆分，不改变仓库排序
 */
package com.wangxingxing.clipbox.ui.history

import com.wangxingxing.clipbox.model.Clip

data class HistoryUiState(
    val isLoaded: Boolean = false,
    val paired: Boolean = false,
    val deviceId: String = "",
    val clips: List<Clip> = emptyList(),
) {
    val pinnedClips: List<Clip> = clips.filter { it.pinned }
    val unpinnedClips: List<Clip> = clips.filterNot { it.pinned }
}

sealed interface HistoryEvent {
    data object Sent : HistoryEvent
    data object TooLarge : HistoryEvent
}
