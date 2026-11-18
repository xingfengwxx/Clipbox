/**
 * author : 王星星
 * date : 2026-11-16 15:21
 * email : 1099420259@qq.com
 * description : 设备认证测试：缺失/格式错误/未知令牌统一 401、合法令牌放行、last_seen_at 五分钟节流、响应不泄露令牌与哈希。
 *               注意：断言信息中不得出现令牌明文或哈希。
 */
package com.wangxingxing.clipbox.server

import com.wangxingxing.clipbox.server.config.AppConfig
import com.wangxingxing.clipbox.server.db.ClipRepository
import com.wangxingxing.clipbox.server.db.ClipRow
import com.wangxingxing.clipbox.server.db.DatabaseFactory
import com.wangxingxing.clipbox.server.db.DeviceRepository
import com.wangxingxing.clipbox.server.db.DeviceRow
import com.wangxingxing.clipbox.server.db.MetaRepository
import com.wangxingxing.clipbox.server.util.Hashing
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.io.TempDir

class DeviceAuthTest {

    @TempDir
    lateinit var tempDir: Path

    private val deviceId = "01J8Y6Z3K4M5N6P7Q8R9S0T1V2"
    private val deviceToken = "d".repeat(43)
    private val unknownToken = "u".repeat(43)

    @Test
    fun `missing token is rejected with the contract error body`() = withApp { _, client ->
        val response = client.get(CLIPS)
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("UNAUTHORIZED", body.getValue("error").jsonObject.getValue("code").jsonPrimitive.content)
        assertTrue(body.getValue("error").jsonObject.getValue("message").jsonPrimitive.content.isNotBlank())
    }

    @Test
    fun `malformed scheme and unknown token are rejected the same way`() = withApp { _, client ->
        val requests = listOf(
            HttpHeaders.Authorization to "Basic $deviceToken",
            HttpHeaders.Authorization to "Bearer",
            HttpHeaders.Authorization to "Bearer $unknownToken",
            HttpHeaders.Authorization to "Bearer   ",
        )
        requests.forEach { (name, value) ->
            val response = client.get(CLIPS) { header(name, value) }
            assertEquals(HttpStatusCode.Unauthorized, response.status, value.takeWhile { it != ' ' })
            assertEquals("UNAUTHORIZED", errorCode(response.bodyAsText()))
        }
    }

    @Test
    fun `valid token returns the page without leaking token material`() = withApp(
        seed = { factory ->
            seedDevice(factory, lastSeenAt = 1_000)
            factory.writeTransaction {
                ClipRepository.insert(
                    ClipRow(
                        id = "CLIP1",
                        content = "hello",
                        sourceDeviceId = deviceId,
                        pinned = false,
                        createdAt = 1_000,
                        deletedAt = null,
                        serverSeq = 1,
                    ),
                )
                MetaRepository.put(MetaRepository.SERVER_SEQ_KEY, "1")
            }
        },
    ) { _, client ->
        val response = client.authorizedGet()
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertFalse(body.contains(deviceToken), "响应不得包含设备令牌")
        assertFalse(body.contains(Hashing.sha256Hex(deviceToken)), "响应不得包含令牌哈希")
        assertFalse(body.contains("token_hash"), "响应不得暴露字段名 token_hash")
        assertFalse(body.contains(unknownToken), "响应不得包含其他令牌")
    }

    @Test
    fun `first authentication records last seen`() = withApp(
        seed = { factory -> seedDevice(factory, lastSeenAt = null) },
    ) { factory, client ->
        assertEquals(HttpStatusCode.OK, client.authorizedGet().status)
        val lastSeen = lastSeenAt(factory)
        assertNotNull(lastSeen)
        assertTrue(lastSeen > 0)
    }

    @Test
    fun `fresh last seen is not rewritten within five minutes`() = withApp(
        seed = { factory -> seedDevice(factory, lastSeenAt = System.currentTimeMillis()) },
    ) { factory, client ->
        val seeded = lastSeenAt(factory)
        assertEquals(HttpStatusCode.OK, client.authorizedGet().status)
        assertEquals(HttpStatusCode.OK, client.authorizedGet().status)
        assertEquals(seeded, lastSeenAt(factory), "五分钟窗口内不得重复写 last_seen_at")
    }

    @Test
    fun `stale last seen is refreshed`() = withApp(
        seed = { factory -> seedDevice(factory, lastSeenAt = System.currentTimeMillis() - 6 * 60 * 1000L) },
    ) { factory, client ->
        val stale = lastSeenAt(factory)!!
        val before = System.currentTimeMillis()
        assertEquals(HttpStatusCode.OK, client.authorizedGet().status)
        val refreshed = lastSeenAt(factory)!!
        assertTrue(refreshed >= before, "超过五分钟应刷新 last_seen_at")
        assertTrue(refreshed > stale)
    }

    // --- 夹具 ---

    private suspend fun seedDevice(factory: DatabaseFactory, lastSeenAt: Long?) {
        factory.writeTransaction {
            DeviceRepository.insert(
                DeviceRow(
                    id = deviceId,
                    name = "Pixel 8",
                    tokenHash = Hashing.sha256Hex(deviceToken),
                    createdAt = 1_000,
                    lastSeenAt = lastSeenAt,
                ),
            )
        }
    }

    private suspend fun lastSeenAt(factory: DatabaseFactory): Long? =
        factory.readTransaction { DeviceRepository.findById(deviceId) }?.lastSeenAt

    private fun withApp(
        seed: suspend (DatabaseFactory) -> Unit = {},
        block: suspend (DatabaseFactory, HttpClient) -> Unit,
    ) = testApplication {
        val factory = DatabaseFactory.open(tempDir)
        factory.migrate()
        seed(factory)
        application { module(testConfig(), factory) }
        client.get("/healthz")
        block(factory, client)
    }

    private fun testConfig() = AppConfig(
        port = 8080,
        dataDir = tempDir,
        publicBaseUrl = "http://127.0.0.1:8080",
    )

    private suspend fun HttpClient.authorizedGet(): HttpResponse =
        get(CLIPS) { header(HttpHeaders.Authorization, "Bearer $deviceToken") }

    private fun errorCode(body: String): String =
        Json.parseToJsonElement(body).jsonObject["error"]!!.jsonObject["code"]!!.jsonPrimitive.content

    companion object {
        private const val CLIPS = "/api/v1/clips"
    }
}
