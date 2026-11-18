/**
 * author : 王星星
 * date : 2026-11-16 16:18
 * email : 1099420259@qq.com
 * description : WebSocket 契约测试：首帧认证成功/失败/超时、同设备连接替换、写操作事件广播与序号单调、
 *               幂等写不广播、认证前不接收事件、连接关闭后注册表归零。
 *               注意：断言信息与日志中不得出现设备令牌明文；每个用例使用独立临时目录与真实 SQLite。
 */
package com.wangxingxing.clipbox.server

import com.wangxingxing.clipbox.server.config.AppConfig
import com.wangxingxing.clipbox.server.db.DatabaseFactory
import com.wangxingxing.clipbox.server.db.DeviceRepository
import com.wangxingxing.clipbox.server.db.DeviceRow
import com.wangxingxing.clipbox.server.util.Hashing
import com.wangxingxing.clipbox.server.ws.ConnectionRegistry
import com.wangxingxing.clipbox.server.ws.WebSocketSettings
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import org.junit.jupiter.api.io.TempDir

class WebSocketTest {

    @TempDir
    lateinit var tempDir: Path

    private val deviceId = "01J8Y6Z3K4M5N6P7Q8R9S0T1V2"
    private val deviceToken = "d".repeat(43)
    private val otherDeviceId = "01J8Y6Z3K4M5N6P7Q8R9S0T1V3"
    private val otherDeviceToken = "o".repeat(43)

    @Test
    fun `auth succeeds and reports the current server seq`() = withApp { _, _ ->
        assertEquals(HttpStatusCode.Created, client.createClip().status)

        val session = openSession()
        val ok = session.auth(deviceToken)
        assertEquals("auth.ok", ok.string("type"))
        assertEquals(deviceId, ok.string("deviceId"))
        assertEquals(1L, ok.long("serverSeq"))
        assertFalse(ok.toString().contains(deviceToken), "auth.ok 不得回显令牌")
    }

    @Test
    fun `unknown token receives an error frame and closes with 4401`() = withApp { _, _ ->
        val session = openSession()
        val error = session.auth("u".repeat(43))
        assertEquals("error", error.string("type"))
        assertEquals("UNAUTHORIZED", error.errorCode())
        assertEquals(4401.toShort(), session.closeCode())
    }

    @Test
    fun `malformed auth frame is rejected with 4401`() = withApp { _, _ ->
        listOf("not-json", """{"type":"subscribe"}""", """{"type":"auth","token":""}""").forEach { frame ->
            val session = openSession()
            session.send(Frame.Text(frame))
            val error = session.receiveJson()
            assertEquals("UNAUTHORIZED", error.errorCode(), frame)
            assertEquals(4401.toShort(), session.closeCode(), frame)
        }
    }

    @Test
    fun `missing auth frame closes with 4408`() = withApp(settings = WebSocketSettings(authTimeoutMillis = 200)) { _, _ ->
        val session = openSession()
        assertEquals(4408.toShort(), session.closeCode())
    }

    @Test
    fun `a second connection for the same device replaces the first with 4001`() = withApp { _, registry ->
        val first = openSession()
        first.auth(deviceToken)
        assertEquals(1, registry.activeCount())

        val second = openSession()
        second.auth(deviceToken)
        assertEquals(4001.toShort(), first.closeCode())
        assertEquals(1, registry.activeCount(), "同设备在注册表中只保留新连接")
    }

    @Test
    fun `writes broadcast created, updated and deleted events with monotonic seq`() = withApp { _, _ ->
        val phone = openSession()
        phone.auth(deviceToken)
        val desktop = openSession()
        desktop.auth(otherDeviceToken)

        assertEquals(HttpStatusCode.Created, client.createClip().status)
        val created = phone.receiveJson()
        assertEquals("clip.created", created.string("type"))
        assertEquals(1L, created.long("seq"))
        val createdData = created.data()
        assertEquals(
            setOf("id", "content", "sourceDeviceId", "sourceDeviceName", "pinned", "createdAt", "serverSeq", "deleted"),
            createdData.keys,
        )
        assertEquals(CLIP_ID, createdData.string("id"))
        assertEquals("hello", createdData.string("content"))
        assertEquals(deviceId, createdData.string("sourceDeviceId"))
        assertEquals("Pixel 8", createdData.string("sourceDeviceName"))
        assertEquals(1L, createdData.long("serverSeq"))
        assertEquals("clip.created", desktop.receiveJson().string("type"), "写入方之外已认证设备同样收到事件")

        assertEquals(HttpStatusCode.OK, client.patchClip(CLIP_ID, pinned = true).status)
        val updated = phone.receiveJson()
        assertEquals("clip.updated", updated.string("type"))
        assertEquals(2L, updated.long("seq"))
        assertTrue(updated.data().boolean("pinned"))
        assertEquals(2L, updated.data().long("serverSeq"))

        assertEquals(HttpStatusCode.OK, client.deleteClip(CLIP_ID).status)
        val deleted = phone.receiveJson()
        assertEquals("clip.deleted", deleted.string("type"))
        assertEquals(3L, deleted.long("seq"))
        val deletedData = deleted.data()
        assertEquals(setOf("id", "serverSeq", "deleted"), deletedData.keys, "墓碑事件只带三个字段")
        assertEquals(CLIP_ID, deletedData.string("id"))
        assertEquals(3L, deletedData.long("serverSeq"))
        assertTrue(deletedData.boolean("deleted"))

        listOf(1L, 2L, 3L).zip(listOf(created, updated, deleted)).forEach { (expected, event) ->
            assertEquals(expected, event.long("seq"))
        }
    }

