/**
 * author : 王星星
 * date : 2026-11-17 16:26
 * email : 1099420259@qq.com
 * description : 时间戳工具纯逻辑：秒/毫秒自动识别与显式切换、时间戳转本地/UTC/ISO、日期字符串反算时间戳；
 *               时区由调用方注入，输出固定 yyyy-MM-dd HH:mm:ss，ISO 固定 UTC `Z` 后缀；
 *               注意：可表示范围限定 0001-01-01 至 9999-12-31，超出返回 outOfRange，不依赖平台 Instant 边界
 */
package com.wangxingxing.clipbox.toolbox

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

enum class TimestampUnit { Auto, Seconds, Millis }

sealed interface TimestampResult {
    /** 时间戳转换结果；local 按注入时区，utc 与 iso 固定 UTC */
    data class DateTime(val local: String, val utc: String, val iso: String) : TimestampResult

    /** 日期字符串反算结果 */
    data class Epoch(val seconds: Long, val millis: Long) : TimestampResult

    /** outOfRange 区分格式不合法与超出可表示范围 */
    data class Invalid(val outOfRange: Boolean) : TimestampResult
}

@OptIn(ExperimentalTime::class)
object TimestampTool {

    const val MAX_INPUT_LENGTH = 32

    private const val MIN_MILLIS = -62_135_596_800_000L // 0001-01-01T00:00:00Z
    private const val MAX_MILLIS = 253_402_300_799_999L // 9999-12-31T23:59:59.999Z

    // 需求只承诺 10 位秒与 13 位毫秒；11–12 位存在歧义，按秒处理（年份仍在可表示范围内）
    private const val MILLIS_DIGIT_THRESHOLD = 13

    /** zone 默认取运行设备时区，仅供 UI 调用；测试固定传入时区避免漂移 */
    fun convert(
        input: String,
        unit: TimestampUnit,
        zone: TimeZone = TimeZone.currentSystemDefault(),
    ): TimestampResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_INPUT_LENGTH) {
            return TimestampResult.Invalid(outOfRange = false)
        }
        return if (looksLikeTimestamp(trimmed)) {
            fromEpoch(trimmed, unit, zone)
        } else {
            fromDate(trimmed, zone)
        }
    }

    private fun fromEpoch(input: String, unit: TimestampUnit, zone: TimeZone): TimestampResult {
        val value = input.toLongOrNull()
            ?: return TimestampResult.Invalid(outOfRange = true)
        val millis = when (unit) {
            TimestampUnit.Millis -> value
            TimestampUnit.Seconds -> secondsToMillis(value) ?: return TimestampResult.Invalid(true)
            TimestampUnit.Auto -> {
                val digits = input.removePrefix("-").length
                if (digits >= MILLIS_DIGIT_THRESHOLD) {
                    value
                } else {
                    secondsToMillis(value) ?: return TimestampResult.Invalid(true)
                }
            }
        }
        if (millis < MIN_MILLIS || millis > MAX_MILLIS) {
            return TimestampResult.Invalid(outOfRange = true)
        }
        return toDateTimeResult(Instant.fromEpochMilliseconds(millis), zone)
    }

    private fun fromDate(input: String, zone: TimeZone): TimestampResult {
        val normalized = input.replace(' ', 'T')
        val instant = try {
            if (normalized.contains('T')) {
                LocalDateTime.parse(normalized).toInstant(zone)
            } else {
                LocalDate.parse(normalized).atStartOfDayIn(zone)
            }
        } catch (_: IllegalArgumentException) {
            return TimestampResult.Invalid(outOfRange = false)
        }
        val millis = instant.toEpochMilliseconds()
        if (millis < MIN_MILLIS || millis > MAX_MILLIS) {
            return TimestampResult.Invalid(outOfRange = true)
        }
        return TimestampResult.Epoch(seconds = instant.epochSeconds, millis = millis)
    }

    private fun toDateTimeResult(instant: Instant, zone: TimeZone): TimestampResult {
        val local = instant.toLocalDateTime(zone)
        val utc = instant.toLocalDateTime(TimeZone.UTC)
        return TimestampResult.DateTime(
            local = formatDateTime(local),
            utc = formatDateTime(utc),
            iso = "${formatDate(utc)}T${formatTime(utc)}Z",
        )
    }

    private fun looksLikeTimestamp(text: String): Boolean {
        val body = text.removePrefix("-")
        return body.isNotEmpty() && body.all { it in '0'..'9' }
    }

    private fun secondsToMillis(seconds: Long): Long? =
        if (seconds > Long.MAX_VALUE / 1000 || seconds < Long.MIN_VALUE / 1000) {
            null
        } else {
            seconds * 1000
        }

    private fun formatDateTime(value: LocalDateTime): String =
        "${formatDate(value)} ${formatTime(value)}"

    private fun formatDate(value: LocalDateTime): String = buildString {
        append(value.year.toString().padStart(4, '0'))
        append('-')
        append(pad2(value.month.ordinal + 1))
        append('-')
        append(pad2(value.day))
    }

    private fun formatTime(value: LocalDateTime): String =
        "${pad2(value.hour)}:${pad2(value.minute)}:${pad2(value.second)}"

    private fun pad2(value: Int): String = value.toString().padStart(2, '0')
}
