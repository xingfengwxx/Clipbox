/**
 * author : 王星星
 * date : 2026-11-16 14:56
 * email : 1099420259@qq.com
 * description : 配对接口契约测试：用设备生成的 6 位码换令牌、前导零、无效/过期/已用码、设备名校验与令牌只存哈希。
 *               注意：码由设备侧生成，用例直接以哈希形态播种；每个用例使用独立临时数据目录与真实 SQLite。
 */
package com.wangxingxing.clipbox.server

import com.wangxingxing.clipbox.server.config.AppConfig
import com.wangxingxing.clipbox.server.db.DatabaseFactory
import com.wangxingxing.clipbox.server.db.DevicePairingCodesTable
import com.wangxingxing.clipbox.server.db.DeviceRepository
import com.wangxingxing.clipbox.server.db.DeviceRow
import com.wangxingxing.clipbox.server.db.DevicesTable
import com.wangxingxing.clipbox.server.db.PairingRepository
import com.wangxingxing.clipbox.server.routes.PairRequest
import com.wangxingxing.clipbox.server.routes.PairResponse
import com.wangxingxing.clipbox.server.util.Hashing
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.nio.file.Path
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.junit.jupiter.api.io.TempDir

class PairingTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `pairing returns a device token once and stores only its hash`() = withApp { factory, client ->
        val deviceId = seedDevice(factory, "Host")
        seedCode(factory, deviceId, code = "482915", expiresAt = System.currentTimeMillis() + 600_000)

        val response = client.pair("482915", "  Pixel 8  ")
        assertEquals(HttpStatusCode.Created, response.status)
        val body = Json.decodeFromString<PairResponse>(response.bodyAsText())

        assertTrue(ULID.matches(body.deviceId), "deviceId=${body.deviceId}")
        assertEquals("Pixel 8", body.deviceName)
        assertEquals(43, body.token.length)
        assertTrue(BASE64URL_43.matches(body.token), "token=${body.token}")
        assertEquals(32, Base64.getUrlDecoder().decode(body.token).size)

        val device = factory.readTransaction { DeviceRepository.findById(body.deviceId) }
        assertNotNull(device)
        assertEquals("Pixel 8", device.name)
        assertEquals(Hashing.sha256Hex(body.token), device.tokenHash)
        assertNotEquals(body.token, device.tokenHash)
        assertEquals(64, device.tokenHash.length)

        val storedTokenHashes = factory.readTransaction {
            DevicesTable.selectAll().map { it[DevicesTable.tokenHash] }
        }
        assertTrue(storedTokenHashes.none { it == body.token }, "数据库中不得出现明文令牌")

