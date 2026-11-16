/**
 * author : 王星星
 * date : 2026-11-16 14:01
 * email : 1099420259@qq.com
 * description : ULID 生成与格式校验；同一毫秒内与时钟回拨时都保证严格递增，注意 generate 维护进程内状态，写路径必须串行调用
 */
package com.wangxingxing.clipbox.util

import kotlin.random.Random

object Ulid {
    private const val ENCODED_LENGTH = 26
    private const val RANDOM_BYTES = 10
    private const val MAX_TIMESTAMP = (1L shl 48) - 1
    private const val CROCKFORD_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private val FORMAT_REGEX = Regex("^[0-9A-HJKMNP-TV-Z]{26}$")

    private var lastTimestamp: Long = -1
    private var lastRandom: ByteArray = ByteArray(RANDOM_BYTES)

    fun generate(now: Long): String {
        require(now in 0..MAX_TIMESTAMP) { "timestamp out of ULID range: $now" }

        val timestamp: Long
        val random: ByteArray
        if (now > lastTimestamp) {
            timestamp = now
            random = randomBytes()
        } else {
            // 同一毫秒或时钟回拨：冻结时间并递增随机部分，保证新 ID 仍排在旧 ID 之后
            val nextRandom = incrementRandom(lastRandom)
            if (nextRandom == null) {
                check(lastTimestamp < MAX_TIMESTAMP) { "ULID timestamp exhausted" }
                timestamp = lastTimestamp + 1
                random = randomBytes()
            } else {
                timestamp = lastTimestamp
                random = nextRandom
            }
        }

        lastTimestamp = timestamp
        lastRandom = random
        return encode(timestamp, random)
    }

    fun isValid(value: String): Boolean =
        value.length == ENCODED_LENGTH && FORMAT_REGEX.matches(value)

    private fun randomBytes(): ByteArray =
        Random.Default.nextBytes(ByteArray(RANDOM_BYTES))

    private fun incrementRandom(bytes: ByteArray): ByteArray? {
        val next = bytes.copyOf()
        for (index in next.indices.reversed()) {
            val value = next[index].toInt() and 0xFF
            if (value < 0xFF) {
                next[index] = (value + 1).toByte()
                return next
            }
            next[index] = 0
        }
        return null
    }

    private fun encode(timestamp: Long, random: ByteArray): String {
        val high = (timestamp shl 16) or
            ((random[0].toLong() and 0xFF) shl 8) or
            (random[1].toLong() and 0xFF)
        var low = 0L
        for (index in 2 until RANDOM_BYTES) {
            low = (low shl 8) or (random[index].toLong() and 0xFF)
        }

        val chars = CharArray(ENCODED_LENGTH)
        for (index in chars.indices) {
            val width = if (index == 0) 3 else 5
            val shift = 125 - index * 5
            chars[index] = CROCKFORD_ALPHABET[extractBits(high, low, shift, width)]
        }
        return chars.concatToString()
    }

    // 128 位按 high:low 拆分，取从 shift 开始的 width 位；宽度最多 5，跨 64 位时只拼接低位段
    private fun extractBits(high: Long, low: Long, shift: Int, width: Int): Int {
        val mask = (1L shl width) - 1
        return when {
            shift >= 64 -> ((high ushr (shift - 64)) and mask).toInt()
            shift + width <= 64 -> ((low ushr shift) and mask).toInt()
            else -> {
                val highBits = shift + width - 64
                val lowPart = (low ushr shift) shl highBits
                val highPart = high and ((1L shl highBits) - 1)
                (lowPart or highPart).toInt()
            }
        }
    }
}
