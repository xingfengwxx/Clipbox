/**
 * author : 王星星
 * date : 2026-11-17 11:26
 * email : 1099420259@qq.com
 * description : 退避算法单元测试；断言 1/2/3 次重试的基础间隔、上限 60s 与 ±20% 抖动边界
 */
package com.wangxingxing.clipbox.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BackoffTest {

    @Test
    fun baseDelaysFollowExponentialSequence() {
        assertEquals(1_000L, Backoff.baseDelayMillis(1))
        assertEquals(2_000L, Backoff.baseDelayMillis(2))
        assertEquals(4_000L, Backoff.baseDelayMillis(3))
        assertEquals(8_000L, Backoff.baseDelayMillis(4))
        assertEquals(16_000L, Backoff.baseDelayMillis(5))
        assertEquals(32_000L, Backoff.baseDelayMillis(6))
        assertEquals(60_000L, Backoff.baseDelayMillis(7))
        assertEquals(60_000L, Backoff.baseDelayMillis(30))
    }

    @Test
    fun jitterCoversLowerAndUpperBounds() {
        assertEquals(800L, Backoff.delayMillis(1, 0.0))
        assertEquals(1_200L, Backoff.delayMillis(1, 1.0))
        assertEquals(1_600L, Backoff.delayMillis(2, 0.0))
        assertEquals(2_400L, Backoff.delayMillis(2, 1.0))
        assertEquals(3_200L, Backoff.delayMillis(3, 0.0))
        assertEquals(4_800L, Backoff.delayMillis(3, 1.0))
        assertEquals(8_000L, Backoff.delayMillis(4, 0.5))
    }

    @Test
    fun delayNeverExceedsCap() {
        for (retryCount in 1..40) {
            for (sample in listOf(0.0, 0.25, 0.5, 0.75, 1.0)) {
                val delay = Backoff.delayMillis(retryCount, sample)
                assertTrue(
                    delay in 1..Backoff.MAX_DELAY_MILLIS,
                    "retryCount=$retryCount sample=$sample delay=$delay",
                )
            }
        }
        // 抖动后的上界也不能突破 60s
        assertEquals(60_000L, Backoff.delayMillis(7, 1.0))
    }
}
