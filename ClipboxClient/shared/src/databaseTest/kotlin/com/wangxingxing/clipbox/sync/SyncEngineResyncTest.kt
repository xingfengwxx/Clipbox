/**
 * author : 王星星
 * date : 2026-11-17 11:28
 * email : 1099420259@qq.com
 * description : SyncEngine 全量重同步集成测试；验证 resyncRequired、游标超出 serverMaxSeq、清空已同步记录、保留 Outbox 与待发 / 失败记录、重同步失败与重同步死循环防护
 */
package com.wangxingxing.clipbox.sync

import com.wangxingxing.clipbox.db.ClipboxDatabase
import com.wangxingxing.clipbox.db.MetaKeys
import com.wangxingxing.clipbox.db.OutboxEntity
import com.wangxingxing.clipbox.db.OutboxOpType
import com.wangxingxing.clipbox.db.createInMemoryDatabase
import com.wangxingxing.clipbox.db.testClip
import com.wangxingxing.clipbox.model.ApiErrorCode
import com.wangxingxing.clipbox.model.ApiFailure
import com.wangxingxing.clipbox.model.CreateClipRequest
import com.wangxingxing.clipbox.model.PatchPinnedRequest
import com.wangxingxing.clipbox.model.SyncState
import com.wangxingxing.clipbox.network.respondJson
import com.wangxingxing.clipbox.network.stubService
import com.wangxingxing.clipbox.settings.FakePreferencesDataStore
import com.wangxingxing.clipbox.settings.SettingsRepository
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncEngineResyncTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val syncedId = "01J8Z6Z3K4M5N6P7Q8R9S0T1V2"
    private val createId = "01J8Z6Z3K4M5N6P7Q8R9S0T1V3"
    private val deleteId = "01J8Z6Z3K4M5N6P7Q8R9S0T1V4"
    private val failedId = "01J8Z6Z3K4M5N6P7Q8R9S0T1V5"

    @Test
    fun resyncRequiredClearsSyncedRecordsAndPullsFromZero() = runTest {
        withFixture {
            database.clipDao().upsert(testClip(id = syncedId, createdAt = 1, content = "old"))
            database.metaDao().put(MetaKeys.LAST_SEQ, 100)
            responder = { request ->
                when (request.url.parameters["since"]) {
                    "100" -> respondJson(
                        pageBody(resyncRequired = true, nextSince = 100, serverMaxSeq = 200)
                    )

                    else -> respondJson(
                        pageBody(
                            items = listOf(activeBody(syncedId, content = "new", serverSeq = 201)),
                            nextSince = 201,
                            serverMaxSeq = 201,
                        )
                    )
                }
            }

            val result = engine().pull()

            assertEquals(PullResult.Success(appliedItems = 1, lastSeq = 201, resynced = true), result)
            val clip = assertNotNull(database.clipDao().getById(syncedId))
            assertEquals("new", clip.content)
            assertEquals(201, clip.serverSeq)
            assertEquals(SyncState.SYNCED, clip.syncState)
            assertEquals(201, lastSeq())
            assertEquals(listOf("100", "0"), sinces())
        }
    }

    @Test
    fun cursorAheadOfServerMaxSeqTriggersResync() = runTest {
        withFixture {
            database.clipDao().upsert(testClip(id = syncedId, createdAt = 1, content = "old"))
            database.metaDao().put(MetaKeys.LAST_SEQ, 500)
            responder = { request ->
                when (request.url.parameters["since"]) {
                    // 服务端被恢复为旧备份：游标来自“未来”，但 resyncRequired 仍为 false
                    "500" -> respondJson(pageBody(nextSince = 500, serverMaxSeq = 42))

                    else -> respondJson(
                        pageBody(
                            items = listOf(activeBody(syncedId, content = "restored", serverSeq = 43)),
                            nextSince = 43,
                            serverMaxSeq = 43,
                        )
                    )
                }
            }

            val result = engine().pull()

            assertEquals(PullResult.Success(appliedItems = 1, lastSeq = 43, resynced = true), result)
            assertEquals("restored", assertNotNull(database.clipDao().getById(syncedId)).content)
            assertEquals(listOf("500", "0"), sinces())
        }
    }

    @Test
    fun resyncPreservesOutboxAndPendingOrFailedRecords() = runTest {
        withFixture {
            database.clipDao().upsert(testClip(id = syncedId, createdAt = 1, content = "old"))
            database.clipDao().upsert(
                testClip(id = createId, createdAt = 2, content = "local", syncState = SyncState.PENDING_CREATE),
            )
            database.clipDao().upsert(
                testClip(
                    id = deleteId,
                    createdAt = 3,
                    syncState = SyncState.PENDING_DELETE,
                    deleted = true,
                ),
            )
            database.clipDao().upsert(
                testClip(id = failedId, createdAt = 4, content = "failed", syncState = SyncState.FAILED_UPDATE)
                    .copy(lastErrorCode = "VALIDATION_ERROR"),
            )
            database.enqueueCreate(createId)
            database.enqueue(deleteId, OutboxOpType.DELETE, "{}")
            database.enqueue(
                failedId,
                OutboxOpType.PATCH_PIN,
                json.encodeToString(PatchPinnedRequest(pinned = true)),
            )
            database.metaDao().put(MetaKeys.LAST_SEQ, 100)
            responder = { request ->
                if (request.url.parameters["since"] == "100") {
                    respondJson(pageBody(resyncRequired = true, nextSince = 100, serverMaxSeq = 200))
                } else {
                    respondJson(
                        pageBody(
                            items = listOf(activeBody(syncedId, content = "new", serverSeq = 201)),
                            nextSince = 201,
                            serverMaxSeq = 201,
                        )
                    )
                }
            }

            val result = engine().pull()

            assertEquals(PullResult.Success(appliedItems = 1, lastSeq = 201, resynced = true), result)
            assertEquals("new", assertNotNull(database.clipDao().getById(syncedId)).content)

            val created = assertNotNull(database.clipDao().getById(createId))
            assertEquals(SyncState.PENDING_CREATE, created.syncState)
            assertEquals("local", created.content)
            assertEquals(1, database.outboxDao().opsForClip(createId).size)

            val deleting = assertNotNull(database.clipDao().getById(deleteId))
            assertEquals(SyncState.PENDING_DELETE, deleting.syncState)
            assertTrue(deleting.deleted)
            assertEquals(1, database.outboxDao().opsForClip(deleteId).size)

            val failed = assertNotNull(database.clipDao().getById(failedId))
            assertEquals(SyncState.FAILED_UPDATE, failed.syncState)
            assertEquals("VALIDATION_ERROR", failed.lastErrorCode)
            assertEquals(1, database.outboxDao().opsForClip(failedId).size)
            assertEquals(201, lastSeq())
        }
    }

    @Test
    fun resyncFailureKeepsOutboxAndReportsOffline() = runTest {
        withFixture {
            database.clipDao().upsert(testClip(id = syncedId, createdAt = 1))
            database.clipDao().upsert(
                testClip(id = createId, createdAt = 2, syncState = SyncState.PENDING_CREATE),
            )
            database.enqueueCreate(createId)
            database.metaDao().put(MetaKeys.LAST_SEQ, 100)
            responder = { request ->
                if (request.url.parameters["since"] == "100") {
                    respondJson(pageBody(resyncRequired = true, nextSince = 100, serverMaxSeq = 200))
                } else {
                    throw IllegalStateException("offline")
                }
            }

            val result = engine().pull()

            assertEquals(PullResult.Failure(ApiFailure.Offline), result)
            assertEquals(0, lastSeq())
            assertNull(database.clipDao().getById(syncedId))
            assertEquals(SyncState.PENDING_CREATE, assertNotNull(database.clipDao().getById(createId)).syncState)
            assertEquals(1, database.outboxDao().opsForClip(createId).size)
            assertEquals(2, requests.size)
        }
    }

    @Test
    fun fullResyncPagesCarryFullTrueAndCompleteAcrossFloor() = runTest {
        withFixture {
            database.clipDao().upsert(testClip(id = syncedId, createdAt = 1, content = "old"))
            database.metaDao().put(MetaKeys.LAST_SEQ, 100)
            responder = { request ->
                when (request.url.parameters["since"]) {
                    "100" -> {
                        assertNull(request.url.parameters["full"], "增量请求不得携带 full")
                        respondJson(pageBody(resyncRequired = true, nextSince = 100, serverMaxSeq = 400))
                    }

                    "0" -> {
                        assertEquals("true", request.url.parameters["full"])
                        respondJson(
                            pageBody(
                                items = listOf(
                                    activeBody(syncedId, content = "new", serverSeq = 2),
                                    activeBody(createId, serverSeq = 201),
                                ),
                                nextSince = 201,
                                hasMore = true,
                                serverMaxSeq = 400,
                            )
                        )
                    }

                    "201" -> {
                        assertEquals("true", request.url.parameters["full"])
                        respondJson(
                            pageBody(
                                items = listOf(activeBody(deleteId, serverSeq = 402)),
                                nextSince = 402,
                                serverMaxSeq = 402,
                            )
                        )
                    }

                    else -> error("unexpected since=${request.url.parameters["since"]}")
                }
            }

            val result = engine().pull()

            assertEquals(PullResult.Success(appliedItems = 3, lastSeq = 402, resynced = true), result)
            assertEquals(listOf(null, "true", "true"), requests.map { it.url.parameters["full"] })
            assertEquals(402, lastSeq())
        }
    }

    @Test
    fun resyncRequiredAtZeroStopsInsteadOfLooping() = runTest {
        withFixture {
            database.metaDao().put(MetaKeys.LAST_SEQ, 100)
            responder = { request ->
                when (request.url.parameters["since"]) {
                    "100" -> respondJson(pageBody(resyncRequired = true, nextSince = 100, serverMaxSeq = 200))
                    else -> {
                        assertEquals("true", request.url.parameters["full"])
                        respondJson(pageBody(resyncRequired = true, nextSince = 0, serverMaxSeq = 0))
                    }
                }
            }

            val result = engine().pull()

            assertEquals(
                PullResult.Failure(ApiFailure.Retryable(ApiErrorCode.INVALID_RESPONSE)),
                result,
            )
            assertEquals(2, requests.size)
            assertEquals(0, lastSeq())
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

        fun sinces(): List<String?> = requests.map { it.url.parameters["since"] }

        fun close() {
            database.close()
        }
    }

    private suspend fun ClipboxDatabase.enqueueCreate(id: String) {
        enqueue(
            id,
            OutboxOpType.CREATE,
            json.encodeToString(CreateClipRequest(id = id, content = "local", createdAt = 1)),
        )
    }

    private suspend fun ClipboxDatabase.enqueue(id: String, opType: OutboxOpType, payload: String) {
        outboxDao().enqueue(
            OutboxEntity(
                clipId = id,
                opType = opType,
                payload = payload,
                createdAt = 1,
            )
        )
    }

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
        createdAt: Long = 10,
    ): String = """
        {"id":"$id","content":"$content","sourceDeviceId":"device-1","sourceDeviceName":"Pixel",
        "pinned":false,"createdAt":$createdAt,"serverSeq":$serverSeq,"deleted":false}
    """.trimIndent()

    private companion object {
        const val TOKEN = "test-device-token"
    }
}
