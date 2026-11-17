/**
 * author : 王星星
 * date : 2026-11-16 17:15
 * email : 1099420259@qq.com
 * description : 记录时间的展示口径：24 小时内给相对时间，超过 24 小时给本地绝对时间；只返回结构化结果，文案由 UI 按当前语言映射
 */
package com.wangxingxing.clipbox.util

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private const val MINUTE_MILLIS = 60_000L
private const val HOUR_MILLIS = 60 * MINUTE_MILLIS
private const val DAY_MILLIS = 24 * HOUR_MILLIS

@OptIn(ExperimentalTime::class)
fun clipNowMillis(): Long = Clock.System.now().toEpochMilliseconds()

sealed interface ClipTimeLabel {
    data object JustNow : ClipTimeLabel
    data class MinutesAgo(val minutes: Int) : ClipTimeLabel
    data class HoursAgo(val hours: Int) : ClipTimeLabel

    /** 已按本地时区格式化为 yyyy-MM-dd HH:mm。 */
    data class Absolute(val text: String) : ClipTimeLabel
}

fun clipTimeLabel(
    createdAtMillis: Long,
    nowMillis: Long,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): ClipTimeLabel {
    // 设备时钟回拨或服务端时间略超前时按“刚刚”处理，避免出现负数分钟
    val elapsed = (nowMillis - createdAtMillis).coerceAtLeast(0)
    return when {
        elapsed < MINUTE_MILLIS -> ClipTimeLabel.JustNow
        elapsed < HOUR_MILLIS -> ClipTimeLabel.MinutesAgo((elapsed / MINUTE_MILLIS).toInt())
        elapsed < DAY_MILLIS -> ClipTimeLabel.HoursAgo((elapsed / HOUR_MILLIS).toInt())
        else -> ClipTimeLabel.Absolute(formatAbsoluteTime(createdAtMillis, timeZone))
    }
}

/** 按本地时区输出 yyyy-MM-dd HH:mm；分享到期时间等只需绝对时间的场景直接复用。 */
@OptIn(ExperimentalTime::class)
fun formatAbsoluteTime(
    epochMillis: Long,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    val local = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(timeZone)
    return buildString {
        append(local.year.toString().padStart(4, '0'))
        append('-')
        // kotlinx-datetime 0.7.1 的 Month 没有 number 属性，ordinal 以 JANUARY = 0 起始
        append((local.month.ordinal + 1).toString().padStart(2, '0'))
        append('-')
        append(local.day.toString().padStart(2, '0'))
        append(' ')
        append(local.hour.toString().padStart(2, '0'))
        append(':')
        append(local.minute.toString().padStart(2, '0'))
    }
}
