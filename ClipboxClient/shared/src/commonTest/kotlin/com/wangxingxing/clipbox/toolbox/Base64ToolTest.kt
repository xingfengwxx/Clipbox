/**
 * author : 王星星
 * date : 2026-11-17 16:25
 * email : 1099420259@qq.com
 * description : Base64 工具纯逻辑测试：标准/URL-safe 字母表、UTF-8 往返、空白与填充容错、非法输入
 */
package com.wangxingxing.clipbox.toolbox

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class Base64ToolTest {

    @Test
    fun standardEncodingUsesPadding() {
        assertEquals("5L2g5aW9", Base64Tool.encode("你好", urlSafe = false))
        assertEquals("SGVsbG8sIENsaXBib3g=", Base64Tool.encode("Hello, Clipbox", urlSafe = false))
        assertEquals("8J+YgA==", Base64Tool.encode("😀", urlSafe = false))
    }

    @Test
    fun urlSafeEncodingDropsPaddingAndSwapsAlphabet() {
        assertEquals("w7_Dvw", Base64Tool.encode("ÿÿ", urlSafe = true))
        assertEquals("w7/Dvw==", Base64Tool.encode("ÿÿ", urlSafe = false))
        assertEquals("5L2g5aW9", Base64Tool.encode("你好", urlSafe = true))
    }

    @Test
    fun emptyStringRoundTrips() {
        assertEquals("", Base64Tool.encode("", urlSafe = false))
        assertEquals(Base64Result.Success(""), Base64Tool.decode(""))
    }

    @Test
    fun decodeAcceptsWhitespaceAndMissingPadding() {
        assertEquals(Base64Result.Success("你好"), Base64Tool.decode("5L2g\n5aW9"))
        assertEquals(Base64Result.Success("Hello, Clipbox"), Base64Tool.decode(" SGVsbG8sIENsaXBib3g "))
        assertEquals(Base64Result.Success("ÿÿ"), Base64Tool.decode("w7_Dvw"))
    }

    @Test
    fun roundTripChineseAndEmoji() {
        val text = "剪贴板 😀 Clipbox"
        listOf(false, true).forEach { urlSafe ->
            val encoded = Base64Tool.encode(text, urlSafe)
            assertEquals(Base64Result.Success(text), Base64Tool.decode(encoded))
        }
    }

    @Test
    fun illegalCharacterIsRejected() {
        assertIs<Base64Result.Invalid>(Base64Tool.decode("abc\$"))
        assertIs<Base64Result.Invalid>(Base64Tool.decode("你好"))
    }

    @Test
    fun invalidLengthIsRejected() {
        assertIs<Base64Result.Invalid>(Base64Tool.decode("abcde"))
        assertIs<Base64Result.Invalid>(Base64Tool.decode("ab==="))
    }
}
