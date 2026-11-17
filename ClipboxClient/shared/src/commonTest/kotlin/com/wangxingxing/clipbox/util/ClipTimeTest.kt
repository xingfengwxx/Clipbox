/**
 * author : 王星星
 * date : 2026-11-16 17:15
 * email : 1099420259@qq.com
 * description : 记录时间口径测试：固定 UTC 时区与时刻，覆盖刚刚、分钟、小时、绝对时间、时钟回拨与三语日期格式
 */
package com.wangxingxing.clipbox.util

import com.wangxingxing.clipbox.settings.AppLanguage
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class ClipTimeTest {

    private val now = 1_763_294_400_000L // 2025-11-16 12:00:00 UTC

    @Test
    fun lessThanOneMinuteIsJustNow() {
        assertEquals(
            ClipTimeLabel.JustNow,
            clipTimeLabel(
                createdAtMillis = now - 59_000,
                nowMillis = now,
                language = AppLanguage.ZH_HANS,
                timeZone = TimeZone.UTC,
            ),
        )
    }

    @Test
    fun minutesAreRoundedDown() {
        assertEquals(
            ClipTimeLabel.MinutesAgo(5),
            clipTimeLabel(
                createdAtMillis = now - 5 * 60_000 - 30_000,
                nowMillis = now,
                language = AppLanguage.ZH_HANS,
                timeZone = TimeZone.UTC,
            ),
        )
    }

    @Test
    fun hoursAreRoundedDown() {
        assertEquals(
            ClipTimeLabel.HoursAgo(2),
            clipTimeLabel(
                createdAtMillis = now - 2 * 3_600_000 - 1,
                nowMillis = now,
                language = AppLanguage.ZH_HANS,
                timeZone = TimeZone.UTC,
            ),
        )
    }

    @Test
    fun beyondOneDayUsesSimplifiedChineseFormat() {
        assertEquals(
            ClipTimeLabel.Absolute("2025-11-15 11:00"),
            clipTimeLabel(
                createdAtMillis = now - 25 * 3_600_000,
                nowMillis = now,
                language = AppLanguage.ZH_HANS,
                timeZone = TimeZone.UTC,
            ),
        )
    }

    @Test
    fun beyondOneDayUsesTraditionalChineseFormat() {
        assertEquals(
            ClipTimeLabel.Absolute("2025/11/15 11:00"),
            clipTimeLabel(
                createdAtMillis = now - 25 * 3_600_000,
                nowMillis = now,
                language = AppLanguage.ZH_TW,
                timeZone = TimeZone.UTC,
            ),
        )
    }

    @Test
    fun beyondOneDayUsesEnglishMonthAbbreviation() {
        assertEquals(
            ClipTimeLabel.Absolute("Nov 15, 2025 11:00"),
            clipTimeLabel(
                createdAtMillis = now - 25 * 3_600_000,
                nowMillis = now,
                language = AppLanguage.EN,
                timeZone = TimeZone.UTC,
            ),
        )
    }

    @Test
    fun futureTimestampFallsBackToJustNow() {
        assertEquals(
            ClipTimeLabel.JustNow,
            clipTimeLabel(
                createdAtMillis = now + 10_000,
                nowMillis = now,
                language = AppLanguage.ZH_HANS,
                timeZone = TimeZone.UTC,
            ),
        )
    }
}
