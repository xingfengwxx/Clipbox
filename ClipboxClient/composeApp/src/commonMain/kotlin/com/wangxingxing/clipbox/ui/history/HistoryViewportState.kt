/**
 * author : 王星星
 * date : 2026-11-17 14:45
 * email : 1099420259@qq.com
 * description : 历史页列表的跨页面视口状态：历史页上报是否停在顶部，App 层据此决定新记录是否提示；App 层请求回顶时历史页滚动到最新记录。
 *               注意：只有历史页能写 isAtTop，其他页面不得凭当前路由猜测滚动位置
 */
package com.wangxingxing.clipbox.ui.history

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class HistoryViewportState {

    var isAtTop by mutableStateOf(true)
        private set

    /** 回顶请求版本号；历史页用 [consumeScrollToTopRequest] 消费，避免每次重新进入都跳回顶部。 */
    var scrollToTopVersion by mutableIntStateOf(0)
        private set

    private var handledScrollVersion = 0

    fun updateAtTop(atTop: Boolean) {
        isAtTop = atTop
    }

    fun requestScrollToTop() {
        scrollToTopVersion += 1
    }

    fun consumeScrollToTopRequest(): Boolean {
        if (scrollToTopVersion <= handledScrollVersion) return false
        handledScrollVersion = scrollToTopVersion
        return true
    }
}
