/**
 * author : 王星星
 * date : 2026-11-17 13:40
 * email : 1099420259@qq.com
 * description : WS 事件应用与缺口补拉集成测试；用内存 Room 与 MockEngine 验证连续事件推进游标、重复事件幂等、缺口只触发一次 REST 补拉且游标只按 nextSince 前进
 */
package com.wangxingxing.clipbox.sync

import com.wangxingxing.clipbox.db.ClipboxDatabase
import com.wangxingxing.clipbox.db.MetaKeys
import com.wangxingxing.clipbox.db.OutboxEntity
import com.wangxingxing.clipbox.db.OutboxOpType
import com.wangxingxing.clipbox.db.createInMemoryDatabase
import com.wangxingxing.clipbox.db.testClip
import com.wangxingxing.clipbox.model.ApiFailure
import com.wangxingxing.clipbox.model.ClipDto
import com.wangxingxing.clipbox.model.SyncState
import com.wangxingxing.clipbox.model.WsClipEvent
import com.wangxingxing.clipbox.model.WsMessageType
import com.wangxingxing.clipbox.network.respondJson
import com.wangxingxing.clipbox.network.stubService
import com.wangxingxing.clipbox.settings.FakePreferencesDataStore
import com.wangxingxing.clipbox.settings.SettingsRepository
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncGapTest {

    private val clipId = "01J8Z6Z3K4M5N6P7Q8R9S0T1V2"
    private val otherId = "01J8Z6Z3K4M5N6P7Q8R9S0T1V3"

    @Test
    fun continuousEventAppliesAndAdvancesCursorWithoutHttp() = runTest {
        withFixture {
            val result = engine().applyEvent(event(otherId, seq = 1))

            assertEquals(SyncEventResult.Applied(1), result)
            assertEquals(1, lastSeq())
            assertEquals("remote-$otherId", assertNotNull(database.clipDao().getById(otherId)).content)
            assertTrue(requests.isEmpty())
        }
    }

    @Test
    fun duplicateEventIsIgnored() = runTest {
        withFixture {
            database.metaDao().put(MetaKeys.LAST_SEQ, 1)
            database.clipDao().upsert(testClip(id = otherId, createdAt = 1, content = "local"))

            val result = engine().applyEvent(event(otherId, seq = 1, content = "remote"))

            assertEquals(SyncEventResult.Ignored, result)
            assertEquals("local", assertNotNull(database.clipDao().getById(otherId)).content)
            assertEquals(1, lastSeq())
            assertTrue(requests.isEmpty())
        }
    }

    @Test
    fun gapTriggersSinglePullAndCursorFollowsNextSince() = runTest {
        withFixture {
            database.metaDao().put(MetaKeys.LAST_SEQ, 1)
            responder = {
                respondJson(
                    pageBody(
                        items = listOf(
                            activeBody(clipId, serverSeq = 2),
                            activeBody(otherId, content = "remote-$otherId", serverSeq = 3),
                        ),
                        nextSince = 3,
                        serverMaxSeq = 3,
                    )
                )
            }

            // 事件自带的正文不得直接落库：以补拉结果为准
            val result = engine().applyEvent(event(otherId, seq = 3, content = "ws-content"))

            assertEquals(SyncEventResult.GapPulled(3), result)
            assertEquals(1, requests.size)
            assertEquals(listOf("1"), requests.map { it.url.parameters["since"] })
            assertEquals(3, lastSeq())
            assertEquals("remote-$otherId", assertNotNull(database.clipDao().getById(otherId)).content)

            // 同一事件再次到达只忽略，不再触发补拉，也不会重复推进游标
            assertEquals(SyncEventResult.Ignored, engine().applyEvent(event(otherId, seq = 3)))
            assertEquals(1, requests.size)
            assertEquals(3, lastSeq())
        }
    }

    @Test
    fun gapPullFailureKeepsCursor() = runTest {
        withFixture {
            database.metaDao().put(MetaKeys.LAST_SEQ, 1)
            responder = { throw IllegalStateException("offline") }

            val result = engine().applyEvent(event(otherId, seq = 5))

            assertEquals(SyncEventResult.GapPullFailed(ApiFailure.Offline), result)
            assertEquals(1, lastSeq())
            assertEquals(1, requests.size)
        }
    }

    @Test
    fun eventForPendingLocalRecordAdvancesCursorButKeepsLocalContent() = runTest {
        withFixture {
            database.clipDao().upsert(
                testClip(id = otherId, createdAt = 1, content = "local", syncState = SyncState.PENDING_CREATE),
            )

            val result = engine().applyEvent(event(otherId, seq = 1, content = "remote"))

            assertEquals(SyncEventResult.Applied(1), result)
            val local = assertNotNull(database.clipDao().getById(otherId))
            assertEquals("local", local.content)
            assertEquals(SyncState.PENDING_CREATE, local.syncState)
            assertEquals(1, lastSeq())
        }
    }

    @Test
    fun tombstoneEventDeletesSyncedRecordAndKeepsPendingDelete() = runTest {
        withFixture {
            database.clipDao().upsert(testClip(id = otherId, createdAt = 1))
            database.clipDao().upsert(
                testClip(id = clipId, createdAt = 2, syncState = SyncState.PENDING_DELETE, deleted = true),
            )
            database.outboxDao().enqueue(
                OutboxEntity(
                    clipId = clipId,
                    opType = OutboxOpType.DELETE,
                    payload = "{}",
                    createdAt = 2,
                )
            )

            assertEquals(SyncEventResult.Applied(1), engine().applyEvent(tombstone(otherId, seq = 1)))
            assertEquals(SyncEventResult.Applied(2), engine().applyEvent(tombstone(clipId, seq = 2)))

            assertNull(database.clipDao().getById(otherId))
            assertNotNull(database.clipDao().getById(clipId))
            assertEquals(2, lastSeq())
            assertTrue(requests.isEmpty())
        }
    }

    @Test
    fun gapEventOnUnpairedDeviceReturnsNotPairedWithoutRequest() = runTest {
        withFixture {
            database.metaDao().put(MetaKeys.LAST_SEQ, 1)
            settings.clearPairingInfo()

            // 连续事件在真实链路中只可能来自已认证连接；只有缺口补拉需要令牌
            val result = engine().applyEvent(event(otherId, seq = 3))

            assertEquals(SyncEventResult.NotPaired, result)
            assertTrue(requests.isEmpty())
            assertEquals(1, lastSeq())
        }
    }

    private suspend fun withFixture(block: suspend Fixture.() -> Unit) {
        val fixture = Fixture()
        try {
            fixture.pair()
            fixture.block()
        } finally {
            fixture.close()
        }
    }

    private inner class Fixture {
        val database: ClipboxDatabase = createInMemoryDatabase()
        val requests = mutableListOf<HttpRequestData>()
        val settings = SettingsRepository(FakePreferencesDataStore(), diagnosticLoggingDefault = false)
        var responder: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = {
            respondJson(pageBody(nextSince = 0, serverMaxSeq = 0))
        }

        suspend fun pair() {
            settings.setPairingInfo(
                deviceToken = TOKEN,
                deviceId = "device-1",
                deviceName = "Pixel",
                lastServerUrl = "http://test.local:8080",
            )
        }

        fun engine(): SyncEngine = SyncEngine(
            database = database,
            apiService = stubService(requests = requests) { responder(it) },
            settingsRepository = settings,
        )

        suspend fun lastSeq(): Long = database.metaDao().get(MetaKeys.LAST_SEQ)

        fun close() {
            database.close()
        }
    }

    private fun event(
        id: String,
        seq: Long,
        content: String = "remote-$id",
    ): WsClipEvent = WsClipEvent(
        type = WsMessageType.CLIP_CREATED,
        seq = seq,
        data = ClipDto(
            id = id,
            content = content,
            sourceDeviceId = "device-1",
            sourceDeviceName = "Pixel",
            pinned = false,
            createdAt = 10,
            serverSeq = seq,
            deleted = false,
        ),
    )

    private fun tombstone(id: String, seq: Long): WsClipEvent = WsClipEvent(
        type = WsMessageType.CLIP_DELETED,
        seq = seq,
        data = ClipDto(id = id, serverSeq = seq, deleted = true),
    )

    private fun pageBody(
        items: List<String> = emptyList(),
        nextSince: Long,
        hasMore: Boolean = false,
        resyncRequired: Boolean = false,
        serverMaxSeq: Long,
    ): String = """
        {"items":[${items.joinToString(",")}],"nextSince":$nextSince,"hasMore":$hasMore,
        "resyncRequired":$resyncRequired,"minAvailableSeq":1,"serverMaxSeq":$serverMaxSeq}
    """.trimIndent()

    private fun activeBody(
        id: String,
        content: String = "remote-$id",
        serverSeq: Long,
    ): String = """
        {"id":"$id","content":"$content","sourceDeviceId":"device-1","sourceDeviceName":"Pixel",
        "pinned":false,"createdAt":10,"serverSeq":$serverSeq,"deleted":false}
    """.trimIndent()

    private companion object {
        const val TOKEN = "test-device-token"
    }
}
