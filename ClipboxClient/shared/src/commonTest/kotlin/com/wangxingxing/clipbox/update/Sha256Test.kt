/**
 * author : 王星星
 * date : 2026-11-19 11:08
 * email : 1099420259@qq.com
 * description : SHA-256 摘要测试；覆盖 NIST 向量、补位边界与分块读取，确保流式实现不依赖一次读完整文件
 */
package com.wangxingxing.clipbox.update

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class Sha256Test {

    private suspend fun sha256Of(bytes: ByteArray, chunkSize: Int): String {
        var offset = 0
        return sha256Hex { buffer ->
            if (offset >= bytes.size) return@sha256Hex -1
            val take = minOf(chunkSize, bytes.size - offset, buffer.size)
            bytes.copyInto(buffer, 0, offset, offset + take)
            offset += take
            take
        }
    }

    @Test
    fun emptyInputMatchesNistVector() = runTest {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            sha256Of(ByteArray(0), chunkSize = 7),
        )
    }

    @Test
    fun abcMatchesNistVectorAcrossSingleByteChunks() = runTest {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256Of("abc".encodeToByteArray(), chunkSize = 1),
        )
    }

    @Test
    fun twoBlockMessageMatchesNistVector() = runTest {
        val message = "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".encodeToByteArray()
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            sha256Of(message, chunkSize = 5),
        )
    }

    @Test
    fun paddingBoundaryLengthsMatchReferenceDigests() = runTest {
        val expected = mapOf(
            55 to "9f4390f8d30c2dd92ec9f095b65e2b9ae9b0a925a5258e241c9f1e910f734318",
            56 to "b35439a4ac6f0948b6d6f9e3c6af0f5f590ce20f1bde7090ef7970686ec6738a",
            64 to "ffe054fe7ae0cb6dc65c3af9b61d5209f439851db43d0ba5997337df154668eb",
        )
        for ((length, digest) in expected) {
            assertEquals(digest, sha256Of(ByteArray(length) { 'a'.code.toByte() }, chunkSize = 13))
        }
    }

    @Test
    fun millionAsMatchNistVector() = runTest {
        assertEquals(
            "cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0",
            sha256Of(ByteArray(1_000_000) { 'a'.code.toByte() }, chunkSize = 997),
        )
    }
}