    @Test
    fun `idempotent writes do not broadcast`() = withApp { _, _ ->
        val session = openSession()
        session.auth(deviceToken)

        client.createClip()
        session.receiveJson()

        assertEquals(HttpStatusCode.OK, client.createClip().status, "同 ID 同正文返回 200")
        assertNull(session.receiveJsonOrNull(), "幂等重放不得广播")

        assertEquals(HttpStatusCode.OK, client.patchClip(CLIP_ID, pinned = false).status, "同值置顶返回 200")
        assertNull(session.receiveJsonOrNull(), "同值置顶不得广播")

        client.deleteClip(CLIP_ID)
        session.receiveJson()

        assertEquals(HttpStatusCode.OK, client.deleteClip(CLIP_ID).status, "重复删除返回 200")
        assertNull(session.receiveJsonOrNull(), "重复删除不得广播")
    }

    @Test
    fun `no clips are delivered before authentication`() = withApp { _, _ ->
        val session = openSession()

        client.createClip()
        assertNull(session.receiveJsonOrNull(), "认证前不得接收业务事件")

        val ok = session.auth(deviceToken)
        assertEquals(1L, ok.long("serverSeq"), "认证成功时返回当前最大序号，供客户端补拉")
    }

    @Test
    fun `closing a session removes it from the registry`() = withApp { _, registry ->
        val session = openSession()
        session.auth(deviceToken)
        assertEquals(1, registry.activeCount())

        session.close()
        withTimeout(2_000) {
            while (registry.activeCount() > 0) delay(20)
        }
        assertEquals(0, registry.activeCount(), "连接关闭后不得残留注册项")
    }

    // --- 夹具 ---

    private fun withApp(
        settings: WebSocketSettings = WebSocketSettings(),
        block: suspend ApplicationTestBuilder.(DatabaseFactory, ConnectionRegistry) -> Unit,
    ) = testApplication {
        val factory = DatabaseFactory.open(tempDir)
        factory.migrate()
        factory.writeTransaction {
            DeviceRepository.insert(DeviceRow(deviceId, "Pixel 8", Hashing.sha256Hex(deviceToken), 1_000, 1_000))
            DeviceRepository.insert(DeviceRow(otherDeviceId, "MacBook", Hashing.sha256Hex(otherDeviceToken), 1_000, 1_000))
        }
        val registry = ConnectionRegistry()
        application { module(testConfig(), factory, settings, registry) }
        block(factory, registry)
    }

    private suspend fun ApplicationTestBuilder.openSession(): DefaultClientWebSocketSession {
        val wsClient = createClient { install(WebSockets) }
        return wsClient.webSocketSession(SYNC_PATH)
    }

    private suspend fun DefaultClientWebSocketSession.auth(token: String): JsonObject {
        send(Frame.Text(buildJsonObject { put("type", "auth"); put("token", token) }.toString()))
        return receiveJson()
    }

    private suspend fun DefaultClientWebSocketSession.receiveJson(): JsonObject =
        withTimeout(2_000) {
            val frame = incoming.receive() as Frame.Text
            Json.parseToJsonElement(frame.readText()).jsonObject
        }

    private suspend fun DefaultClientWebSocketSession.receiveJsonOrNull(): JsonObject? =
        withTimeoutOrNull(300) {
            val frame = incoming.receive() as Frame.Text
            Json.parseToJsonElement(frame.readText()).jsonObject
        }

    private suspend fun DefaultClientWebSocketSession.closeCode(): Short? =
        withTimeout(2_000) { closeReason.await() }?.code

    private suspend fun HttpClient.createClip(): HttpResponse =
        post("/api/v1/clips") {
            header(HttpHeaders.Authorization, "Bearer $deviceToken")
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    put("id", CLIP_ID)
                    put("content", "hello")
                    put("createdAt", CREATED_AT)
                }.toString(),
            )
        }

    private suspend fun HttpClient.patchClip(id: String, pinned: Boolean): HttpResponse =
        patch("/api/v1/clips/$id") {
            header(HttpHeaders.Authorization, "Bearer $deviceToken")
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("pinned", pinned) }.toString())
        }

    private suspend fun HttpClient.deleteClip(id: String): HttpResponse =
        delete("/api/v1/clips/$id") {
            header(HttpHeaders.Authorization, "Bearer $deviceToken")
        }

    private fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.content

    private fun JsonObject.long(key: String) = getValue(key).jsonPrimitive.long

    private fun JsonObject.boolean(key: String) = getValue(key).jsonPrimitive.content.toBoolean()

    private fun JsonObject.data(): JsonObject = getValue("data").jsonObject

    private fun JsonObject.errorCode(): String =
        getValue("error").jsonObject.getValue("code").jsonPrimitive.content

    private fun testConfig() = AppConfig(
        port = 8080,
        dataDir = tempDir,
        publicBaseUrl = "http://127.0.0.1:8080",
    )

    companion object {
        private const val SYNC_PATH = "/api/v1/sync"
        private const val CLIP_ID = "01J8Z6Z3K4M5N6P7Q8R9S0T1V2"
        private const val CREATED_AT = 1_730_000_000_000L
    }
}
