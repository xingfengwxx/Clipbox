/**
 * author : 王星星
 * date : 2026-11-16 14:01
 * email : 1099420259@qq.com
 * description : UTF-8 字节计数测试；重点覆盖中文与 emoji 与 String.length 不一致的场景，以及 64 KiB 边界
 */
package com.wangxingxing.clipbox.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TextSizeTest {

    @Test
    fun countsAsciiBytes() {
        assertEquals(0, TextSize.utf8ByteCount(""))
        assertEquals(5, TextSize.utf8ByteCount("hello"))
    }

    @Test
    fun countsChineseBytesNotCharacters() {
        val text = "中文"

        assertEquals(6, TextSize.utf8ByteCount(text))
        assertNotEquals(text.length, TextSize.utf8ByteCount(text))
    }

    @Test
    fun countsEmojiBytes() {
        val text = "😀"

        assertEquals(4, TextSize.utf8ByteCount(text))
        assertNotEquals(text.length, TextSize.utf8ByteCount(text))
    }

    @Test
    fun countsMixedText() {
        assertEquals(9, TextSize.utf8ByteCount("a中😀b"))
    }

    @Test
    fun handlesClipLimitBoundary() {
        val max = TextSize.MAX_CLIP_UTF8_BYTES

        assertEquals(max, TextSize.utf8ByteCount("a".repeat(max)))
        assertEquals(max + 3, TextSize.utf8ByteCount("a".repeat(max) + "中"))
        assertEquals(max, TextSize.utf8ByteCount("中".repeat(21_845) + "a"))
    }

    @Test
    fun warningThresholdIsNinetyPercentRoundedUp() {
        val max = TextSize.MAX_CLIP_UTF8_BYTES
        val warn = TextSize.WARN_CLIP_UTF8_BYTES

        // 65536 × 0.9 = 58982.4，向上取整后从 58,983 B 开始预警
        assertEquals(58_983, warn)
        assertEquals(19661 * 3, warn)
        assertEquals(warn - 1, TextSize.utf8ByteCount("a".repeat(warn - 1)))
        assertEquals(warn, TextSize.utf8ByteCount("中".repeat(19_661)))
        assertEquals(warn + 3, TextSize.utf8ByteCount("a".repeat(warn) + "中"))
        assertEquals(max, TextSize.utf8ByteCount("a".repeat(max)))
        assertTrue(warn < max)
    }
}
