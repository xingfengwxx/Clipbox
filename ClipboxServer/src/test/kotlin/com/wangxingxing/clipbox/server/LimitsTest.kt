/**
 * author : 王星星
 * date : 2026-11-17 11:05
 * email : 1099420259@qq.com
 * description : 请求防护测试：512 KiB 请求体上限（含分块）、415、405、六类限流额度、Retry-After 与转发头不可绕过。
 *               注意：每个用例使用独立临时目录与全新应用实例，限流计数因此互不影响；
 *                     断言中不得输出令牌或配对码明文。
 */
package com.wangxingxing.clipbox.server

import com.wangxingxing.clipbox.server.config.AppConfig
import com.wangxingxing.clipbox.server.db.DatabaseFactory
import com.wangxingxing.clipbox.server.db.DeviceRepository
import com.wangxingxing.clipbox.server.db.DeviceRow
import com.wangxingxing.clipbox.server.db.PairingCodesTable
import com.wangxingxing.clipbox.server.plugins.FixedWindowRateLimiter
import com.wangxingxing.clipbox.server.plugins.MAX_REQUEST_BODY_BYTES
import com.wangxingxing.clipbox.server.plugins.RateLimitDecision
import com.wangxingxing.clipbox.server.plugins.RateLimitSettings
import com.wangxingxing.clipbox.server.util.Hashing
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.junit.jupiter.api.io.TempDir

class LimitsTest {

    @TempDir
    lateinit var tempDir: Path

    private val adminToken = "a".repeat(32)
    private val deviceId = "01J8Y6Z3K4M5N6P7Q8R9S0T1V2"
    private val deviceToken = "d".repeat(43)

    @Test
    fun `sixth pairing attempt from the same ip is rate limited`() = withApp { factory, client ->
        val code = unknownCode(factory)
        repeat(5) { attempt ->
            val response = client.pair(code)
            assertEquals(HttpStatusCode.BadRequest, response.status, "第 ${attempt + 1} 次应进入业务校验")
        }

        val limited = client.pair(code)
        assertEquals(HttpStatusCode.TooManyRequests, limited.status)
        assertEquals("RATE_LIMITED", errorCode(limited.bodyAsText()))
        assertRetryAfter(limited)
    }

    @Test
    fun `admin pairing code endpoint stops after ten attempts`() = withApp { _, client ->
        repeat(10) {
            assertEquals(HttpStatusCode.Created, client.createCode().status)
        }

        val limited = client.createCodeResponse()
        assertEquals(HttpStatusCode.TooManyRequests, limited.status)
        assertEquals("RATE_LIMITED", errorCode(limited.bodyAsText()))
        assertRetryAfter(limited)
    }

    @Test
    fun `x forwarded for header does not bypass the ip limit`() = withApp { factory, client ->
        val code = unknownCode(factory)
        repeat(5) { attempt ->
            val response = client.pair(code, forwardedFor = "203.0.113.$attempt")
            assertEquals(HttpStatusCode.BadRequest, response.status, "第 ${attempt + 1} 次应进入业务校验")
        }

        val limited = client.pair(code, forwardedFor = "198.51.100.7")
        assertEquals(HttpStatusCode.TooManyRequests, limited.status, "来源 IP 只取 TCP 连接，转发头不得重置计数")
    }

    @Test
    fun `content length above the limit is rejected with 413`() = withApp { factory, client ->
        seedDevice(factory)
        val content = "a".repeat(MAX_REQUEST_BODY_BYTES.toInt())
        val response = client.post(CLIPS) {
            header(HttpHeaders.Authorization, "Bearer $deviceToken")
            contentType(ContentType.Application.Json)
            setBody("""{"id":"$CLIP_ID","content":"$content","createdAt":1}""")
        }

        assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        assertEquals("PAYLOAD_TOO_LARGE", errorCode(response.bodyAsText()))
    }

