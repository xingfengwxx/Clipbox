/**
 * author : 王星星
 * date : 2026-11-17 13:50
 * email : 1099420259@qq.com
 * description : 服务端集成测试夹具：临时 SQLite、可注入限流、真实 HTTP/WebSocket 客户端，以及配对、记录、分享 helper。
 *               注意：令牌只在内存中传递，不得写入日志或断言消息；默认限流放宽，限流用例自行注入更严格额度。
 */
package com.wangxingxing.clipbox.server.integration

import com.wangxingxing.clipbox.server.config.AppConfig
import com.wangxingxing.clipbox.server.db.DatabaseFactory
import com.wangxingxing.clipbox.server.module
import com.wangxingxing.clipbox.server.plugins.RateLimitSettings
import com.wangxingxing.clipbox.server.ws.ConnectionRegistry
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import java.nio.file.Path
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

internal val ADMIN_TOKEN = "a".repeat(32)
internal const val PUBLIC_BASE_URL = "http://127.0.0.1:8080"
internal const val CREATED_AT = 1_730_000_000_000L
private const val SYNC_PATH = "/api/v1/sync"

/** 集成用例默认额度：足够支撑 200 并发请求，避免限流掩盖业务断言。 */
internal val LOOSE_LIMITS = RateLimitSettings(
    pairPerMinute = 1_000,
    adminPerMinute = 1_000,
    devicePerMinute = 100_000,
    sharePagePerMinute = 100_000,
    healthPerMinute = 100_000,
    webSocketAttemptsPerMinute = 100_000,
)

internal data class DeviceSession(
    val deviceId: String,
    val token: String,
    val name: String,
)

/** 启动一个独立数据目录的真实模块，并以 [IntegrationContext] 提供 HTTP/WS helper。 */
internal fun integrationApp(
    tempDir: Path,
    rateLimitSettings: RateLimitSettings = LOOSE_LIMITS,
    block: suspend IntegrationContext.() -> Unit,
) = testApplication {
    val factory = DatabaseFactory.open(tempDir)
    factory.migrate()
    val registry = ConnectionRegistry()
    application {
        module(
            config = AppConfig(
                port = 8080,
                dataDir = tempDir,
                adminToken = ADMIN_TOKEN,
                publicBaseUrl = PUBLIC_BASE_URL,
            ),
            databaseFactory = factory,
            connectionRegistry = registry,
            rateLimitSettings = rateLimitSettings,
        )
    }
    // 首个请求触发模块启动，确保启动自助配对码已落库后再执行用例。
    client.get("/healthz")
    IntegrationContext(this, factory, registry).block()
}

