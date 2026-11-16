/**
 * author : 王星星
 * date : 2026-11-16 14:01
 * email : 1099420259@qq.com
 * description : ULID 格式与单调性测试；覆盖正则、时间排序、同毫秒递增与时钟回拨，测试使用未来时间避免与生成器内部状态互相干扰
 */
package com.wangxingxing.clipbox.util

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UlidTest {

    @Test
    fun generatedIdMatchesContractFormat() {
        val id = Ulid.generate(1_800_000_000_000)

        assertTrue(Ulid.isValid(id), "generated id should match ULID contract: $id")
        assertTrue(id.first() in '0'..'7', "first character must fit the 48-bit timestamp: $id")
    }

    @Test
    fun idSortsByTimestamp() {
        val base = 1_810_000_000_000
        val first = Ulid.generate(base)
        val second = Ulid.generate(base + 1)
        val third = Ulid.generate(base + 1_000)

        assertTrue(first < second, "$first should sort before $second")
        assertTrue(second < third, "$second should sort before $third")
    }

    @Test
    fun sameMillisecondProducesStrictlyIncreasingIds() {
        val base = 1_820_000_000_000
        val ids = List(5) { Ulid.generate(base) }

        ids.zipWithNext().forEach { (previous, next) ->
            assertTrue(previous < next, "$previous should sort before $next")
        }
        assertTrue(ids.toSet().size == ids.size, "same-millisecond ids must be unique")
    }

    @Test
    fun clockRollbackDoesNotProduceEarlierId() {
        val high = 1_830_000_000_000
        val first = Ulid.generate(high)
        val second = Ulid.generate(high - 10_000)

        assertTrue(second > first, "rollback id $second must sort after $first")
    }

    @Test
    fun rejectsMalformedIds() {
        assertFalse(Ulid.isValid(""))
        assertFalse(Ulid.isValid("01j8y6z3k4m5n6p7q8r9s0t1v2"), "lowercase must be rejected")
        assertFalse(Ulid.isValid("01J8Y6Z3K4M5N6P7Q8R9S0T1V"), "25 characters must be rejected")
        assertFalse(Ulid.isValid("01J8Y6Z3K4M5N6P7Q8R9S0T1V2X"), "27 characters must be rejected")
        assertFalse(Ulid.isValid("01J8Y6Z3K4M5N6P7Q8R9S0T1VU"), "Crockford excludes U")
        assertFalse(Ulid.isValid("01J8Y6Z3K4M5N6P7Q8R9S0T1V-"), "symbols must be rejected")
        assertTrue(Ulid.isValid("01J8Y6Z3K4M5N6P7Q8R9S0T1V2"))
    }
}