        val storedCodeHashes = factory.readTransaction {
            DevicePairingCodesTable.selectAll().map { it[DevicePairingCodesTable.codeHash] }
        }
        assertEquals(listOf(Hashing.sha256Hex("482915")), storedCodeHashes, "配对码只能以哈希形态落库")
        val codeRow = assertNotNull(factory.readTransaction { PairingRepository.findByCodeHash(Hashing.sha256Hex("482915")) })
        assertEquals(deviceId, codeRow.deviceId, "取码设备与新设备在同一用户空间")
        assertNotNull(codeRow.usedAt)
    }

    @Test
    fun `code with leading zeros can be paired`() = withApp { factory, client ->
        val deviceId = seedDevice(factory, "Host")
        seedCode(factory, deviceId, code = "000123", expiresAt = System.currentTimeMillis() + 600_000)

        val response = client.pair("000123")
        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun `unknown code is rejected as invalid`() = withApp { _, client ->
        val response = client.pair(UNKNOWN_CODE)
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("INVALID_CODE", errorCode(response.bodyAsText()))
    }

    @Test
    fun `expired code is rejected`() = withApp { factory, client ->
        val deviceId = seedDevice(factory, "Host")
        seedCode(factory, deviceId, code = "111111", expiresAt = System.currentTimeMillis() - 1)

        val response = client.pair("111111")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("CODE_EXPIRED", errorCode(response.bodyAsText()))
    }

    @Test
    fun `used code cannot be paired twice`() = withApp { factory, client ->
        val deviceId = seedDevice(factory, "Host")
        val codeHash = seedCode(factory, deviceId, code = "123456", expiresAt = System.currentTimeMillis() + 600_000)
        factory.writeTransaction { PairingRepository.markUsedIfUnused(codeHash, usedAt = System.currentTimeMillis()) }
        val deviceCountBefore = deviceCount(factory)

        val response = client.pair("123456")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("CODE_USED", errorCode(response.bodyAsText()))
        assertEquals(deviceCountBefore, deviceCount(factory), "已使用的码不得再创建设备")
    }

    @Test
    fun `malformed code and invalid device names are validation errors`() = withApp { factory, client ->
        val badCode = client.pair("12345")
        assertEquals(HttpStatusCode.BadRequest, badCode.status)
        assertEquals("VALIDATION_ERROR", errorCode(badCode.bodyAsText()))

        val nonDigits = client.pair("12a456")
        assertEquals(HttpStatusCode.BadRequest, nonDigits.status)
        assertEquals("VALIDATION_ERROR", errorCode(nonDigits.bodyAsText()))

        val blankName = client.pair("123456", "   ")
        assertEquals(HttpStatusCode.BadRequest, blankName.status)
        assertEquals("VALIDATION_ERROR", errorCode(blankName.bodyAsText()))

        val longName = client.pair("123456", "x".repeat(65))
        assertEquals(HttpStatusCode.BadRequest, longName.status)
        assertEquals("VALIDATION_ERROR", errorCode(longName.bodyAsText()))

        assertEquals(0L, deviceCount(factory), "校验失败不得创建设备")
    }

    @Test
    fun `pairing ignores unknown json fields`() = withApp { factory, client ->
        val deviceId = seedDevice(factory, "Host")
        seedCode(factory, deviceId, code = "654321", expiresAt = System.currentTimeMillis() + 600_000)
        val response = client.post("/api/v1/pair") {
            contentType(ContentType.Application.Json)
            setBody("""{"code":"654321","deviceName":"Pixel 8","futureField":"ignored"}""")
        }
        assertEquals(HttpStatusCode.Created, response.status)
    }

    // --- 夹具 ---

    private fun withApp(
        factory: suspend (DatabaseFactory) -> Unit = {},
        block: suspend (DatabaseFactory, HttpClient) -> Unit,
    ) = testApplication {
        val database = DatabaseFactory.open(tempDir)
        database.migrate()
        factory(database)
        application { module(testConfig(), database) }
        client.get("/healthz")
        block(database, client)
    }

    private fun testConfig() = AppConfig(
        port = 8080,
        dataDir = tempDir,
        publicBaseUrl = "http://127.0.0.1:8080",
    )

    private suspend fun seedDevice(factory: DatabaseFactory, name: String): String {
        val deviceId = "01J8Y6Z3K4M5N6P7Q8R9S0T1V${DEVICE_SEED.fetchAndIncrement()}"
        factory.writeTransaction {
            DeviceRepository.insert(
                DeviceRow(deviceId, name, Hashing.sha256Hex(Hashing.randomToken()), 1_000, 1_000),
            )
        }
        return deviceId
    }

    private suspend fun seedCode(
        factory: DatabaseFactory,
        deviceId: String,
        code: String,
        expiresAt: Long,
    ): String {
        val codeHash = Hashing.sha256Hex(code)
        factory.writeTransaction {
            PairingRepository.upsertCode(deviceId, codeHash, createdAt = expiresAt - 600_000, expiresAt = expiresAt)
        }
        return codeHash
    }

    private suspend fun HttpClient.pair(code: String, deviceName: String = "Pixel 8"): HttpResponse =
        post("/api/v1/pair") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(PairRequest(code = code, deviceName = deviceName)))
        }

    private suspend fun deviceCount(factory: DatabaseFactory): Long =
        factory.readTransaction { DevicesTable.selectAll().count() }

    private fun errorCode(body: String): String =
        Json.parseToJsonElement(body).jsonObject["error"]!!.jsonObject["code"]!!.jsonPrimitive.content

    companion object {
        private const val UNKNOWN_CODE = "999999"
        private val ULID = Regex("^[0-9A-HJKMNP-TV-Z]{26}$")
        private val BASE64URL_43 = Regex("^[A-Za-z0-9_-]{43}$")
    }
}

private class DeviceSeed {
    private var counter = 0

    fun fetchAndIncrement(): Char = CROCKFORD[counter++ % CROCKFORD.length]

    private companion object {
        private const val CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    }
}

private val DEVICE_SEED = DeviceSeed()
