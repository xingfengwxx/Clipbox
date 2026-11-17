/**
 * author : 王星星
 * date : 2026-11-17 16:24
 * email : 1099420259@qq.com
 * description : Base64 工具纯逻辑：自实现字母表以保证三端一致；编码 UTF-8，标准带填充、URL-safe 去填充；
 *               解码同时容忍两种字母表、空白与缺失填充，非法字符或长度错误返回统一失败，不静默跳过
 */
package com.wangxingxing.clipbox.toolbox

sealed interface Base64Result {
    data class Success(val text: String) : Base64Result
    data object Invalid : Base64Result
}

object Base64Tool {

    private const val STANDARD_ALPHABET =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private const val URL_SAFE_ALPHABET =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    fun encode(text: String, urlSafe: Boolean): String {
        val alphabet = if (urlSafe) URL_SAFE_ALPHABET else STANDARD_ALPHABET
        val bytes = text.encodeToByteArray()
        return buildString {
            var index = 0
            while (index < bytes.size) {
                val first = bytes[index].toInt() and 0xFF
                val second = if (index + 1 < bytes.size) bytes[index + 1].toInt() and 0xFF else -1
                val third = if (index + 2 < bytes.size) bytes[index + 2].toInt() and 0xFF else -1
                append(alphabet[first shr 2])
                append(alphabet[((first and 0x03) shl 4) or (if (second >= 0) second shr 4 else 0)])
                if (second >= 0) {
                    append(alphabet[((second and 0x0F) shl 2) or (if (third >= 0) third shr 6 else 0)])
                } else if (!urlSafe) {
                    append('=')
                }
                if (third >= 0) {
                    append(alphabet[third and 0x3F])
                } else if (!urlSafe) {
                    append('=')
                }
                index += 3
            }
        }
    }

    fun decode(input: String): Base64Result {
        val compact = buildString {
            input.forEach { char -> if (!char.isWhitespace()) append(char) }
        }
        if (compact.isEmpty()) return Base64Result.Success("")

        val data = compact.trimEnd('=')
        if (data.isEmpty() || data.contains('=')) return Base64Result.Invalid
        val padding = compact.length - data.length
        val expectedPadding = (4 - data.length % 4) % 4
        if (padding != 0 && padding != expectedPadding) return Base64Result.Invalid
        if (data.length % 4 == 1) return Base64Result.Invalid

        val decoded = ByteArray(data.length * 3 / 4)
        var buffer = 0
        var bits = 0
        var written = 0
        for (char in data) {
            val value = charValue(char) ?: return Base64Result.Invalid
            buffer = (buffer shl 6) or value
            bits += 6
            if (bits >= 8) {
                bits -= 8
                decoded[written++] = ((buffer shr bits) and 0xFF).toByte()
            }
        }
        return Base64Result.Success(decoded.copyOf(written).decodeToString())
    }

    private fun charValue(char: Char): Int? {
        val standard = STANDARD_ALPHABET.indexOf(char)
        if (standard >= 0) return standard
        val urlSafe = URL_SAFE_ALPHABET.indexOf(char)
        return if (urlSafe >= 0) urlSafe else null
    }
}
