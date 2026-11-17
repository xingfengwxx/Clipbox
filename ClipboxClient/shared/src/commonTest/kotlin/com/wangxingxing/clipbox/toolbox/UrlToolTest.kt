/**
 * author : 王星星
 * date : 2026-11-17 16:23
 * email : 1099420259@qq.com
 * description : UrlTool 单元测试：组件/整串编码差异、`+` 空格开关、UTF-8 往返与非法百分号位置
 */
package com.wangxingxing.clipbox.toolbox

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class UrlToolTest {

    @Test
    fun componentEncodingEscapesReservedCharacters() {
        assertEquals(
            "a%20b%26c%3Dd",
            UrlTool.encode("a b&c=d", wholeUrl = false, plusAsSpace = false),
        )
        assertEquals(
            "https%3A%2F%2Fexample.com%2Fa",
            UrlTool.encode("https://example.com/a", wholeUrl = false, plusAsSpace = false),
        )
    }

    @Test
    fun wholeUrlEncodingKeepsStructureCharacters() {
        assertEquals(
            "https://example.com/a%20b?q=1&r=2",
            UrlTool.encode("https://example.com/a b?q=1&r=2", wholeUrl = true, plusAsSpace = false),
        )
        assertEquals(
            "https://example.com/a+b?q=1&r=2",
            UrlTool.encode("https://example.com/a b?q=1&r=2", wholeUrl = true, plusAsSpace = true),
        )
    }

    @Test
    fun plusSwitchOnlyChangesSpaceMapping() {
        assertEquals(
            "a+b%26c",
            UrlTool.encode("a b&c", wholeUrl = false, plusAsSpace = true),
        )
        assertEquals(UrlResult.Success("a b"), UrlTool.decode("a+b", plusAsSpace = true))
        assertEquals(UrlResult.Success("a+b"), UrlTool.decode("a+b", plusAsSpace = false))
        assertEquals(UrlResult.Success("a+b"), UrlTool.decode("a%2Bb", plusAsSpace = true))
        assertEquals(UrlResult.Success("a b"), UrlTool.decode("a%20b", plusAsSpace = true))
    }

    @Test
    fun utf8RoundTripForChineseAndEmoji() {
        assertEquals("%E4%BD%A0%E5%A5%BD", UrlTool.encode("你好", wholeUrl = false, plusAsSpace = false))
        assertEquals(
            "%E5%89%AA%E8%B4%B4%E6%9D%BF%20%F0%9F%98%80",
            UrlTool.encode("剪贴板 😀", wholeUrl = false, plusAsSpace = false),
        )
        // `+` 开关开启时字面 `+` 与空格不可区分，往返样本不包含字面 `+`
        val text = "剪贴板 😀 https://例子.com/路径?q=a b&x=1"
        for (plusAsSpace in listOf(false, true)) {
            val encoded = UrlTool.encode(text, wholeUrl = true, plusAsSpace = plusAsSpace)
            assertEquals(UrlResult.Success(text), UrlTool.decode(encoded, plusAsSpace = plusAsSpace))
        }
    }

    @Test
    fun emptyStringRoundTrips() {
        assertEquals("", UrlTool.encode("", wholeUrl = false, plusAsSpace = false))
        assertEquals(UrlResult.Success(""), UrlTool.decode("", plusAsSpace = false))
        assertEquals(UrlResult.Success(""), UrlTool.decode("", plusAsSpace = true))
    }

    @Test
    fun illegalPercentSequenceReportsPosition() {
        assertIs<UrlResult.InvalidPercent>(UrlTool.decode("abc%zz", plusAsSpace = false))
        assertEquals(UrlResult.InvalidPercent(4), UrlTool.decode("abc%", plusAsSpace = false))
        assertEquals(UrlResult.InvalidPercent(4), UrlTool.decode("abc%2", plusAsSpace = false))
        assertEquals(UrlResult.InvalidPercent(2), UrlTool.decode("a%2Gx", plusAsSpace = false))
        assertEquals(UrlResult.InvalidPercent(1), UrlTool.decode("%", plusAsSpace = true))
    }

    @Test
    fun literalPercentMustBeEncoded() {
        assertEquals("%25", UrlTool.encode("%", wholeUrl = true, plusAsSpace = false))
        assertEquals(UrlResult.Success("%"), UrlTool.decode("%25", plusAsSpace = false))
    }
}
