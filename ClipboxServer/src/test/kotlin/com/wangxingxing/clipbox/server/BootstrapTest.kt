/**
 * author : 王星星
 * date : 2026-11-18 16:05
 * email : 1099420259@qq.com
 * description : 空库初始化接口测试：首台设备 201 并签发令牌、已有设备 409 ALREADY_INITIALIZED、并发只成功一次、
 *               设备名校验、畸形 JSON 与 3 次/分钟 IP 限流。
 *               注意：并发用例显式放宽限流，避免 429 干扰“只有一个成功”的断言；令牌断言只比对哈希。
 */
package com.wangxingxing.clipbox.server

import com.wangxingxing.clipbox.server.config.AppConfig
import com.wangxingxing.clipbox.server.db.DatabaseFactory
import com.wangxingxing.clipbox.server.db.DeviceRepository
import com.wangxingxing.clipbox.server.db.DeviceRow
import com.wangxingxing.clipbox.server.db.DevicesTable
import com.wangxingxing.clipbox.server.plugins.RateLimitSettings
import com.wangxingxing.clipbox.server.util.Hashing
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.junit.jupiter.api.io.TempDir

class BootstrapTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `bootstrap creates the first device when the database is empty`() = withApp { factory, client ->
        val before = System.currentTimeMillis()
        val response = client.bootstrap("  MacBook  ")
        val after = System.currentTimeMillis()

        assertEquals(HttpStatusCode.Created, response.status)
        val body = json(response.bodyAsText())
        assertEquals(setOf("deviceId", "token", "deviceName"), body.keys)
        assertTrue(ULID.matches(body.str("deviceId")), "deviceId=${body.str("deviceId")}")
        assertEquals("MacBook", body.str("deviceName"))
        assertEquals(43, body.str("token").length)
        assertTrue(BASE64URL_43.matches(body.str("token")))

        val device = assertNotNull(
            factory.readTransaction { DeviceRepository.findById(body.str("deviceId")) },
        )
        assertEquals("MacBook", device.name)
        assertEquals(Hashing.sha256Hex(body.str("token")), device.tokenHash)
        assertTrue(device.createdAt in before..after, "创建时间应取服务端当前时间")

        val storedHashes = factory.readTransaction { DevicesTable.selectAll().map { it[DevicesTable.tokenHash] } }
        assertEquals(listOf(Hashing.sha256Hex(body.str("token"))), storedHashes)
    }

    @Test
    fun `bootstrap returns conflict once a device exists`() = withApp { factory, client ->
        assertEquals(HttpStatusCode.Created, client.bootstrap("First").status)

        val second = client.bootstrap("Second")
        assertEquals(HttpStatusCode.Conflict, second.status)
        assertEquals("ALREADY_INITIALIZED", errorCode(second.bodyAsText()))
        assertEquals(1L, deviceCount(factory), "已初始化后不得再创建设备")
    }

    @Test
    fun `bootstrap returns conflict when devices already exist`() = withApp { factory, client ->
        factory.writeTransaction {
            DeviceRepository.insert(
                DeviceRow("01J8Y6Z3K4M5N6P7Q8R9S0T1V2", "Existing", Hashing.sha256Hex("e".repeat(43)), 1_000, 1_000),
            )
        }

        val response = client.bootstrap("Late")
        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals("ALREADY_INITIALIZED", errorCode(response.bodyAsText()))
        assertEquals(1L, deviceCount(factory))
    }

    @Test
    fun `concurrent bootstrap attempts create exactly one device`() =
        withApp(RateLimitSettings(bootstrapPerMinute = 1_000)) { factory, client ->
            val responses = coroutineScope {
                (1..8).map { index ->
                    async { client.bootstrap("Device $index") }
                }.awaitAll()
            }

            assertEquals(1, responses.count { it.status == HttpStatusCode.Created }, "只能有一个初始化成功")
            assertEquals(7, responses.count { it.status == HttpStatusCode.Conflict })
            assertEquals(1L, deviceCount(factory))
        }

    @Test
    fun `bootstrap validates the device name`() =
        withApp(RateLimitSettings(bootstrapPerMinute = 100)) { factory, client ->
            assertValidationError(client.bootstrap("   "))
            assertValidationError(client.bootstrap("x".repeat(65)))
            assertValidationError(client.bootstrap(""))

            val malformed = client.post("/api/v1/bootstrap") {
                contentType(ContentType.Application.Json)
                setBody("{oops")
            }
            assertValidationError(malformed)

            val missing = client.post("/api/v1/bootstrap") {
                contentType(ContentType.Application.Json)
                setBody("""{"futureField":"ignored"}""")
            }
            assertValidationError(missing)
            assertEquals(0L, deviceCount(factory), "校验失败不得占用空库初始化名额")

            assertEquals(HttpStatusCode.Created, client.bootstrap("Pixel 8").status)
        }

    @Test
    fun `bootstrap stops after three attempts from the same ip`() = withApp { _, client ->
        assertEquals(HttpStatusCode.Created, client.bootstrap("First").status)
        assertEquals(HttpStatusCode.Conflict, client.bootstrap("Second").status)
        assertEquals(HttpStatusCode.Conflict, client.bootstrap("Third").status)

        val limited = client.bootstrap("Fourth")
        assertEquals(HttpStatusCode.TooManyRequests, limited.status)
        assertEquals("RATE_LIMITED", errorCode(limited.bodyAsText()))
        assertRetryAfter(limited)
    }

    // --- 夹具 ---

    private fun withApp(
        settings: RateLimitSettings = RateLimitSettings(),
        block: suspend (DatabaseFactory, HttpClient) -> Unit,
    ) = testApplication {
        val factory = DatabaseFactory.open(tempDir)
        factory.migrate()
        application { module(testConfig(), factory, rateLimitSettings = settings) }
        client.get("/healthz")
        block(factory, client)
    }

    private fun testConfig() = AppConfig(
        port = 8080,
        dataDir = tempDir,
        publicBaseUrl = "http://127.0.0.1:8080",
    )

    private suspend fun HttpClient.bootstrap(name: String): HttpResponse =
        post("/api/v1/bootstrap") {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("deviceName", name) }.toString())
        }

    private suspend fun deviceCount(factory: DatabaseFactory): Long =
        factory.readTransaction { DevicesTable.selectAll().count() }

    private suspend fun assertValidationError(response: HttpResponse) {
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("VALIDATION_ERROR", errorCode(response.bodyAsText()))
    }

    private fun assertRetryAfter(response: HttpResponse) {
        val raw = response.headers[HttpHeaders.RetryAfter]
        assertNotNull(raw, "429 必须带 Retry-After")
        val seconds = raw.toLongOrNull()
        assertNotNull(seconds, "Retry-After 必须是秒数：$raw")
        assertTrue(seconds in 1..60, "Retry-After 超出窗口范围：$seconds")
    }

    private fun json(raw: String) = Json.parseToJsonElement(raw).jsonObject

    private fun kotlinx.serialization.json.JsonObject.str(key: String) =
        getValue(key).jsonPrimitive.content

    private fun errorCode(body: String): String =
        json(body)["error"]!!.jsonObject["code"]!!.jsonPrimitive.content

    companion object {
        private val ULID = Regex("^[0-9A-HJKMNP-TV-Z]{26}$")
        private val BASE64URL_43 = Regex("^[A-Za-z0-9_-]{43}$")
    }
}
