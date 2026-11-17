/**
 * author : 王星星
 * date : 2026-11-17 14:20
 * email : 1099420259@qq.com
 * description : 手动同步结果；区分配对、拉取失败与成功，供设置页展示“已同步 / 离线 / 需要重新配对”而不解析异常文本
 */
package com.wangxingxing.clipbox.sync

import com.wangxingxing.clipbox.model.ApiFailure

sealed interface SyncNowResult {
    data class Success(
        val appliedItems: Int,
        val resynced: Boolean,
        val outboxApplied: Int,
    ) : SyncNowResult

    data class Failure(val failure: ApiFailure) : SyncNowResult

    data object WaitingForPairing : SyncNowResult
}
