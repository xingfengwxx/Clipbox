/**
 * author : 王星星
 * date : 2026-11-16 17:15
 * email : 1099420259@qq.com
 * description : 记录时间的展示口径：24 小时内给相对时间，超过 24 小时给本地绝对时间；只返回结构化结果，文案与日期格式由 UI 按当前语言映射
 */
package com.wangxingxing.clipbox.util

import com.wangxingxing.clipbox.settings.AppLanguage
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private const val MINUTE_MILLIS = 60_000L
private const val HOUR_MILLIS = 60 * MINUTE_MILLIS
private const val DAY_MILLIS = 24 * HOUR_MILLIS

private val EN_MONTH_ABBREVIATIONS = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

@OptIn(ExperimentalTime::class)
fun clipNowMillis(): Long = Clock.System.now().toEpochMilliseconds()

sealed interface ClipTimeLabel {
    data object JustNow : ClipTimeLabel
    data class MinutesAgo(val minutes: Int) : ClipTimeLabel
    data class HoursAgo(val hours: Int) : ClipTimeLabel

    /** 已按当前语言的日期格式与本地时区格式化，例如 2026-11-17 15:56 / 2026/11/17 15:56 / Nov 17, 2026 15:56。 */
    data class Absolute(val text: String) : ClipTimeLabel
}

fun clipTimeLabel(
    createdAtMillis: Long,
    nowMillis: Long,
    language: AppLanguage,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): ClipTimeLabel {
    // 设备时钟回拨或服务端时间略超前时按“刚刚”处理，避免出现负数分钟
    val elapsed = (nowMillis - createdAtMillis).coerceAtLeast(0)
    return when {
        elapsed < MINUTE_MILLIS -> ClipTimeLabel.JustNow
        elapsed < HOUR_MILLIS -> ClipTimeLabel.MinutesAgo((elapsed / MINUTE_MILLIS).toInt())
        elapsed < DAY_MILLIS -> ClipTimeLabel.HoursAgo((elapsed / HOUR_MILLIS).toInt())
        else -> ClipTimeLabel.Absolute(formatAbsoluteTime(createdAtMillis, language, timeZone))
    }
}

/** 按本地时区与当前语言输出绝对时间；分享到期时间等只需绝对时间的场景直接复用。 */
@OptIn(ExperimentalTime::class)
fun formatAbsoluteTime(
    epochMillis: Long,
    language: AppLanguage,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    val local = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(timeZone)
    val hour = local.hour.toString().padStart(2, '0')
    val minute = local.minute.toString().padStart(2, '0')
    return "${formatDate(local, language)} $hour:$minute"
}

/** 更新发布时间的纯日期展示；描述里的 publishedAt 是 RFC 3339，解析失败返回 null 由 UI 显示占位符 */
@OptIn(ExperimentalTime::class)
fun formatPublishedDate(
    publishedAt: String,
    language: AppLanguage,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String? {
    val local = runCatching { Instant.parse(publishedAt) }.getOrNull()
        ?.toLocalDateTime(timeZone)
        ?: return null
    return formatDate(local, language)
}

private fun formatDate(local: LocalDateTime, language: AppLanguage): String = when (language) {
    AppLanguage.ZH_HANS -> buildString {
        append(local.year.toString().padStart(4, '0'))
        append('-')
        // kotlinx-datetime 0.7.1 的 Month 没有 number 属性，ordinal 以 JANUARY = 0 起始
        append((local.month.ordinal + 1).toString().padStart(2, '0'))
        append('-')
        append(local.day.toString().padStart(2, '0'))
    }

    AppLanguage.ZH_TW -> buildString {
        append(local.year.toString().padStart(4, '0'))
        append('/')
        append((local.month.ordinal + 1).toString().padStart(2, '0'))
        append('/')
        append(local.day.toString().padStart(2, '0'))
    }

    AppLanguage.EN -> buildString {
        append(EN_MONTH_ABBREVIATIONS[local.month.ordinal])
        append(' ')
        append(local.day)
        append(", ")
        append(local.year.toString().padStart(4, '0'))
    }
}
