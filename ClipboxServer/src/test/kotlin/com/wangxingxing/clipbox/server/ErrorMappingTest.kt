/**
 * author : 王星星
 * date : 2026-11-17 11:05
 * email : 1099420259@qq.com
 * description : 错误契约测试：所有非 2xx 都是 {"error":{"code","message"}}、无堆栈泄漏、404/400/401/405 映射一致，
 *               并校验 X-Request-Id 与 nosniff/no-referrer/no-store 响应头（且不写 HSTS）。
 *               注意：断言只读取 HTTP 状态、响应头与 JSON 结构，不依赖具体中文文案。
 */
package com.wangxingxing.clipbox.server

import com.wangxingxing.clipbox.server.config.AppConfig
import com.wangxingxing.clipbox.server.db.DatabaseFactory
import com.wangxingxing.clipbox.server.db.DeviceRepository
import com.wangxingxing.clipbox.server.db.DeviceRow
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.io.TempDir

class ErrorMappingTest {

    @TempDir
    lateinit var tempDir: Path

    private val adminToken = "a".repeat(32)
    private val deviceId = "01J8Y6Z3K4M5N6P7Q8R9S0T1V2"
    private val deviceToken = "d".repeat(43)

    @Test
    fun `unknown route returns the unified 404 body`() = withApp { _, client ->
        val response = client.get("/api/v1/does-not-exist")
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertErrorBody(response, "NOT_FOUND")
    }

    @Test
    fun `unsupported method returns the unified 405 body`() = withApp { _, client ->
        val response = client.post("/healthz")
        assertEquals(HttpStatusCode.MethodNotAllowed, response.status)
        assertErrorBody(response, "METHOD_NOT_ALLOWED")
    }

    @Test
    fun `invalid query parameters return the unified 400 body`() = withApp(seedDevice = true) { _, client ->
        val response = client.get("/api/v1/clips?since=-1") {
            header(HttpHeaders.Authorization, "Bearer $deviceToken")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertErrorBody(response, "VALIDATION_ERROR")
    }

    @Test
    fun `malformed json is a validation error rather than a server error`() = withApp { _, client ->
        val response = client.post("/api/v1/pair") {
            contentType(ContentType.Application.Json)
            setBody("{oops")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertErrorBody(response, "VALIDATION_ERROR")
    }

    @Test
    fun `missing device token returns the unified 401 body`() = withApp { _, client ->
        val response = client.get("/api/v1/clips")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertErrorBody(response, "UNAUTHORIZED")
    }

    @Test
    fun `error responses carry request id and security headers`() = withApp { _, client ->
        val response = client.get("/api/v1/does-not-exist")
        assertNotNull(response.headers[HttpHeaders.XRequestId]?.takeIf { it.isNotBlank() }, "缺少 X-Request-Id")
        assertEquals("nosniff", response.headers["X-Content-Type-Options"])
        assertEquals("no-referrer", response.headers["Referrer-Policy"])
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        assertNull(response.headers["Strict-Transport-Security"], "学习版明文 HTTP 不得下发 HSTS")
    }

    @Test
    fun `successful responses carry the same baseline headers`() = withApp { _, client ->
        val response = client.get("/healthz")
        assertEquals(HttpStatusCode.OK, response.status)
        assertNotNull(response.headers[HttpHeaders.XRequestId]?.takeIf { it.isNotBlank() })
        assertEquals("nosniff", response.headers["X-Content-Type-Options"])
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
    }

    // --- 夹具 ---

    private fun withApp(
        seedDevice: Boolean = false,
        block: suspend (DatabaseFactory, HttpClient) -> Unit,
    ) = testApplication {
        val factory = DatabaseFactory.open(tempDir)
        factory.migrate()
        if (seedDevice) {
            factory.writeTransaction {
                DeviceRepository.insert(DeviceRow(deviceId, "Pixel 8", Hashing.sha256Hex(deviceToken), 1_000, 1_000))
            }
        }
        application { module(testConfig(), factory) }
        client.get("/healthz")
        block(factory, client)
    }

    private fun testConfig() = AppConfig(
        port = 8080,
        dataDir = tempDir,
        adminToken = adminToken,
        publicBaseUrl = "http://127.0.0.1:8080",
    )

    private suspend fun assertErrorBody(response: HttpResponse, expectedCode: String) {
        val text = response.bodyAsText()
        val body = Json.parseToJsonElement(text).jsonObject
        assertEquals(setOf("error"), body.keys, "错误响应只能有 error 字段")
        val error = body.getValue("error").jsonObject
        assertEquals(setOf("code", "message"), error.keys, "error 只能有 code 与 message")
        assertEquals(expectedCode, error.getValue("code").jsonPrimitive.content)
        assertTrue(error.getValue("message").jsonPrimitive.content.isNotBlank())
        assertTrue(!text.contains("Exception") && !text.contains("at com."), "响应不得包含堆栈")
    }
}