internal class IntegrationContext(
    private val app: ApplicationTestBuilder,
    val factory: DatabaseFactory,
    val registry: ConnectionRegistry,
) {
    val client: HttpClient get() = app.client

    suspend fun pairingCode(): String {
        val response = client.post("/api/v1/admin/pairing-codes") {
            header("X-Admin-Token", ADMIN_TOKEN)
        }
        check(response.status == HttpStatusCode.Created) { "生成配对码失败：${response.status}" }
        return json(response).str("code")
    }

    suspend fun pairDevice(name: String = "Pixel 8"): DeviceSession {
        val code = pairingCode()
        val response = client.post("/api/v1/pair") {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("code", code); put("deviceName", name) }.toString())
        }
        check(response.status == HttpStatusCode.Created) { "配对失败：${response.status}" }
        val body = json(response)
        return DeviceSession(body.str("deviceId"), body.str("token"), body.str("deviceName"))
    }

    suspend fun createClip(
        token: String,
        id: String,
        content: String = "hello",
        createdAt: Long = CREATED_AT,
    ): HttpResponse = client.post("/api/v1/clips") {
        bearer(token)
        contentType(ContentType.Application.Json)
        setBody(
            buildJsonObject {
                put("id", id)
                put("content", content)
                put("createdAt", createdAt)
            }.toString(),
        )
    }

    suspend fun pinClip(token: String, id: String, pinned: Boolean): HttpResponse =
        client.patch("/api/v1/clips/$id") {
            bearer(token)
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("pinned", pinned) }.toString())
        }

    suspend fun deleteClip(token: String, id: String): HttpResponse =
        client.delete("/api/v1/clips/$id") { bearer(token) }

    suspend fun clipsResponse(
        token: String,
        since: Long? = null,
        limit: Int? = null,
        full: Boolean? = null,
    ): HttpResponse = client.get("/api/v1/clips") {
        bearer(token)
        since?.let { parameter("since", it) }
        limit?.let { parameter("limit", it) }
        full?.let { parameter("full", it) }
    }

    suspend fun clips(
        token: String,
        since: Long? = null,
        limit: Int? = null,
        full: Boolean? = null,
    ): JsonObject = json(clipsResponse(token, since, limit, full))

    suspend fun createShare(
        token: String,
        clipId: String,
        ttlSeconds: Long? = null,
        oneTime: Boolean? = null,
    ): HttpResponse = client.post("/api/v1/shares") {
        bearer(token)
        contentType(ContentType.Application.Json)
        setBody(
            buildJsonObject {
                put("clipId", clipId)
                ttlSeconds?.let { put("ttlSeconds", it) }
                oneTime?.let { put("oneTime", it) }
            }.toString(),
        )
    }

    suspend fun openShare(shareToken: String): HttpResponse = client.get("/s/$shareToken")

    suspend fun postJsonRaw(path: String, body: String, token: String? = null): HttpResponse =
        client.post(path) {
            token?.let { bearer(it) }
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    suspend fun patchJsonRaw(path: String, body: String, token: String): HttpResponse =
        client.patch(path) {
            bearer(token)
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    /** 打开并认证一条同步连接；认证失败直接失败用例，避免后续断言读到错误帧。 */
    suspend fun openSocket(token: String): DefaultClientWebSocketSession {
        val session = app.createClient { install(WebSockets) }.webSocketSession(SYNC_PATH)
        session.send(
            Frame.Text(buildJsonObject { put("type", "auth"); put("token", token) }.toString()),
        )
        val auth = session.receiveJson()
        check(auth.str("type") == "auth.ok") { "WebSocket 认证失败：${auth.str("type")}" }
        return session
    }
}

internal fun HttpRequestBuilder.bearer(token: String) {
    header(HttpHeaders.Authorization, "Bearer $token")
}

internal suspend fun json(response: HttpResponse): JsonObject =
    Json.parseToJsonElement(response.bodyAsText()).jsonObject

internal suspend fun DefaultClientWebSocketSession.receiveJson(): JsonObject = withTimeout(2_000) {
    val frame = incoming.receive() as Frame.Text
    Json.parseToJsonElement(frame.readText()).jsonObject
}

internal suspend fun DefaultClientWebSocketSession.receiveJsonOrNull(): JsonObject? =
    withTimeoutOrNull(300) {
        val frame = incoming.receive() as Frame.Text
        Json.parseToJsonElement(frame.readText()).jsonObject
    }

internal fun JsonObject.str(key: String): String = getValue(key).jsonPrimitive.content

internal fun JsonObject.long(key: String): Long = getValue(key).jsonPrimitive.long

internal fun JsonObject.bool(key: String): Boolean = getValue(key).jsonPrimitive.boolean

internal fun JsonObject.items(): List<JsonObject> = getValue("items").jsonArray.map { it.jsonObject }

internal fun JsonObject.errorCode(): String =
    getValue("error").jsonObject.getValue("code").jsonPrimitive.content

/** 生成契约允许的 26 位 ULID：固定时间前缀 + Crockford Base32 序号后缀。 */
internal fun clipIdFor(index: Int): String {
    require(index >= 0) { "index 不能为负数" }
    var remaining = index
    val suffix = CharArray(6) { '0' }
    for (position in 5 downTo 0) {
        suffix[position] = CROCKFORD[remaining % CROCKFORD.length]
        remaining /= CROCKFORD.length
    }
    return CLIP_ID_PREFIX + suffix.concatToString()
}

private const val CLIP_ID_PREFIX = "01J8Z6Z3K4M5N6P7Q8R9"
private const val CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
