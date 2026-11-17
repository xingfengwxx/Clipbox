/**
 * author : 王星星
 * date : 2026-11-17 11:20
 * email : 1099420259@qq.com
 * description : 同步重试的指数退避与抖动计算；Outbox 重放与后续 WS 重连共用同一区间（1s 起、60s 上限）。注意：上限必须作用在抖动之后，否则 +20% 抖动会突破 60s
 */
package com.wangxingxing.clipbox.sync

import kotlin.math.roundToLong

internal object Backoff {

    const val BASE_DELAY_MILLIS = 1_000L
    const val MAX_DELAY_MILLIS = 60_000L

    private const val JITTER_RATIO = 0.2

    // retryCount 从 1 开始：1s、2s、4s…32s，第 7 次起固定落在上限
    fun baseDelayMillis(retryCount: Int): Long =
        if (retryCount >= 7) MAX_DELAY_MILLIS else BASE_DELAY_MILLIS shl (retryCount - 1)

    // jitterSample 取 [0, 1)，映射到 [-20%, +20%]；传入 0 或 1 用于测试边界
    fun delayMillis(retryCount: Int, jitterSample: Double): Long {
        val factor = 1 - JITTER_RATIO + 2 * JITTER_RATIO * jitterSample.coerceIn(0.0, 1.0)
        return (baseDelayMillis(retryCount) * factor).roundToLong()
            .coerceIn(1, MAX_DELAY_MILLIS)
    }
}
