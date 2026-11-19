/**
 * author : 王星星
 * date : 2026-11-19 11:08
 * email : 1099420259@qq.com
 * description : 纯 Kotlin 流式 SHA-256；用于安装包完整性校验，不依赖平台摘要实现，便于三端共用与 NIST 向量验证。
 *               注意：读取在 Dispatchers.Default 执行，读取器返回 0 或负数即视为结束，避免零进度死循环
 */
package com.wangxingxing.clipbox.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val BLOCK_SIZE = 64
private const val IO_BUFFER_SIZE = 64 * 1024
private const val HEX_DIGITS = "0123456789abcdef"

private val INITIAL_STATE = intArrayOf(
    0x6a09e667.toInt(), 0xbb67ae85.toInt(), 0x3c6ef372, 0xa54ff53a.toInt(),
    0x510e527f, 0x9b05688c.toInt(), 0x1f83d9ab, 0x5be0cd19,
)

private val ROUND_CONSTANTS = intArrayOf(
    0x428a2f98, 0x71374491, 0xb5c0fbcf.toInt(), 0xe9b5dba5.toInt(),
    0x3956c25b, 0x59f111f1, 0x923f82a4.toInt(), 0xab1c5ed5.toInt(),
    0xd807aa98.toInt(), 0x12835b01, 0x243185be, 0x550c7dc3,
    0x72be5d74, 0x80deb1fe.toInt(), 0x9bdc06a7.toInt(), 0xc19bf174.toInt(),
    0xe49b69c1.toInt(), 0xefbe4786.toInt(), 0x0fc19dc6, 0x240ca1cc,
    0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
    0x983e5152.toInt(), 0xa831c66d.toInt(), 0xb00327c8.toInt(), 0xbf597fc7.toInt(),
    0xc6e00bf3.toInt(), 0xd5a79147.toInt(), 0x06ca6351, 0x14292967,
    0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
    0x650a7354, 0x766a0abb, 0x81c2c92e.toInt(), 0x92722c85.toInt(),
    0xa2bfe8a1.toInt(), 0xa81a664b.toInt(), 0xc24b8b70.toInt(), 0xc76c51a3.toInt(),
    0xd192e819.toInt(), 0xd6990624.toInt(), 0xf40e3585.toInt(), 0x106aa070,
    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5,
    0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
    0x748f82ee, 0x78a5636f, 0x84c87814.toInt(), 0x8cc70208.toInt(),
    0x90befffa.toInt(), 0xa4506ceb.toInt(), 0xbef9a3f7.toInt(), 0xc67178f2.toInt(),
)

/**
 * 流式计算 SHA-256 并返回 64 位小写十六进制。
 * [readChunk] 每次把下一段字节从缓冲区偏移 0 写入并返回写入长度；返回 0 或负数表示结束。
 * 读取异常原样抛给调用方，由安装流程映射为失败。
 */
suspend fun sha256Hex(readChunk: suspend (ByteArray) -> Int): String = withContext(Dispatchers.Default) {
    val digest = Sha256Digest()
    val buffer = ByteArray(IO_BUFFER_SIZE)
    while (true) {
        val read = readChunk(buffer)
        if (read <= 0) break
        digest.update(buffer, 0, read)
    }
    digest.finishHex()
}

private class Sha256Digest {

    private val state = INITIAL_STATE.copyOf()
    private val block = ByteArray(BLOCK_SIZE)
    private val schedule = IntArray(64)
    private var blockLength = 0
    private var totalBytes = 0L

    fun update(data: ByteArray, offset: Int, length: Int) {
        totalBytes += length
        var position = offset
        var remaining = length

        if (blockLength > 0) {
            val take = minOf(remaining, BLOCK_SIZE - blockLength)
            data.copyInto(block, blockLength, position, position + take)
            blockLength += take
            position += take
            remaining -= take
            if (blockLength == BLOCK_SIZE) {
                compress()
                blockLength = 0
            }
        }

        while (remaining >= BLOCK_SIZE) {
            data.copyInto(block, 0, position, position + BLOCK_SIZE)
            compress()
            position += BLOCK_SIZE
            remaining -= BLOCK_SIZE
        }

        if (remaining > 0) {
            data.copyInto(block, 0, position, position + remaining)
            blockLength = remaining
        }
    }

    fun finishHex(): String {
        // 长度以 64 位大端位计数写入尾部；补位后若跨块，先压入当前块再继续
        val bitLength = totalBytes * 8
        var index = blockLength
        block[index++] = 0x80.toByte()
        if (index > BLOCK_SIZE - 8) {
            while (index < BLOCK_SIZE) block[index++] = 0
            compress()
            index = 0
        }
        while (index < BLOCK_SIZE - 8) block[index++] = 0
        for (shift in 7 downTo 0) {
            block[index++] = (bitLength ushr (shift * 8)).toByte()
        }
        compress()

        val hex = CharArray(64)
        for (wordIndex in 0 until 8) {
            val word = state[wordIndex]
            for (byteIndex in 0 until 4) {
                val value = (word ushr (24 - byteIndex * 8)) and 0xFF
                val charIndex = wordIndex * 8 + byteIndex * 2
                hex[charIndex] = HEX_DIGITS[value ushr 4]
                hex[charIndex + 1] = HEX_DIGITS[value and 0x0F]
            }
        }
        return hex.concatToString()
    }

    private fun compress() {
        for (i in 0 until 16) {
            val offset = i * 4
            schedule[i] = ((block[offset].toInt() and 0xFF) shl 24) or
                ((block[offset + 1].toInt() and 0xFF) shl 16) or
                ((block[offset + 2].toInt() and 0xFF) shl 8) or
                (block[offset + 3].toInt() and 0xFF)
        }
        for (i in 16 until 64) {
            val previous15 = schedule[i - 15]
            val previous2 = schedule[i - 2]
            val small0 = previous15.rotateRight(7) xor previous15.rotateRight(18) xor (previous15 ushr 3)
            val small1 = previous2.rotateRight(17) xor previous2.rotateRight(19) xor (previous2 ushr 10)
            schedule[i] = schedule[i - 16] + small0 + schedule[i - 7] + small1
        }

        var a = state[0]
        var b = state[1]
        var c = state[2]
        var d = state[3]
        var e = state[4]
        var f = state[5]
        var g = state[6]
        var h = state[7]

        for (i in 0 until 64) {
            val big1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
            val choose = (e and f) xor (e.inv() and g)
            val temp1 = h + big1 + choose + ROUND_CONSTANTS[i] + schedule[i]
            val big0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
            val majority = (a and b) xor (a and c) xor (b and c)
            val temp2 = big0 + majority

            h = g
            g = f
            f = e
            e = d + temp1
            d = c
            c = b
            b = a
            a = temp1 + temp2
        }

        state[0] += a
        state[1] += b
        state[2] += c
        state[3] += d
        state[4] += e
        state[5] += f
        state[6] += g
        state[7] += h
    }
}

private fun Int.rotateRight(bits: Int): Int = (this ushr bits) or (this shl (32 - bits))
