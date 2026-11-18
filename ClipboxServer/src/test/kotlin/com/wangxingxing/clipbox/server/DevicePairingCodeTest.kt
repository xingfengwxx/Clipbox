/**
 * author : 王星星
 * date : 2026-11-18 16:10
 * email : 1099420259@qq.com
 * description : 设备自有配对码接口测试：认证失败 401、6 位码与 10 分钟 TTL、只存哈希、重新生成立即作废旧码、
 *               过期码拒绝新设备、10 次/分钟 IP 限流。
 *               注意：明文码只存在于响应与断言内存中，不得写入日志；每个用例使用独立临时目录与真实 SQLite。
 */
package com.wangxingxing.clipbox.server

import com.wangxingxing.clipbox.server.config.AppConfig
import com.wangxingxing.clipbox.server.db.DatabaseFactory
import com.wangxingxing.clipbox.server.db.DevicePairingCodesTable
import com.wangxingxing.clipbox.server.db.DeviceRepository
import com.wangxingxing.clipbox.server.db.DeviceRow
import com.wangxingxing.clipbox.server.db.DevicesTable
import com.wangxingxing.clipbox.server.db.PairingRepository
import com.wangxingxing.clipbox.server.plugins.RateLimitSettings
import com.wangxingxing.clipbox.server.util.Hashing
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
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
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.junit.jupiter.api.io.TempDir

class DevicePairingCodeTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `pairing code generation requires a valid device token`() = withApp { _, client ->
        val missing = client.post(PAIRING_CODE_PATH)
        assertEquals(HttpStatusCode.Unauthorized, missing.status)
        assertEquals("UNAUTHORIZED", errorCode(missing.bodyAsText()))

        val unknown = client.post(PAIRING_CODE_PATH) {
            header(HttpHeaders.Authorization, "Bearer ${"d".repeat(43)}")
        }
        assertEquals(HttpStatusCode.Unauthorized, unknown.status)
        assertEquals("UNAUTHORIZED", errorCode(unknown.bodyAsText()))

