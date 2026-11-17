/**
 * author : 王星星
 * date : 2026-11-17 11:30
 * email : 1099420259@qq.com
 * description : OutboxWorker 集成测试；用内存 Room 与 MockEngine 验证成功出队、同记录保序、退避时间点、429、401、永久失败与手动重试后的重启重放
 */
package com.wangxingxing.clipbox.sync

import com.wangxingxing.clipbox.db.ClipboxDatabase
import com.wangxingxing.clipbox.db.OutboxEntity
import com.wangxingxing.clipbox.db.OutboxOpType
import com.wangxingxing.clipbox.db.createInMemoryDatabase
import com.wangxingxing.clipbox.db.testClip
import com.wangxingxing.clipbox.model.CreateClipRequest
import com.wangxingxing.clipbox.model.PatchPinnedRequest
import com.wangxingxing.clipbox.model.SyncState
import com.wangxingxing.clipbox.network.respondJson
import com.wangxingxing.clipbox.network.stubService
import com.wangxingxing.clipbox.repository.ClipRepository
import com.wangxingxing.clipbox.settings.FakePreferencesDataStore
import com.wangxingxing.clipbox.settings.SettingsRepository
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OutboxWorkerTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val clipId = "01J8Z6Z3K4M5N6P7Q8R9S0T1V2"

    @Test
    fun createSuccessMarksClipSyncedAndClearsOutbox() = runTest {
        withFixture {
            responder = { respondJson(createBody(clipId, serverSeq = 41), HttpStatusCode.Created) }
            database.enqueueCreateOp(clipId)

            val report = worker().replayDue()

            assertEquals(1, report.applied)
            assertEquals(false, report.waitingForPairing)
            assertNull(report.nextDueAt)
            val clip = assertNotNull(database.clipDao().getById(clipId))
            assertEquals(SyncState.SYNCED, clip.syncState)
            assertEquals(41, clip.serverSeq)
            assertTrue(database.outboxDao().opsForClip(clipId).isEmpty())

            val request = requests.single()
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/v1/clips", request.url.encodedPath)
            assertEquals("Bearer $TOKEN", request.headers[HttpHeaders.Authorization])
        }
    }

    @Test
    fun createAndPatchRunInEnqueueOrderWithinOnePass() = runTest {
        withFixture {
            responder = { request ->
                when (request.method) {
                    HttpMethod.Post -> respondJson(createBody(clipId, serverSeq = 41), HttpStatusCode.Created)
                    else -> respondJson(patchBody(clipId, pinned = true, serverSeq = 42))
                }
            }
            database.enqueueCreateOp(clipId)
            val pending = assertNotNull(database.clipDao().getById(clipId))
            database.clipDao().upsert(pending.copy(pinned = true))
            database.enqueuePatchOp(clipId, pinned = true)

            val report = worker().replayDue()

            assertEquals(2, report.applied)
            assertEquals(listOf(HttpMethod.Post, HttpMethod.Patch), requests.map { it.method })
            val clip = assertNotNull(database.clipDao().getById(clipId))
            assertEquals(SyncState.SYNCED, clip.syncState)
            assertTrue(clip.pinned)
            assertEquals(42, clip.serverSeq)
            assertTrue(database.outboxDao().opsForClip(clipId).isEmpty())
        }
    }

    @Test
    fun laterOpOfSameClipWaitsForEarlierOp() = runTest {
        withFixture {
            database.enqueueCreateOp(clipId)
            val createOp = database.outboxDao().opsForClip(clipId).single()
            database.outboxDao().markRetry(createOp.opId, retryCount = 1, nextRetryAt = 5_000, errorCode = null)
            database.enqueuePatchOp(clipId, pinned = true)

            val report = worker().replayDue()

            assertEquals(0, report.applied)
            assertTrue(requests.isEmpty())
            assertEquals(5_000L, report.nextDueAt)
            assertEquals(2, database.outboxDao().opsForClip(clipId).size)
        }
    }

    @Test
    fun networkFailuresBackOffExponentiallyFromLowerJitterBound() = runTest {
        withFixture {
            jitter = 0.0
            responder = { throw IllegalStateException("offline") }
            database.enqueueCreateOp(clipId)
            val worker = worker()

            worker.replayDue()
            var op = database.outboxDao().opsForClip(clipId).single()
            assertEquals(1, op.retryCount)
            assertEquals(now + 800, op.nextRetryAt)

            // 未到期不得重发
            now += 799
            worker.replayDue()
            assertEquals(1, requests.size)

            now += 1
            worker.replayDue()
            op = database.outboxDao().opsForClip(clipId).single()
            assertEquals(2, op.retryCount)
            assertEquals(now + 1_600, op.nextRetryAt)

            now += 1_600
            worker.replayDue()
            op = database.outboxDao().opsForClip(clipId).single()
            assertEquals(3, op.retryCount)
            assertEquals(now + 3_200, op.nextRetryAt)
            assertEquals(3, requests.size)

            val clip = assertNotNull(database.clipDao().getById(clipId))
            assertEquals(SyncState.PENDING_CREATE, clip.syncState)
        }
    }

    @Test
    fun networkFailuresUseUpperJitterBound() = runTest {
        withFixture {
            jitter = 1.0
            responder = { throw IllegalStateException("offline") }
            database.enqueueCreateOp(clipId)
            val worker = worker()

            worker.replayDue()
            assertEquals(now + 1_200, database.outboxDao().opsForClip(clipId).single().nextRetryAt)

            now += 1_200
            worker.replayDue()
            now += 2_400
            worker.replayDue()
            val op = database.outboxDao().opsForClip(clipId).single()
            assertEquals(3, op.retryCount)
            assertEquals(now + 4_800, op.nextRetryAt)
        }
    }

    @Test
    fun rateLimitedUsesRetryAfterWithoutExtraBackoff() = runTest {
        withFixture {
            jitter = 0.0
            responder = {
                respondJson(
                    body = errorBody("RATE_LIMITED"),
                    status = HttpStatusCode.TooManyRequests,
                    retryAfterSeconds = "60",
                )
            }
            database.enqueueCreateOp(clipId)

            worker().replayDue()

            val op = database.outboxDao().opsForClip(clipId).single()
            assertEquals(0, op.retryCount)
            assertEquals(now + 60_000, op.nextRetryAt)
            assertEquals("RATE_LIMITED", op.lastErrorCode)
            assertEquals(SyncState.PENDING_CREATE, assertNotNull(database.clipDao().getById(clipId)).syncState)
        }
    }

    @Test
    fun unauthorizedPausesWithoutLosingQueuedOps() = runTest {
        withFixture {
            var unauthorized = true
            responder = {
                if (unauthorized) {
                    respondJson(errorBody("UNAUTHORIZED"), HttpStatusCode.Unauthorized)
                } else {
                    respondJson(createBody(clipId, serverSeq = 41), HttpStatusCode.Created)
                }
            }
            database.enqueueCreateOp(clipId)
            val worker = worker()

            val paused = worker.replayDue()

            assertTrue(paused.waitingForPairing)
            assertTrue(worker.requiresPairing.value)
            val op = database.outboxDao().opsForClip(clipId).single()
            assertEquals(0, op.retryCount)
            assertEquals(0, op.nextRetryAt)
            assertNull(op.lastErrorCode)
            assertEquals(SyncState.PENDING_CREATE, assertNotNull(database.clipDao().getById(clipId)).syncState)

            // 暂停期间再触发也不发请求
            assertTrue(worker.replayDue().waitingForPairing)
            assertEquals(1, requests.size)

            unauthorized = false
            worker.onPaired()
            assertFalse(worker.requiresPairing.value)
            assertEquals(1, worker.replayDue().applied)
            assertEquals(SyncState.SYNCED, assertNotNull(database.clipDao().getById(clipId)).syncState)
        }
    }

    @Test
    fun permanentFailureMarksFailedAndStopsAutoRetry() = runTest {
        withFixture {
            responder = { respondJson(errorBody("VALIDATION_ERROR"), HttpStatusCode.BadRequest) }
            database.enqueueCreateOp(clipId)
            val worker = worker()

            worker.replayDue()

            val clip = assertNotNull(database.clipDao().getById(clipId))
            assertEquals(SyncState.FAILED_CREATE, clip.syncState)
            assertEquals("VALIDATION_ERROR", clip.lastErrorCode)
            assertEquals(now, clip.lastErrorAt)
            val op = database.outboxDao().opsForClip(clipId).single()
            assertEquals(Long.MAX_VALUE, op.nextRetryAt)
            assertEquals("VALIDATION_ERROR", op.lastErrorCode)

            now += 24 * 60 * 60 * 1_000L
            val later = worker.replayDue()

            assertEquals(0, later.applied)
            assertEquals(1, requests.size)
            assertTrue(database.outboxDao().nextDue(now).isEmpty())
        }
    }

    @Test
    fun permanentFailureBlocksQueuedPatchOfSameClip() = runTest {
        withFixture {
            responder = { respondJson(errorBody("PAYLOAD_TOO_LARGE"), HttpStatusCode.PayloadTooLarge) }
            database.enqueueCreateOp(clipId)
            val pending = assertNotNull(database.clipDao().getById(clipId))
            database.clipDao().upsert(pending.copy(pinned = true))
            database.enqueuePatchOp(clipId, pinned = true)

            worker().replayDue()

            assertEquals(1, requests.size)
            assertEquals(HttpMethod.Post, requests.single().method)
            assertEquals(SyncState.FAILED_CREATE, assertNotNull(database.clipDao().getById(clipId)).syncState)
            assertTrue(database.outboxDao().opsForClip(clipId).all { it.nextRetryAt == Long.MAX_VALUE })
        }
    }

    @Test
    fun deleteSuccessRemovesLocalClip() = runTest {
        withFixture {
            responder = { respondJson(deleteBody(clipId, serverSeq = 50)) }
            database.clipDao().upsert(
                testClip(id = clipId, createdAt = 1, deleted = true, syncState = SyncState.PENDING_DELETE),
            )
            database.enqueueDeleteOp(clipId)

            val report = worker().replayDue()

            assertEquals(1, report.applied)
            assertNull(database.clipDao().getById(clipId))
            assertTrue(database.outboxDao().opsForClip(clipId).isEmpty())
            assertEquals(HttpMethod.Delete, requests.single().method)
        }
    }

    @Test
    fun deleteNotFoundIsTreatedAsSuccess() = runTest {
        withFixture {
            responder = { respondJson(errorBody("NOT_FOUND"), HttpStatusCode.NotFound) }
            database.clipDao().upsert(
                testClip(id = clipId, createdAt = 1, deleted = true, syncState = SyncState.PENDING_DELETE),
            )
            database.enqueueDeleteOp(clipId)

            val report = worker().replayDue()

            assertEquals(1, report.applied)
            assertNull(database.clipDao().getById(clipId))
            assertTrue(database.outboxDao().opsForClip(clipId).isEmpty())
        }
    }

    @Test
    fun patchNotFoundMarksFailedUpdate() = runTest {
        withFixture {
            responder = { respondJson(errorBody("NOT_FOUND"), HttpStatusCode.NotFound) }
            database.clipDao().upsert(
                testClip(id = clipId, createdAt = 1, pinned = true, syncState = SyncState.PENDING_UPDATE),
            )
            database.enqueuePatchOp(clipId, pinned = true)

            worker().replayDue()

            val clip = assertNotNull(database.clipDao().getById(clipId))
            assertEquals(SyncState.FAILED_UPDATE, clip.syncState)
            assertEquals("NOT_FOUND", clip.lastErrorCode)
        }
    }

    @Test
    fun manualRetryAfterFailureReplaysOnRestartedWorker() = runTest {
        withFixture {
            var failing = true
            responder = {
                if (failing) {
                    respondJson(errorBody("CONFLICT"), HttpStatusCode.Conflict)
                } else {
                    respondJson(createBody(clipId, serverSeq = 41), HttpStatusCode.Created)
                }
            }
            database.enqueueCreateOp(clipId)
            worker().replayDue()
            assertEquals(SyncState.FAILED_CREATE, assertNotNull(database.clipDao().getById(clipId)).syncState)

            failing = false
            val repository = ClipRepository(database, settings, clock = { now })
            assertTrue(repository.retry(clipId))
            val retried = assertNotNull(database.clipDao().getById(clipId))
            assertEquals(SyncState.PENDING_CREATE, retried.syncState)
            assertEquals(0, database.outboxDao().opsForClip(clipId).single().nextRetryAt)

            // 新 worker 实例消费同一数据库，等价于重启后继续重放
            val report = worker().replayDue()

            assertEquals(1, report.applied)
            assertEquals(SyncState.SYNCED, assertNotNull(database.clipDao().getById(clipId)).syncState)
            assertEquals(2, requests.size)
        }
    }

    @Test
    fun unpairedDeviceSendsNothing() = runTest {
        withFixture {
            settings.clearPairingInfo()
            database.enqueueCreateOp(clipId)

            val report = worker().replayDue()

            assertTrue(report.waitingForPairing)
            assertEquals(0, report.applied)
            assertTrue(requests.isEmpty())
            assertEquals(SyncState.PENDING_CREATE, assertNotNull(database.clipDao().getById(clipId)).syncState)
            assertEquals(1, database.outboxDao().opsForClip(clipId).size)
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
        var now: Long = 1_000L
        var jitter: Double = 0.5
        var responder: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = {
            respondJson("{}")
        }

        suspend fun pair() {
            settings.setPairingInfo(
                deviceToken = TOKEN,
                deviceId = "device-1",
                deviceName = "Pixel",
                lastServerUrl = "http://test.local:8080",
            )
        }

        fun worker(): OutboxWorker = OutboxWorker(
            database = database,
            apiService = stubService(requests = requests) { responder(it) },
            settingsRepository = settings,
            clock = { now },
            jitterSample = { jitter },
        )

        fun close() {
            database.close()
        }
    }

    private suspend fun ClipboxDatabase.enqueueCreateOp(
        id: String,
        content: String = "hello",
        createdAt: Long = 100L,
    ) {
        clipDao().upsert(
            testClip(id = id, createdAt = createdAt, content = content, syncState = SyncState.PENDING_CREATE),
        )
        outboxDao().enqueue(
            OutboxEntity(
                clipId = id,
                opType = OutboxOpType.CREATE,
                payload = json.encodeToString(CreateClipRequest(id = id, content = content, createdAt = createdAt)),
                createdAt = createdAt,
            ),
        )
    }

    private suspend fun ClipboxDatabase.enqueuePatchOp(id: String, pinned: Boolean) {
        outboxDao().enqueue(
            OutboxEntity(
                clipId = id,
                opType = OutboxOpType.PATCH_PIN,
                payload = json.encodeToString(PatchPinnedRequest(pinned = pinned)),
                createdAt = 200,
            ),
        )
    }

    private suspend fun ClipboxDatabase.enqueueDeleteOp(id: String) {
        outboxDao().enqueue(
            OutboxEntity(
                clipId = id,
                opType = OutboxOpType.DELETE,
                payload = "{}",
                createdAt = 300,
            ),
        )
    }

    private fun createBody(id: String, serverSeq: Long): String =
        """{"id":"$id","serverSeq":$serverSeq,"createdAt":100,"pinned":false,"deleted":false}"""

    private fun patchBody(id: String, pinned: Boolean, serverSeq: Long): String =
        """{"id":"$id","pinned":$pinned,"serverSeq":$serverSeq,"deleted":false}"""

    private fun deleteBody(id: String, serverSeq: Long): String =
        """{"id":"$id","deleted":true,"serverSeq":$serverSeq}"""

    private fun errorBody(code: String): String =
        """{"error":{"code":"$code","message":"test"}}"""

    private companion object {
        const val TOKEN = "test-device-token"
    }
}
