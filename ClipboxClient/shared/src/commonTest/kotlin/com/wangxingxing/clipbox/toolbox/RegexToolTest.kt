/**
 * author : 王星星
 * date : 2026-11-17 16:31
 * email : 1099420259@qq.com
 * description : RegexTool 的分组区间、空匹配、Unicode、标志开关与非法正则测试；按平台 Regex 的 UTF-16 下标断言
 */
package com.wangxingxing.clipbox.toolbox

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RegexToolTest {

    @Test
    fun groupsAndRangesForNumberPairs() {
        val result = RegexTool.search("(\\d+)-(\\d+)", "12-34 和 5-6") as RegexResult.Matches
        assertEquals(2, result.matches.size)

        val first = result.matches[0]
        assertEquals(0..4, first.range)
        assertEquals("12-34", first.value)
        assertEquals(listOf("12", "34"), first.groups.map { it.value })
        assertEquals(0..1, first.groups[0].range)
        assertEquals(3..4, first.groups[1].range)

        val second = result.matches[1]
        assertEquals(8..10, second.range)
        assertEquals(listOf("5", "6"), second.groups.map { it.value })
    }

    @Test
    fun unmatchedOptionalGroupIsNull() {
        val result = RegexTool.search("(a)?(b)", "b") as RegexResult.Matches
        val groups = result.matches.single().groups
        assertEquals(2, groups.size)
        assertNull(groups[0].range)
        assertNull(groups[0].value)
        assertEquals("b", groups[1].value)
        assertEquals(0..0, groups[1].range)
    }

    @Test
    fun emptyMatchesAreCountedAndOrdered() {
        val result = RegexTool.search("a*", "bb") as RegexResult.Matches
        assertEquals(3, result.matches.size)
        assertTrue(result.matches.all { it.value.isEmpty() && it.range.isEmpty() })
        assertEquals(listOf(0, 1, 2), result.matches.map { it.range.first })
    }

    @Test
    fun unicodeMatchUsesCharacterIndices() {
        val result = RegexTool.search("好+", "你好好好世界") as RegexResult.Matches
        val match = result.matches.single()
        assertEquals(1..3, match.range)
        assertEquals("好好好", match.value)
    }

    @Test
    fun invalidPatternReturnsReason() {
        val result = RegexTool.search("(", "abc") as RegexResult.Invalid
        assertTrue(result.reason.isNotEmpty())
        assertTrue(result.reason.lines().size == 1)
    }

    @Test
    fun ignoreCaseFlag() {
        val result = RegexTool.search("abc", "xxABC", ignoreCase = true) as RegexResult.Matches
        assertEquals(2..4, result.matches.single().range)
        assertEquals(RegexResult.NoMatch, RegexTool.search("abc", "xxABC"))
    }

    @Test
    fun multilineFlag() {
        val text = "a\nb"
        val result = RegexTool.search("^b", text, multiline = true) as RegexResult.Matches
        assertEquals(2..2, result.matches.single().range)
        assertEquals(RegexResult.NoMatch, RegexTool.search("^b", text))
    }

    @Test
    fun dotMatchesAllFlag() {
        val text = "a\nb"
        val result = RegexTool.search("a.b", text, dotMatchesAll = true) as RegexResult.Matches
        assertEquals(0..2, result.matches.single().range)
        assertEquals(RegexResult.NoMatch, RegexTool.search("a.b", text))
    }

    @Test
    fun noMatchAndEmptyText() {
        assertEquals(RegexResult.NoMatch, RegexTool.search("z+", "abc"))
        assertEquals(RegexResult.NoMatch, RegexTool.search("z+", ""))
        assertNotNull(RegexTool.search("a?", "") as RegexResult.Matches)
    }
}