        val basic = client.post(PAIRING_CODE_PATH) {
            header(HttpHeaders.Authorization, "Basic abc")
        }
        assertEquals(HttpStatusCode.Unauthorized, basic.status)
        assertEquals("UNAUTHORIZED", errorCode(basic.bodyAsText()))
    }

    @Test
    fun `code is six digits valid for ten minutes and stored only as hash`() = withApp { factory, client ->
        val device = seedDevice(factory)
        val before = System.currentTimeMillis()
        val response = client.createCode(device.token)
        val after = System.currentTimeMillis()

        assertEquals(HttpStatusCode.Created, response.status)
        val body = json(response.bodyAsText())
        assertEquals(setOf("code", "expiresAt"), body.keys)
        val code = body.str("code")
        assertTrue(SIX_DIGITS.matches(code), "code=$code")
        assertTrue(
            body.long("expiresAt") in (before + 600_000)..(after + 600_000),
            "expiresAt=${body.long("expiresAt")}",
        )

        val rows = factory.readTransaction {
            DevicePairingCodesTable.selectAll().map { row ->
                listOf(
                    row[DevicePairingCodesTable.deviceId],
                    row[DevicePairingCodesTable.codeHash],
                )
            }
        }
        assertEquals(listOf(listOf(device.deviceId, Hashing.sha256Hex(code))), rows, "库中只能有哈希与设备 ID")
        assertTrue(rows.flatten().none { it == code }, "库中不得出现明文配对码")
        val stored = assertNotNull(
            factory.readTransaction { PairingRepository.findByCodeHash(Hashing.sha256Hex(code)) },
        )
        assertEquals(device.deviceId, stored.deviceId)
        assertNull(stored.usedAt)
    }

    @Test
    fun `regenerating invalidates the previous code immediately`() = withApp { factory, client ->
        val device = seedDevice(factory)
        val first = json(client.createCode(device.token).bodyAsText()).str("code")
        val second = json(client.createCode(device.token).bodyAsText()).str("code")
        // 旧码仍在同一行的哈希配对检查中，生成器不会重复给出同一个码。
        assertNotEquals(first, second)

        val rowCount = factory.readTransaction { DevicePairingCodesTable.selectAll().count() }
        assertEquals(1L, rowCount, "同一设备只保留一行有效码")

        val oldCode = client.pair(first, "New Device")
        assertEquals(HttpStatusCode.BadRequest, oldCode.status)
        assertEquals("INVALID_CODE", errorCode(oldCode.bodyAsText()), "换码后旧码必须立即失效")

        assertEquals(HttpStatusCode.Created, client.pair(second, "New Device").status)
    }

    @Test
    fun `regenerating after use clears the used mark`() = withApp { factory, client ->
        val device = seedDevice(factory)
        val first = json(client.createCode(device.token).bodyAsText()).str("code")
        assertEquals(HttpStatusCode.Created, client.pair(first, "New Device").status)
        assertNotNull(
            factory.readTransaction { PairingRepository.findByCodeHash(Hashing.sha256Hex(first)) }?.usedAt,
        )

        val second = json(client.createCode(device.token).bodyAsText()).str("code")
        val refreshed = assertNotNull(
            factory.readTransaction { PairingRepository.findByCodeHash(Hashing.sha256Hex(second)) },
        )
        assertNull(refreshed.usedAt, "重新生成必须重置使用状态")
        assertEquals(HttpStatusCode.Created, client.pair(second, "Third Device").status)
        assertEquals(3L, deviceCount(factory), "锚设备 + 两台新设备")
    }

    @Test
    fun `expired code is rejected for new devices`() = withApp { factory, client ->
        val device = seedDevice(factory)
        factory.writeTransaction {
            PairingRepository.upsertCode(
                deviceId = device.deviceId,
                codeHash = Hashing.sha256Hex("111111"),
                createdAt = 1_000,
                expiresAt = System.currentTimeMillis() - 1,
            )
        }

        val response = client.pair("111111", "New Device")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("CODE_EXPIRED", errorCode(response.bodyAsText()))
        assertEquals(1L, deviceCount(factory))
    }

    @Test
    fun `generation stops after ten attempts from the same ip`() = withApp { factory, client ->
        val device = seedDevice(factory)
        repeat(10) { attempt ->
            assertEquals(
                HttpStatusCode.Created,
                client.createCode(device.token).status,
                "第 ${attempt + 1} 次应放行",
            )
        }

        val limited = client.createCode(device.token)
        assertEquals(HttpStatusCode.TooManyRequests, limited.status)
        assertEquals("RATE_LIMITED", errorCode(limited.bodyAsText()))
        assertRetryAfter(limited)
    }

    // --- 夹具 ---

    private data class SeededDevice(val deviceId: String, val token: String)

    private fun withApp(
        // 默认额度保持产品值，限流用例依赖 10 次/分钟；其余用例的请求数远低于额度。
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

    private suspend fun seedDevice(factory: DatabaseFactory): SeededDevice {
        val token = Hashing.randomToken()
        val deviceId = "01J8Y6Z3K4M5N6P7Q8R9S0T1V2"
        factory.writeTransaction {
            DeviceRepository.insert(
                DeviceRow(deviceId, "Host", Hashing.sha256Hex(token), 1_000, 1_000),
            )
        }
        return SeededDevice(deviceId, token)
    }

    private suspend fun HttpClient.createCode(token: String): HttpResponse =
        post(PAIRING_CODE_PATH) {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

    private suspend fun HttpClient.pair(code: String, deviceName: String): HttpResponse =
        post("/api/v1/pair") {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("code", code); put("deviceName", deviceName) }.toString())
        }

    private suspend fun deviceCount(factory: DatabaseFactory): Long =
        factory.readTransaction { DevicesTable.selectAll().count() }

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

    private fun kotlinx.serialization.json.JsonObject.long(key: String) =
        getValue(key).jsonPrimitive.content.toLong()

    private fun errorCode(body: String): String =
        json(body)["error"]!!.jsonObject["code"]!!.jsonPrimitive.content

    companion object {
        private const val PAIRING_CODE_PATH = "/api/v1/devices/me/pairing-code"
        private val SIX_DIGITS = Regex("^\\d{6}$")
    }
}
