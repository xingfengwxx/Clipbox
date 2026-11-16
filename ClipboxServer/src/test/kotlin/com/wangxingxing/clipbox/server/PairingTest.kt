/**
 * author : 王星星
 * date : 2026-11-16 14:56
 * email : 1099420259@qq.com
 * description : 配对接口契约测试：管理鉴权、6 位码生成、配对成功与各类失败、启动自助配对码、令牌只存哈希。
 *               注意：每个用例使用独立临时数据目录与真实 SQLite；断言只通过仓储与表查询读取库内状态。
 */
package com.wangxingxing.clipbox.server

import com.wangxingxing.clipbox.server.config.AppConfig
import com.wangxingxing.clipbox.server.db.DatabaseFactory
import com.wangxingxing.clipbox.server.db.DeviceRepository
import com.wangxingxing.clipbox.server.db.DevicesTable
import com.wangxingxing.clipbox.server.db.PairingCodesTable
import com.wangxingxing.clipbox.server.db.PairingRepository
import com.wangxingxing.clipbox.server.routes.AdminPairingCodeResponse
import com.wangxingxing.clipbox.server.routes.PairRequest
import com.wangxingxing.clipbox.server.routes.PairResponse
import com.wangxingxing.clipbox.server.util.Hashing
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
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

    private val adminToken = "a".repeat(32)

    @Test
    fun `admin endpoint rejects missing and wrong token without creating a code`() = withApp { factory, client ->
        // 先触发应用启动，使启动自动码已落库，再比较管理接口调用前后的行数。
        val before = pairingCodeCount(factory)

        val missing = client.post("/api/v1/admin/pairing-codes")
        assertEquals(HttpStatusCode.Unauthorized, missing.status)
        assertEquals("UNAUTHORIZED", errorCode(missing.bodyAsText()))

        val wrong = client.post("/api/v1/admin/pairing-codes") {
            header("X-Admin-Token", "b".repeat(32))
        }
        assertEquals(HttpStatusCode.Unauthorized, wrong.status)
        assertEquals("UNAUTHORIZED", errorCode(wrong.bodyAsText()))

        assertEquals(before, pairingCodeCount(factory))
    }

    @Test
    fun `admin issues a six digit code valid for ten minutes`() = withApp { factory, client ->
        val before = System.currentTimeMillis()
        val issued = client.createCode()
        val after = System.currentTimeMillis()

        assertTrue(SIX_DIGITS.matches(issued.code), "code=${issued.code}")
        assertTrue(
            issued.expiresAt in (before + 600_000)..(after + 600_000),
            "expiresAt=${issued.expiresAt}",
        )

        val stored = factory.readTransaction { PairingRepository.findByCode(issued.code) }
        assertNotNull(stored)
        assertEquals(issued.expiresAt, stored.expiresAt)
        assertEquals(null, stored.usedAt)
    }

    @Test
    fun `pairing returns a device token once and stores only its hash`() = withApp { factory, client ->
        val code = client.createCode().code

        val response = client.pair(code, "  Pixel 8  ")
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

        val storedHashes = factory.readTransaction {
            DevicesTable.selectAll().map { it[DevicesTable.tokenHash] }
        }
        assertTrue(storedHashes.none { it == body.token }, "数据库中不得出现明文令牌")

        val codeRow = factory.readTransaction { PairingRepository.findByCode(code) }
        assertNotNull(codeRow)
        assertNotNull(codeRow.usedAt)
    }

    @Test
    fun `code with leading zeros can be paired`() = withApp(
        seed = { factory ->
            factory.writeTransaction {
                PairingRepository.insert(
                    code = "000123",
                    createdAt = 1_000,
                    expiresAt = System.currentTimeMillis() + 600_000,
                )
            }
        },
    ) { _, client ->
        val response = client.pair("000123")
        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun `unknown code is rejected as invalid`() = withApp { factory, client ->
        val knownCodes = factory.readTransaction {
            PairingCodesTable.selectAll().map { it[PairingCodesTable.code] }.toSet()
        }
        val unknown = generateSequence(0) { it + 1 }
            .map { it.toString().padStart(6, '0') }
            .first { it !in knownCodes }

        val response = client.pair(unknown)
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("INVALID_CODE", errorCode(response.bodyAsText()))
    }

    @Test
    fun `expired code is rejected`() = withApp(
        seed = { factory ->
            factory.writeTransaction {
                PairingRepository.insert(
                    code = "111111",
                    createdAt = 1_000,
                    expiresAt = System.currentTimeMillis() - 1,
                )
            }
        },
    ) { _, client ->
        val response = client.pair("111111")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("CODE_EXPIRED", errorCode(response.bodyAsText()))
    }

    @Test
    fun `used code cannot be paired twice`() = withApp { factory, client ->
        val code = client.createCode().code
        assertEquals(HttpStatusCode.Created, client.pair(code).status)
        val deviceCountAfterFirst = deviceCount(factory)

        val second = client.pair(code, "Second Device")
        assertEquals(HttpStatusCode.BadRequest, second.status)
        assertEquals("CODE_USED", errorCode(second.bodyAsText()))
        assertEquals(deviceCountAfterFirst, deviceCount(factory))
    }

    @Test
    fun `malformed code and invalid device names are validation errors`() = withApp { _, client ->
        val badCode = client.pair("12345")
        assertEquals(HttpStatusCode.BadRequest, badCode.status)
        assertEquals("VALIDATION_ERROR", errorCode(badCode.bodyAsText()))

        val blankName = client.pair("123456", "   ")
        assertEquals(HttpStatusCode.BadRequest, blankName.status)
        assertEquals("VALIDATION_ERROR", errorCode(blankName.bodyAsText()))

        val longName = client.pair("123456", "x".repeat(65))
        assertEquals(HttpStatusCode.BadRequest, longName.status)
        assertEquals("VALIDATION_ERROR", errorCode(longName.bodyAsText()))
    }

    @Test
    fun `pairing ignores unknown json fields`() = withApp { _, client ->
        val code = client.createCode().code
        val response = client.post("/api/v1/pair") {
            contentType(ContentType.Application.Json)
            setBody("""{"code":"$code","deviceName":"Pixel 8","futureField":"ignored"}""")
        }
        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun `startup generates one self service code when none exists`() = withApp { factory, _ ->
        val codes = factory.readTransaction {
            PairingCodesTable.selectAll().map { it[PairingCodesTable.code] }
        }
        assertEquals(1, codes.size)
        assertTrue(SIX_DIGITS.matches(codes.single()), "code=${codes.single()}")
        assertTrue(
            factory.readTransaction {
                PairingRepository.existsUnusedUnexpired(System.currentTimeMillis())
            },
        )
    }

    private fun withApp(
        seed: suspend (DatabaseFactory) -> Unit = {},
        block: suspend (DatabaseFactory, HttpClient) -> Unit,
    ) = testApplication {
        val factory = DatabaseFactory.open(tempDir)
        factory.migrate()
        seed(factory)
        application { module(testConfig(), factory) }
        // 首个请求触发模块初始化，确保启动自动码已写入后再执行用例。
        client.get("/healthz")
        block(factory, client)
    }

    private fun testConfig() = AppConfig(
        port = 8080,
        dataDir = tempDir,
        adminToken = adminToken,
        publicBaseUrl = "http://127.0.0.1:8080",
    )

    private suspend fun HttpClient.createCode(): AdminPairingCodeResponse {
        val response = post("/api/v1/admin/pairing-codes") {
            header("X-Admin-Token", adminToken)
        }
        assertEquals(HttpStatusCode.Created, response.status)
        return Json.decodeFromString(response.bodyAsText())
    }

    private suspend fun HttpClient.pair(code: String, deviceName: String = "Pixel 8"): HttpResponse =
        post("/api/v1/pair") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(PairRequest(code = code, deviceName = deviceName)))
        }

    private suspend fun pairingCodeCount(factory: DatabaseFactory): Long =
        factory.readTransaction { PairingCodesTable.selectAll().count() }

    private suspend fun deviceCount(factory: DatabaseFactory): Long =
        factory.readTransaction { DevicesTable.selectAll().count() }

    private fun errorCode(body: String): String =
        Json.parseToJsonElement(body).jsonObject["error"]!!.jsonObject["code"]!!.jsonPrimitive.content

    companion object {
        private val SIX_DIGITS = Regex("^\\d{6}$")
        private val ULID = Regex("^[0-9A-HJKMNP-TV-Z]{26}$")
        private val BASE64URL_43 = Regex("^[A-Za-z0-9_-]{43}$")
    }
}