    @Test
    fun `chunked body above the limit is rejected with 413`() = withApp { factory, client ->
        seedDevice(factory)
        val oversized = ByteArray((MAX_REQUEST_BODY_BYTES + 1024).toInt()) { 'a'.code.toByte() }
        val response = client.post(CLIPS) {
            header(HttpHeaders.Authorization, "Bearer $deviceToken")
            contentType(ContentType.Application.Json)
            // 不带 contentLength 的 WriteChannelContent 会以 chunked 发送，绕过 Content-Length 前置检查。
            setBody(
                object : OutgoingContent.WriteChannelContent() {
                    override suspend fun writeTo(channel: ByteWriteChannel) {
                        channel.writeFully(oversized)
                    }
                },
            )
        }

        assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        assertEquals("PAYLOAD_TOO_LARGE", errorCode(response.bodyAsText()))
    }

    @Test
    fun `non json body is rejected with 415`() = withApp { factory, client ->
        seedDevice(factory)
        val response = client.post(CLIPS) {
            header(HttpHeaders.Authorization, "Bearer $deviceToken")
            contentType(ContentType.Text.Plain)
            setBody("plain text")
        }

        assertEquals(HttpStatusCode.UnsupportedMediaType, response.status)
        assertEquals("UNSUPPORTED_MEDIA_TYPE", errorCode(response.bodyAsText()))
    }

    @Test
    fun `unsupported method is rejected with 405`() = withApp { _, client ->
        val response = client.delete(HEALTH)
        assertEquals(HttpStatusCode.MethodNotAllowed, response.status)
        assertEquals("METHOD_NOT_ALLOWED", errorCode(response.bodyAsText()))
    }

    @Test
    fun `device quota is counted per device token`() = withApp(RateLimitSettings(devicePerMinute = 3)) { factory, client ->
        seedDevice(factory)
        repeat(3) { attempt ->
            val response = client.authorizedGet()
            assertEquals(HttpStatusCode.OK, response.status, "第 ${attempt + 1} 次应放行")
        }

        val limited = client.authorizedGet()
        assertEquals(HttpStatusCode.TooManyRequests, limited.status)
        assertEquals("RATE_LIMITED", errorCode(limited.bodyAsText()))
        assertRetryAfter(limited)
    }

    @Test
    fun `health endpoint is rate limited`() = withApp(RateLimitSettings(healthPerMinute = 3)) { _, client ->
        // 额度 3 中已被夹具的预热请求占用 1 次，块内两次放行、第三次触发限流。
        repeat(2) { assertEquals(HttpStatusCode.OK, client.get(HEALTH).status) }

        val limited = client.get(HEALTH)
        assertEquals(HttpStatusCode.TooManyRequests, limited.status)
        assertRetryAfter(limited)
    }

    @Test
    fun `web socket connection attempts are limited per device`() =
        withApp(RateLimitSettings(webSocketAttemptsPerMinute = 2)) { factory, _ ->
            seedDevice(factory)
            val wsClient = createClient { install(WebSockets) }

            val (first, firstReply) = wsClient.openSession(deviceToken)
            assertEquals("auth.ok", firstReply?.string("type"))
            val (second, secondReply) = wsClient.openSession(deviceToken)
            assertEquals("auth.ok", secondReply?.string("type"))

            val (third, thirdReply) = wsClient.openSession(deviceToken)
            // 服务端先发 error 帧再关闭；极短窗口内客户端可能只观察到关闭，因此关闭码是主断言。
            thirdReply?.let { assertEquals("RATE_LIMITED", it.errorCode()) }
            assertEquals(4429.toShort(), third.closeCode())
            first.close()
            second.close()
        }

    @Test
    fun `fixed window limiter resets after the window elapses`() {
        var current = 1_000L
        val limiter = FixedWindowRateLimiter(limit = 2, windowMillis = 1_000) { current }

        assertTrue(limiter.acquire("key") is RateLimitDecision.Allowed)
        assertTrue(limiter.acquire("key") is RateLimitDecision.Allowed)
        val denied = limiter.acquire("key")
        assertTrue(denied is RateLimitDecision.Denied)
        assertEquals(1L, denied.retryAfterSeconds)

        current += 1_000
        assertTrue(limiter.acquire("key") is RateLimitDecision.Allowed, "新窗口应重新计数")
    }

