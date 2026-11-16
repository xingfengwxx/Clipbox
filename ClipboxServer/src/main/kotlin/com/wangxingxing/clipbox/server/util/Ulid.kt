/**
 * author : 王星星
 * date : 2026-11-16 14:56
 * email : 1099420259@qq.com
 * description : 服务端 ULID 生成器，用于设备 ID；48 bit 时间戳 + 80 bit 随机数，Crockford Base32 大写 26 字符。
 *               注意：时间戳只保证大致有序，设备 ID 的唯一性来自随机段，不依赖单调计数。
 */
package com.wangxingxing.clipbox.server.util

import java.math.BigInteger
import java.security.SecureRandom

object Ulid {
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private const val TIMESTAMP_BYTES = 6
    private const val RANDOM_BYTES = 10
    private const val LENGTH = 26

    private val random = SecureRandom()

    fun generate(nowMillis: Long = System.currentTimeMillis()): String {
        val bytes = ByteArray(TIMESTAMP_BYTES + RANDOM_BYTES)
        // 时间戳按 48 bit 大端写入，超出 48 bit 的高位被丢弃（当前毫秒时间戳远未接近上限）。
        val timestamp = nowMillis and 0xFFFF_FFFF_FFFFL
        for (i in 0 until TIMESTAMP_BYTES) {
            bytes[i] = (timestamp ushr (8 * (TIMESTAMP_BYTES - 1 - i))).toByte()
        }
        val randomBytes = ByteArray(RANDOM_BYTES).also(random::nextBytes)
        randomBytes.copyInto(bytes, TIMESTAMP_BYTES)

        // 128 bit 编码为 26 个 Base32 字符：首字符只承载高 3 bit，因此结果首位不会超过 '7'。
        var value = BigInteger(1, bytes)
        val chars = CharArray(LENGTH)
        for (i in LENGTH - 1 downTo 0) {
            chars[i] = ALPHABET[value.and(BigInteger.valueOf(31)).toInt()]
            value = value.shiftRight(5)
        }
        return String(chars)
    }
}
