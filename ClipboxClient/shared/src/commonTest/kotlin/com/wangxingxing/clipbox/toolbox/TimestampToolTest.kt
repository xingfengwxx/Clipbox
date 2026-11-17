/**
 * author : 王星星
 * date : 2026-11-17 16:26
 * email : 1099420259@qq.com
 * description : TimestampTool 单元测试：固定 Asia/Shanghai 与 UTC，覆盖自动识别、显式单位、日期反算、
 *               闰年、负数、范围边界与非法输入，避免系统默认时区导致漂移
 */
package com.wangxingxing.clipbox.toolbox

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class TimestampToolTest {

    private val shanghai = TimeZone.of("Asia/Shanghai")

    @Test
    fun secondsAutoConvertsToLocalUtcAndIso() {
        assertEquals(
            TimestampResult.DateTime(
                local = "2026-11-13 15:24:22",
                utc = "2026-11-13 07:24:22",
                iso = "2026-11-13T07:24:22Z",
            ),
            TimestampTool.convert("1794554662", TimestampUnit.Auto, shanghai),
        )
    }

    @Test
    fun thirteenDigitsAreTreatedAsMillisInAutoMode() {
        assertEquals(
            TimestampTool.convert("1794554662", TimestampUnit.Auto, shanghai),
            TimestampTool.convert("1794554662000", TimestampUnit.Auto, shanghai),
        )
    }

    @Test
    fun explicitUnitsOverrideAutoDetection() {
        assertEquals(
            TimestampResult.DateTime(
                local = "1970-01-22 02:29:14",
                utc = "1970-01-21 18:29:14",
                iso = "1970-01-21T18:29:14Z",
            ),
            TimestampTool.convert("1794554662", TimestampUnit.Millis, shanghai),
        )
        assertEquals(
            TimestampResult.Invalid(outOfRange = true),
            TimestampTool.convert("1794554662000", TimestampUnit.Seconds, shanghai),
        )
    }

    @Test
    fun negativeTimestampCrossesEpoch() {
        assertEquals(
            TimestampResult.DateTime(
                local = "1970-01-01 07:59:59",
                utc = "1969-12-31 23:59:59",
                iso = "1969-12-31T23:59:59Z",
            ),
            TimestampTool.convert("-1", TimestampUnit.Auto, shanghai),
        )
    }

    @Test
    fun dateStringConvertsBackToEpoch() {
        val expected = TimestampResult.Epoch(seconds = 1_794_549_862, millis = 1_794_549_862_000)
        assertEquals(expected, TimestampTool.convert("2026-11-13T14:04:22", TimestampUnit.Auto, shanghai))
        assertEquals(expected, TimestampTool.convert("2026-11-13 14:04:22", TimestampUnit.Auto, shanghai))
        assertEquals(
            TimestampResult.Epoch(seconds = 1_794_499_200, millis = 1_794_499_200_000),
            TimestampTool.convert("2026-11-13", TimestampUnit.Auto, shanghai),
        )
    }

    @Test
    fun leapYearIsAccepted() {
        assertEquals(
            TimestampResult.Epoch(seconds = 1_709_208_000, millis = 1_709_208_000_000),
            TimestampTool.convert("2024-02-29T12:00:00", TimestampUnit.Auto, TimeZone.UTC),
        )
    }

    @Test
    fun invalidInputIsRejected() {
        assertEquals(
            TimestampResult.Invalid(outOfRange = false),
            TimestampTool.convert("2026-13-01", TimestampUnit.Auto, shanghai),
        )
        assertEquals(
            TimestampResult.Invalid(outOfRange = false),
            TimestampTool.convert("abc", TimestampUnit.Auto, shanghai),
        )
        assertEquals(
            TimestampResult.Invalid(outOfRange = false),
            TimestampTool.convert("", TimestampUnit.Auto, shanghai),
        )
        assertEquals(
            TimestampResult.Invalid(outOfRange = false),
            TimestampTool.convert("1".repeat(TimestampTool.MAX_INPUT_LENGTH + 1), TimestampUnit.Auto, shanghai),
        )
    }

    @Test
    fun outOfRangeIsDistinguishedFromBadFormat() {
        assertEquals(
            TimestampResult.Invalid(outOfRange = true),
            TimestampTool.convert("253402300800", TimestampUnit.Seconds, TimeZone.UTC),
        )
        assertEquals(
            TimestampResult.Invalid(outOfRange = true),
            TimestampTool.convert("9223372036854775808", TimestampUnit.Auto, TimeZone.UTC),
        )
    }

    @Test
    fun boundaryValuesStayInsideRange() {
        assertEquals(
            TimestampResult.DateTime(
                local = "9999-12-31 23:59:59",
                utc = "9999-12-31 23:59:59",
                iso = "9999-12-31T23:59:59Z",
            ),
            TimestampTool.convert("253402300799", TimestampUnit.Seconds, TimeZone.UTC),
        )
        assertEquals(
            TimestampResult.Epoch(seconds = -62_135_596_800, millis = -62_135_596_800_000),
            TimestampTool.convert("0001-01-01", TimestampUnit.Auto, TimeZone.UTC),
        )
    }
}
