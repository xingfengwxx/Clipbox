/**
 * author : 王星星
 * date : 2026-11-17 13:36
 * email : 1099420259@qq.com
 * description : SyncSocket 状态机测试；用假传输与会话驱动认证、事件缓冲、关闭码与退避重连，断言令牌只出现在 auth 首帧、未配对不连接、后台不重连
 */
package com.wangxingxing.clipbox.network

import com.wangxingxing.clipbox.model.ClipDto
import com.wangxingxing.clipbox.model.WsClipEvent
import com.wangxingxing.clipbox.model.WsMessageType
import com.wangxingxing.clipbox.settings.FakePreferencesDataStore
import com.wangxingxing.clipbox.settings.SettingsRepository
import com.wangxingxing.clipbox.sync.PullResult
import com.wangxingxing.clipbox.sync.SyncEventResult
import com.wangxingxing.clipbox.sync.SyncGateway
import com.wangxingxing.clipbox.sync.SyncStatusReporter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SyncSocketTest {

    @Test
    fun connectsAfterPullAndSendsTokenOnlyInAuthFrame() = runTest {
        withFixture { socket, transport, gateway, status ->
            socket.start(backgroundScope)
            runCurrent()

            assertEquals(listOf("ws://localhost:8080/api/v1/sync"), transport.urls)
            assertEquals(1, gateway.pullCalls)
            val frame = Json.parseToJsonElement(transport.latest.sent.single()).jsonObject
            assertEquals("auth", frame["type"]?.jsonPrimitive?.content)
            assertEquals(TOKEN, frame["token"]?.jsonPrimitive?.content)
            assertFalse(transport.urls.single().contains(TOKEN))

            transport.latest.pushText(AUTH_OK)
            runCurrent()
            // auth.ok 之后再补拉一次，补齐连接期间可能遗漏的变更
            assertEquals(2, gateway.pullCalls)
            assertTrue(status.synced >= 2)
        }
    }

    @Test
    fun eventsArrivingDuringCatchUpAreBufferedAndAppliedAfterPull() = runTest {
        withFixture { socket, transport, gateway, _ ->
            val gate = CompletableDeferred<Unit>()
            gateway.pullGate = gate
            socket.start(backgroundScope)
            runCurrent()
            transport.latest.pushText(AUTH_OK)
            runCurrent()
            // 第二次 pull 被卡住，此时到达的事件必须缓冲而不是丢弃
            transport.latest.pushText(eventText(id = "clip-1", seq = 1))
            runCurrent()
            assertTrue(gateway.applied.isEmpty())

            gateway.pullGate = null
            gate.complete(Unit)
            runCurrent()
            assertEquals(listOf("clip-1"), gateway.applied.map { it.data.id })
        }
    }

    @Test
    fun authErrorFrameStopsReconnectAndReportsUnauthorized() = runTest {
        withFixture { socket, transport, _, status ->
            socket.start(backgroundScope)
            runCurrent()
            transport.latest.pushText(ERROR_FRAME)
            runCurrent()
            advanceUntilIdle()

            assertTrue(status.unauthorized)
            assertEquals(1, transport.sessions.size)
            advanceTimeBy(61_000)
            assertEquals(1, transport.sessions.size)
        }
    }

    @Test
    fun authTimeoutStopsReconnect() = runTest {
        withFixture { socket, transport, _, status ->
            socket.start(backgroundScope)
            runCurrent()
            advanceTimeBy(5_001)
            runCurrent()
            advanceUntilIdle()

            assertTrue(status.unauthorized)
            assertEquals(1, transport.sessions.size)
        }
    }

    @Test
    fun close4408ReconnectsAfterInitialBackoff() = runTest {
        withFixture { socket, transport, gateway, _ ->
            socket.start(backgroundScope)
            runCurrent()
            transport.latest.pushClose(4408)
            runCurrent()

            // 1s 起步退避：虚拟时间未推进前不得立即重连
            assertEquals(1, transport.sessions.size)
            advanceTimeBy(1_001)
            runCurrent()
            assertEquals(2, transport.sessions.size)
            assertEquals(2, gateway.pullCalls)

            transport.latest.pushText(AUTH_OK)
            runCurrent()
            socket.onBackground()
        }
    }

    @Test
    fun backgroundStopsReconnect() = runTest {
        withFixture { socket, transport, _, _ ->
            socket.start(backgroundScope)
            runCurrent()
            transport.latest.pushClose(4408)
            socket.onBackground()
            runCurrent()
            advanceUntilIdle()

            assertEquals(1, transport.sessions.size)
        }
    }

    @Test
    fun close4401AfterAuthStopsReconnect() = runTest {
        withFixture { socket, transport, _, status ->
            socket.start(backgroundScope)
            runCurrent()
            transport.latest.pushText(AUTH_OK)
            runCurrent()
            transport.latest.pushClose(4401)
            runCurrent()
            advanceUntilIdle()

            assertTrue(status.unauthorized)
            assertEquals(1, transport.sessions.size)
        }
    }

    @Test
    fun unpairedDeviceDoesNotConnect() = runTest {
        withFixture(pair = false) { socket, transport, gateway, status ->
            socket.start(backgroundScope)
            runCurrent()

            assertTrue(transport.sessions.isEmpty())
            assertEquals(0, gateway.pullCalls)
            assertTrue(status.unauthorized)
        }
    }

    @Test
    fun syncSocketUrlMapsHttpAndHttpsScheme() {
        assertEquals("ws://localhost:8080/api/v1/sync", syncSocketUrl("http://localhost:8080"))
        assertEquals("wss://clip.example:8443/api/v1/sync", syncSocketUrl("https://clip.example:8443/"))
    }

    private suspend fun TestScope.withFixture(
        pair: Boolean = true,
        block: suspend (SyncSocket, FakeTransport, FakeGateway, RecordingStatus) -> Unit,
    ) {
        val settings = SettingsRepository(FakePreferencesDataStore(), diagnosticLoggingDefault = false)
        if (pair) {
            settings.setPairingInfo(
                deviceToken = TOKEN,
                deviceId = "device-1",
                deviceName = "Pixel",
                lastServerUrl = "http://localhost:8080",
            )
        }
        val transport = FakeTransport()
        val gateway = FakeGateway()
        val status = RecordingStatus()
        val socket = SyncSocket(
            transport = transport,
            gateway = gateway,
            settingsRepository = settings,
            defaultServerUrl = "http://localhost:8080",
            statusReporter = status,
            jitterSample = { 0.5 },
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        try {
            block(socket, transport, gateway, status)
        } finally {
            socket.onBackground()
        }
    }

    private fun eventText(id: String, seq: Long): String = ApiJson.encodeToString(
        WsClipEvent(
            type = WsMessageType.CLIP_CREATED,
            seq = seq,
            data = ClipDto(
                id = id,
                content = "remote-$id",
                sourceDeviceId = "device-1",
                sourceDeviceName = "Pixel",
                pinned = false,
                createdAt = 10,
                serverSeq = seq,
                deleted = false,
            ),
        )
    )

    private class FakeTransport : SyncSocketTransport {
        val sessions = mutableListOf<FakeSession>()
        val urls = mutableListOf<String>()

        val latest: FakeSession get() = sessions.last()

        override suspend fun connect(url: String): SyncSocketSession {
            urls += url
            return FakeSession().also { sessions += it }
        }
    }

    private class FakeSession : SyncSocketSession {
        val sent = mutableListOf<String>()
        private val incoming = Channel<SyncSocketFrame>(Channel.UNLIMITED)

        override suspend fun send(text: String) {
            sent += text
        }

        override suspend fun receive(): SyncSocketFrame = incoming.receive()

        override fun close() {
            incoming.close()
        }

        fun pushText(text: String) {
            incoming.trySend(SyncSocketFrame.Text(text))
        }

        fun pushClose(code: Int?) {
            incoming.trySend(SyncSocketFrame.Closed(code))
        }
    }

    private class FakeGateway : SyncGateway {
        var pullCalls = 0
        var pullGateCall = 2
        var pullGate: CompletableDeferred<Unit>? = null
        val applied = mutableListOf<WsClipEvent>()

        override suspend fun pull(): PullResult {
            pullCalls++
            // 只卡住指定的第 N 次补拉，前台连接前的首次拉取照常放行
            if (pullCalls == pullGateCall) pullGate?.await()
            return PullResult.Success(appliedItems = 0, lastSeq = 0, resynced = false)
        }

        override suspend fun applyEvent(event: WsClipEvent): SyncEventResult {
            applied += event
            return SyncEventResult.Applied(event.seq)
        }
    }

    private class RecordingStatus : SyncStatusReporter {
        var unauthorized = false
        var synced = 0
        var offline = 0

        override fun onSynced() {
            synced++
        }

        override fun onOffline() {
            offline++
        }

        override fun onUnauthorized() {
            unauthorized = true
        }
    }

    private companion object {
        const val TOKEN = "test-device-token"
        const val AUTH_OK = """{"type":"auth.ok","deviceId":"device-1","serverSeq":3}"""
        const val ERROR_FRAME =
            """{"type":"error","error":{"code":"UNAUTHORIZED","message":"invalid token"}}"""
    }
}