    // --- 夹具 ---

    private fun withApp(
        settings: RateLimitSettings = RateLimitSettings(),
        block: suspend ApplicationTestBuilder.(DatabaseFactory, HttpClient) -> Unit,
    ) = testApplication {
        val factory = DatabaseFactory.open(tempDir)
        factory.migrate()
        application { module(testConfig(), factory, rateLimitSettings = settings) }
        // 首个请求触发模块启动，确保自动配对码已生成后再进入用例。
        client.get(HEALTH)
        block(factory, client)
    }

    private suspend fun seedDevice(factory: DatabaseFactory) {
        factory.writeTransaction {
            DeviceRepository.insert(DeviceRow(deviceId, "Pixel 8", Hashing.sha256Hex(deviceToken), 1_000, 1_000))
        }
    }

    private suspend fun unknownCode(factory: DatabaseFactory): String {
        val known = factory.readTransaction {
            PairingCodesTable.selectAll().map { it[PairingCodesTable.code] }.toSet()
        }
        return generateSequence(0) { it + 1 }
            .map { it.toString().padStart(6, '0') }
            .first { it !in known }
    }

    private fun testConfig() = AppConfig(
        port = 8080,
        dataDir = tempDir,
        adminToken = adminToken,
        publicBaseUrl = "http://127.0.0.1:8080",
    )

    private suspend fun HttpClient.pair(code: String, forwardedFor: String? = null): HttpResponse =
        post("/api/v1/pair") {
            forwardedFor?.let { header("X-Forwarded-For", it) }
            contentType(ContentType.Application.Json)
            setBody("""{"code":"$code","deviceName":"Pixel 8"}""")
        }

    private suspend fun HttpClient.createCodeResponse(): HttpResponse =
        post("/api/v1/admin/pairing-codes") { header("X-Admin-Token", adminToken) }

    private suspend fun HttpClient.createCode(): HttpResponse = createCodeResponse()

    private suspend fun HttpClient.authorizedGet(): HttpResponse =
        get(CLIPS) { header(HttpHeaders.Authorization, "Bearer $deviceToken") }

    private suspend fun HttpClient.openSession(
        token: String,
    ): Pair<DefaultClientWebSocketSession, JsonObject?> {
        val session = webSocketSession("/api/v1/sync")
        session.send(Frame.Text(buildJsonObject { put("type", "auth"); put("token", token) }.toString()))
        // 被拒绝的连接可能在客户端读帧前就关闭，此时只保留关闭码供断言。
        val reply = runCatching { session.receiveJson() }.getOrNull()
        return session to reply
    }

    private suspend fun DefaultClientWebSocketSession.receiveJson(): JsonObject =
        withTimeout(2_000) {
            val frame = incoming.receive() as Frame.Text
            Json.parseToJsonElement(frame.readText()).jsonObject
        }

    private suspend fun DefaultClientWebSocketSession.closeCode(): Short? =
        withTimeout(2_000) { closeReason.await() }?.code

    private fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.content

    private fun JsonObject.errorCode(): String =
        getValue("error").jsonObject.getValue("code").jsonPrimitive.content

    private fun errorCode(body: String): String =
        Json.parseToJsonElement(body).jsonObject["error"]!!.jsonObject["code"]!!.jsonPrimitive.content

    private fun assertRetryAfter(response: HttpResponse) {
        val raw = response.headers[HttpHeaders.RetryAfter]
        assertNotNull(raw, "429 必须带 Retry-After")
        val seconds = raw.toLongOrNull()
        assertNotNull(seconds, "Retry-After 必须是秒数：$raw")
        assertTrue(seconds in 1..60, "Retry-After 超出窗口范围：$seconds")
    }

    companion object {
        private const val HEALTH = "/healthz"
        private const val CLIPS = "/api/v1/clips"
        private const val CLIP_ID = "01J8Z6Z3K4M5N6P7Q8R9S0T1V2"
    }
}
