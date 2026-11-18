/**
 * author : 王星星
 * date : 2026-11-17 11:28
 * email : 1099420259@qq.com
 * description : SyncEngine 增量拉取集成测试；用内存 Room 与 MockEngine 验证多页、空页、墓碑、待发状态保护、未配对、离线、写响应不推进游标以及与 OutboxWorker 共享同步锁
 */
package com.wangxingxing.clipbox.sync

import com.wangxingxing.clipbox.db.ClipboxDatabase
import com.wangxingxing.clipbox.db.MetaKeys
import com.wangxingxing.clipbox.db.OutboxEntity
import com.wangxingxing.clipbox.db.OutboxOpType
import com.wangxingxing.clipbox.db.createInMemoryDatabase
import com.wangxingxing.clipbox.db.testClip
import com.wangxingxing.clipbox.model.ApiFailure
import com.wangxingxing.clipbox.model.CreateClipRequest
import com.wangxingxing.clipbox.model.SyncState
import com.wangxingxing.clipbox.network.respondJson
import com.wangxingxing.clipbox.network.stubService
import com.wangxingxing.clipbox.settings.FakePreferencesDataStore
import com.wangxingxing.clipbox.settings.SettingsRepository
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncEnginePullTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val clipId = "01J8Z6Z3K4M5N6P7Q8R9S0T1V2"
    private val otherId = "01J8Z6Z3K4M5N6P7Q8R9S0T1V3"
    private val thirdId = "01J8Z6Z3K4M5N6P7Q8R9S0T1V4"

    @Test
    fun multiPagePullAppliesAllPagesAndEndsAtLastNextSince() = runTest {
        withFixture {
            database.clipDao().upsert(testClip(id = otherId, createdAt = 5))
            database.clipDao().upsert(testClip(id = thirdId, createdAt = 6))
            responder = { request ->
                when (request.url.parameters["since"]) {
                    "0" -> respondJson(
                        pageBody(
                            items = listOf(
                                activeBody(clipId, serverSeq = 41),
                                activeBody(otherId, serverSeq = 42),
                            ),
                            nextSince = 42,
                            hasMore = true,
                            serverMaxSeq = 42,
                        )
                    )

                    else -> respondJson(
                        pageBody(
                            items = listOf(tombstoneBody(thirdId, serverSeq = 50)),
                            nextSince = 50,
                            serverMaxSeq = 50,
                        )
                    )
                }
            }

            val result = engine().pull()

            assertEquals(PullResult.Success(appliedItems = 3, lastSeq = 50, resynced = false), result)
            assertEquals("remote-$clipId", assertNotNull(database.clipDao().getById(clipId)).content)
            assertNull(database.clipDao().getById(thirdId))
            assertEquals(50, lastSeq())
            assertEquals(listOf("0", "42"), requests.map { it.url.parameters["since"] })
            assertTrue(requests.all { it.url.parameters["limit"] == "200" })
            assertTrue(
                requests.all { it.url.parameters["full"] == "true" },
                "首次配对从 lastSeq=0 开始，整轮分页都应携带 full=true",
            )
        }
    }

    @Test
    fun emptyPageKeepsCursorUnchanged() = runTest {
        withFixture {
            database.metaDao().put(MetaKeys.LAST_SEQ, 41)
            responder = { respondJson(pageBody(nextSince = 41, serverMaxSeq = 41)) }

            val result = engine().pull()

            assertEquals(PullResult.Success(appliedItems = 0, lastSeq = 41, resynced = false), result)
            assertEquals(41, lastSeq())
            assertEquals(1, requests.size)
            assertNull(requests.single().url.parameters["full"], "增量拉取不得携带 full")
        }
    }

    @Test
    fun tombstoneDeletesSyncedRecordButKeepsRecordWithPendingOp() = runTest {
        withFixture {
            database.clipDao().upsert(testClip(id = otherId, createdAt = 5))
            database.clipDao().upsert(
                testClip(id = clipId, createdAt = 1, syncState = SyncState.PENDING_CREATE),
            )
            database.enqueueCreate(clipId)
            responder = {
                respondJson(
                    pageBody(
                        items = listOf(
                            tombstoneBody(otherId, serverSeq = 51),
                            tombstoneBody(clipId, serverSeq = 52),
                        ),
                        nextSince = 52,
                        serverMaxSeq = 52,
                    )
                )
            }

            engine().pull()

            assertNull(database.clipDao().getById(otherId))
            val pending = assertNotNull(database.clipDao().getById(clipId))
            assertEquals(SyncState.PENDING_CREATE, pending.syncState)
            assertEquals(1, database.outboxDao().opsForClip(clipId).size)
            assertEquals(52, lastSeq())
        }
    }

    @Test
    fun pendingAndFailedRecordsAreNotOverwrittenByRemoteContent() = runTest {
        withFixture {
            database.clipDao().upsert(
                testClip(id = clipId, createdAt = 1, content = "local", syncState = SyncState.PENDING_CREATE),
            )
            database.clipDao().upsert(
                testClip(id = otherId, createdAt = 2, content = "failed", syncState = SyncState.FAILED_UPDATE)
                    .copy(lastErrorCode = "VALIDATION_ERROR"),
            )
            responder = {
                respondJson(
                    pageBody(
                        items = listOf(
                            activeBody(clipId, content = "remote", serverSeq = 41),
                            activeBody(otherId, content = "remote", serverSeq = 42),
                        ),
                        nextSince = 42,
                        serverMaxSeq = 42,
                    )
                )
            }

            engine().pull()

            val pending = assertNotNull(database.clipDao().getById(clipId))
            assertEquals("local", pending.content)
            assertEquals(SyncState.PENDING_CREATE, pending.syncState)
            assertEquals(0, pending.serverSeq)
            val failed = assertNotNull(database.clipDao().getById(otherId))
            assertEquals("failed", failed.content)
            assertEquals(SyncState.FAILED_UPDATE, failed.syncState)
            assertEquals("VALIDATION_ERROR", failed.lastErrorCode)
            // 记录被跳过不影响游标推进，否则同一变更会被无限重拉
            assertEquals(42, lastSeq())
        }
    }

    @Test
    fun unpairedDeviceSendsNoRequest() = runTest {
        withFixture {
            settings.clearPairingInfo()

            val result = engine().pull()

            assertEquals(PullResult.WaitingForPairing, result)
            assertTrue(requests.isEmpty())
            assertEquals(0, lastSeq())
        }
    }

    @Test
    fun offlineFailureKeepsCursorAtLastCommittedPage() = runTest {
        withFixture {
            database.metaDao().put(MetaKeys.LAST_SEQ, 41)
            responder = { throw IllegalStateException("offline") }

            val result = engine().pull()

            assertEquals(PullResult.Failure(ApiFailure.Offline), result)
            assertEquals(41, lastSeq())
            assertEquals(1, requests.size)
        }
    }

    @Test
    fun writeResponseDoesNotAdvanceCursor() = runTest {
        withFixture {
            database.clipDao().upsert(
                testClip(id = clipId, createdAt = 1, syncState = SyncState.PENDING_CREATE),
            )
            database.enqueueCreate(clipId)
            responder = {
                respondJson(
                    """{"id":"$clipId","serverSeq":41,"createdAt":1,"pinned":false,"deleted":false}""",
                    HttpStatusCode.Created,
                )
            }
            val worker = OutboxWorker(
                database = database,
                apiService = stubService(requests = requests) { responder(it) },
                settingsRepository = settings,
                clock = { 1_000 },
            )

            worker.replayDue()

            assertEquals(SyncState.SYNCED, assertNotNull(database.clipDao().getById(clipId)).syncState)
            assertEquals(0, lastSeq())
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun pullWaitsForMutexSharedWithOutboxWorker() = runTest {
        withFixture {
            responder = { respondJson(pageBody(nextSince = 0, serverMaxSeq = 0)) }
            val sharedMutex = Mutex()
            val worker = OutboxWorker(
                database = database,
                apiService = stubService(requests = requests) { responder(it) },
                settingsRepository = settings,
                syncMutex = sharedMutex,
            )
            val syncEngine = engine(sharedMutex)

            // 模拟 Outbox 正在重放：拉取必须排队，不能同时改本地库
            sharedMutex.lock()
            assertTrue(worker.mutex.isLocked)
            var result: PullResult? = null
            try {
                val job = launch { result = syncEngine.pull() }
                runCurrent()
                assertTrue(requests.isEmpty())

                sharedMutex.unlock()
                job.join()
            } finally {
                if (sharedMutex.isLocked) sharedMutex.unlock()
            }

            assertEquals(PullResult.Success(appliedItems = 0, lastSeq = 0, resynced = false), result)
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

        fun engine(mutex: Mutex = Mutex()): SyncEngine = SyncEngine(
            database = database,
            apiService = stubService(requests = requests) { responder(it) },
            settingsRepository = settings,
            syncMutex = mutex,
        )

        suspend fun lastSeq(): Long = database.metaDao().get(MetaKeys.LAST_SEQ)

        fun close() {
            database.close()
        }
    }

    private suspend fun ClipboxDatabase.enqueueCreate(id: String) {
        outboxDao().enqueue(
            OutboxEntity(
                clipId = id,
                opType = OutboxOpType.CREATE,
                payload = json.encodeToString(
                    CreateClipRequest(id = id, content = "local", createdAt = 1),
                ),
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
        pinned: Boolean = false,
        createdAt: Long = 10,
        serverSeq: Long,
    ): String = """
        {"id":"$id","content":"$content","sourceDeviceId":"device-1","sourceDeviceName":"Pixel",
        "pinned":$pinned,"createdAt":$createdAt,"serverSeq":$serverSeq,"deleted":false}
    """.trimIndent()

    private fun tombstoneBody(id: String, serverSeq: Long): String =
        """{"id":"$id","serverSeq":$serverSeq,"deleted":true}"""

    private companion object {
        const val TOKEN = "test-device-token"
    }
}
