/**
 * author : 王星星
 * date : 2026-11-17 16:38
 * email : 1099420259@qq.com
 * description : SyncSocket 日志接入测试；驱动真实状态机断言认证令牌只以脱敏形式出现，事件正文与连接细节记录不泄漏敏感值
 */
package com.wangxingxing.clipbox.network

import com.wangxingxing.clipbox.logging.AppLogger
import com.wangxingxing.clipbox.logging.RecordingSink
import com.wangxingxing.clipbox.model.ClipDto
import com.wangxingxing.clipbox.model.WsClipEvent
import com.wangxingxing.clipbox.model.WsMessageType
import com.wangxingxing.clipbox.settings.FakePreferencesDataStore
import com.wangxingxing.clipbox.settings.SettingsRepository
import com.wangxingxing.clipbox.sync.PullResult
import com.wangxingxing.clipbox.sync.SyncEventResult
import com.wangxingxing.clipbox.sync.SyncGateway
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SocketLoggingTest {

    @Test
    fun diagnosticLogsRedactAuthTokenAndSkipEventContent() = runTest {
        val settings = SettingsRepository(FakePreferencesDataStore(), diagnosticLoggingDefault = false)
        settings.setPairingInfo(
            deviceToken = TOKEN,
            deviceId = "device-1",
            deviceName = "Pixel",
            lastServerUrl = "http://localhost:8080",
        )
        val sink = RecordingSink()
        val transport = FakeTransport()
        val socket = SyncSocket(
            transport = transport,
            gateway = FakeGateway(),
            settingsRepository = settings,
            defaultServerUrl = "http://localhost:8080",
            logger = AppLogger(sink, isDebugBuild = true),
            jitterSample = { 0.5 },
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        try {
            socket.start(backgroundScope)
            runCurrent()
            transport.latest.pushText(AUTH_OK)
            runCurrent()
            transport.latest.pushText(eventText())
            runCurrent()

            val text = sink.joined
            assertTrue(text.contains("auth-sent"))
            assertTrue(text.contains("token=<redacted>"))
            assertFalse(text.contains(TOKEN))
            // 记录正文不属于日志白名单，即使诊断开启也不得输出
            assertFalse(text.contains(REMOTE_CONTENT))
        } finally {
            socket.onBackground()
        }
    }

    private fun eventText(): String = ApiJson.encodeToString(
        WsClipEvent(
            type = WsMessageType.CLIP_CREATED,
            seq = 1,
            data = ClipDto(
                id = "clip-1",
                content = REMOTE_CONTENT,
                sourceDeviceId = "device-1",
                sourceDeviceName = "Pixel",
                pinned = false,
                createdAt = 10,
                serverSeq = 1,
                deleted = false,
            ),
        )
    )

    private class FakeTransport : SyncSocketTransport {
        val sessions = mutableListOf<FakeSession>()

        val latest: FakeSession get() = sessions.last()

        override suspend fun connect(url: String): SyncSocketSession =
            FakeSession().also { sessions += it }
    }

    private class FakeSession : SyncSocketSession {
        private val incoming = Channel<SyncSocketFrame>(Channel.UNLIMITED)

        override suspend fun send(text: String) = Unit

        override suspend fun receive(): SyncSocketFrame = incoming.receive()

        override fun close() {
            incoming.close()
        }

        fun pushText(text: String) {
            incoming.trySend(SyncSocketFrame.Text(text))
        }
    }

    private class FakeGateway : SyncGateway {
        override suspend fun pull(): PullResult =
            PullResult.Success(appliedItems = 0, lastSeq = 0, resynced = false)

        override suspend fun applyEvent(event: WsClipEvent): SyncEventResult =
            SyncEventResult.Applied(event.seq)
    }

    private companion object {
        const val TOKEN = "Zm9vYmFyYmF6cXV4MTIzNDU2Nzg5MGFiY2RlZg"
        const val REMOTE_CONTENT = "remote-content-must-not-be-logged"
        const val AUTH_OK = """{"type":"auth.ok","deviceId":"device-1","serverSeq":3}"""
    }
}
