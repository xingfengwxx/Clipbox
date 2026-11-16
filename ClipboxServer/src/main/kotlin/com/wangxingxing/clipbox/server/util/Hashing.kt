/**
 * author : 王星星
 * date : 2026-11-16 14:56
 * email : 1099420259@qq.com
 * description : 令牌生成与摘要工具；设备令牌/分享令牌为 32 字节 SecureRandom 的 Base64URL 无填充编码。
 *               注意：服务端只持久化 SHA-256 十六进制摘要，明文令牌只在签发响应中出现一次。
 */
package com.wangxingxing.clipbox.server.util

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object Hashing {
    private const val TOKEN_BYTES = 32
    private val HEX = "0123456789abcdef".toCharArray()
    private val random = SecureRandom()

    /** 生成 43 字符的设备或分享令牌。 */
    fun randomToken(): String {
        val bytes = ByteArray(TOKEN_BYTES).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /** 计算 SHA-256 摘要的 64 字符小写十六进制表示。 */
    fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
        val hex = CharArray(digest.size * 2)
        for (i in digest.indices) {
            val byte = digest[i].toInt() and 0xFF
            hex[i * 2] = HEX[byte ushr 4]
            hex[i * 2 + 1] = HEX[byte and 0x0F]
        }
        return String(hex)
    }
}
