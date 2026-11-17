/**
 * author : 王星星
 * date : 2026-11-17 16:23
 * email : 1099420259@qq.com
 * description : URL 编解码纯逻辑：组件级编码全部保留字符，整串级保留 RFC 3986 结构字符；
 *               解码严格校验百分号序列并返回 1-based 位置，`+` 空格开关只改变空格映射；
 *               注意：百分号字节若不能组成合法 UTF-8，按替换字符输出，不视为百分号错误
 */
package com.wangxingxing.clipbox.toolbox

sealed interface UrlResult {
    data class Success(val text: String) : UrlResult

    /** position 为 1-based 字符位置，指向非法百分号序列的 `%` */
    data class InvalidPercent(val position: Int) : UrlResult
}

object UrlTool {

    private const val UNRESERVED =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.~"
    private const val RESERVED = ":/?#[]@!$&'()*+,;="
    private const val HEX = "0123456789ABCDEF"

    fun encode(text: String, wholeUrl: Boolean, plusAsSpace: Boolean): String {
        val kept = if (wholeUrl) RESERVED else ""
        return buildString {
            for (byte in text.encodeToByteArray()) {
                val value = byte.toInt() and 0xFF
                val char = value.toChar()
                when {
                    value == 0x20 -> append(if (plusAsSpace) '+' else "%20")
                    value < 0x80 &&
                        (UNRESERVED.indexOf(char) >= 0 || kept.indexOf(char) >= 0) -> append(char)
                    else -> {
                        append('%')
                        append(HEX[value shr 4])
                        append(HEX[value and 0x0F])
                    }
                }
            }
        }
    }

    fun decode(input: String, plusAsSpace: Boolean): UrlResult {
        // 每个 Char 最多编码为 3 字节，百分号序列反而收缩，按该上限一次性分配
        val bytes = ByteArray(input.length * 3)
        var written = 0
        var index = 0
        while (index < input.length) {
            val char = input[index]
            when {
                char == '%' -> {
                    if (index + 2 >= input.length) return UrlResult.InvalidPercent(index + 1)
                    val high = hexValue(input[index + 1])
                    val low = hexValue(input[index + 2])
                    if (high < 0 || low < 0) return UrlResult.InvalidPercent(index + 1)
                    bytes[written++] = ((high shl 4) or low).toByte()
                    index += 3
                }
                char == '+' && plusAsSpace -> {
                    bytes[written++] = 0x20.toByte()
                    index += 1
                }
                char.code < 0x80 -> {
                    bytes[written++] = char.code.toByte()
                    index += 1
                }
                else -> {
                    val code = char.code
                    if (code in 0xD800..0xDBFF && index + 1 < input.length) {
                        val low = input[index + 1].code
                        if (low in 0xDC00..0xDFFF) {
                            written = writeCodePoint(
                                bytes,
                                written,
                                0x10000 + ((code - 0xD800) shl 10) + (low - 0xDC00),
                            )
                            index += 2
                        } else {
                            written = writeCodePoint(bytes, written, 0xFFFD)
                            index += 1
                        }
                    } else {
                        val codePoint = if (code in 0xDC00..0xDFFF) 0xFFFD else code
                        written = writeCodePoint(bytes, written, codePoint)
                        index += 1
                    }
                }
            }
        }
        return UrlResult.Success(bytes.copyOf(written).decodeToString())
    }

    private fun hexValue(char: Char): Int = when (char) {
        in '0'..'9' -> char - '0'
        in 'a'..'f' -> char - 'a' + 10
        in 'A'..'F' -> char - 'A' + 10
        else -> -1
    }

    private fun writeCodePoint(bytes: ByteArray, offset: Int, codePoint: Int): Int {
        var written = offset
        when {
            codePoint < 0x80 -> bytes[written++] = codePoint.toByte()
            codePoint < 0x800 -> {
                bytes[written++] = (0xC0 or (codePoint shr 6)).toByte()
                bytes[written++] = (0x80 or (codePoint and 0x3F)).toByte()
            }
            codePoint < 0x10000 -> {
                bytes[written++] = (0xE0 or (codePoint shr 12)).toByte()
                bytes[written++] = (0x80 or ((codePoint shr 6) and 0x3F)).toByte()
                bytes[written++] = (0x80 or (codePoint and 0x3F)).toByte()
            }
            else -> {
                bytes[written++] = (0xF0 or (codePoint shr 18)).toByte()
                bytes[written++] = (0x80 or ((codePoint shr 12) and 0x3F)).toByte()
                bytes[written++] = (0x80 or ((codePoint shr 6) and 0x3F)).toByte()
                bytes[written++] = (0x80 or (codePoint and 0x3F)).toByte()
            }
        }
        return written
    }
}
