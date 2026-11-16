/**
 * author : 王星星
 * date : 2026-11-16 16:14
 * email : 1099420259@qq.com
 * description : Outbox 入队不变量测试；验证一记录一操作、同一记录操作保序、PATCH 合并且重试不追加重复项
 */
package com.wangxingxing.clipbox.db

import com.wangxingxing.clipbox.model.CreateClipRequest
import com.wangxingxing.clipbox.model.PatchPinnedRequest
import com.wangxingxing.clipbox.model.SyncState
import com.wangxingxing.clipbox.repository.ClipRepository
import com.wangxingxing.clipbox.repository.SendResult
import com.wangxingxing.clipbox.settings.FakePreferencesDataStore
import com.wangxingxing.clipbox.settings.SettingsRepository
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OutboxEnqueueTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun eachSendCreatesOneCreateOpForDistinctClip() = runTest {
        withDatabase { database, repository ->
            val first = assertIs<SendResult.Success>(repository.send("first"))
            val second = assertIs<SendResult.Success>(repository.send("second"))

            assertTrue(first.clipId != second.clipId)
            val ops = database.outboxDao().nextDue(now = 0)
            assertEquals(2, ops.size)
            assertEquals(listOf(OutboxOpType.CREATE, OutboxOpType.CREATE), ops.map { it.opType })
            assertEquals(setOf(first.clipId, second.clipId), ops.map { it.clipId }.toSet())
        }
    }

    @Test
    fun sameClipOperationsKeepEnqueueOrder() = runTest {
        withDatabase { database, repository ->
            val clipId = assertIs<SendResult.Success>(repository.send("hello")).clipId

            assertTrue(repository.pin(clipId, true))
            assertTrue(repository.delete(clipId))

            val ops = database.outboxDao().opsForClip(clipId)
            assertEquals(
                listOf(OutboxOpType.CREATE, OutboxOpType.PATCH_PIN, OutboxOpType.DELETE),
                ops.map { it.opType },
            )
            assertEquals(ops.map { it.opId }.sorted(), ops.map { it.opId })
        }
    }

    @Test
    fun pinCoalescesUnsentPatchIntoLatestValue() = runTest {
        withDatabase { database, repository ->
            val clipId = assertIs<SendResult.Success>(repository.send("hello")).clipId

            assertTrue(repository.pin(clipId, true))
            assertTrue(repository.pin(clipId, false))

            val ops = database.outboxDao().opsForClip(clipId)
            assertEquals(2, ops.size)
            val patch = ops.single { it.opType == OutboxOpType.PATCH_PIN }
            assertEquals(
                PatchPinnedRequest(pinned = false),
                json.decodeFromString<PatchPinnedRequest>(patch.payload),
            )
        }
    }

    @Test
    fun retryFailedUpdateReusesSinglePatch() = runTest {
        withDatabase { database, repository ->
            database.clipDao().upsert(
                testClip(id = "failed-update", createdAt = 1, pinned = true, syncState = SyncState.FAILED_UPDATE)
                    .copy(lastErrorCode = "VALIDATION_ERROR", lastErrorAt = 2),
            )
            database.outboxDao().enqueue(
                outboxOp(
                    clipId = "failed-update",
                    opType = OutboxOpType.PATCH_PIN,
                    payload = json.encodeToString(PatchPinnedRequest(pinned = false)),
                    retryCount = 2,
                    nextRetryAt = 5_000,
                    errorCode = "VALIDATION_ERROR",
                ),
            )

            assertTrue(repository.retry("failed-update"))

            val clip = assertNotNull(database.clipDao().getById("failed-update"))
            assertEquals(SyncState.PENDING_UPDATE, clip.syncState)
            val op = database.outboxDao().opsForClip("failed-update").single()
            assertEquals(OutboxOpType.PATCH_PIN, op.opType)
            assertEquals(0, op.retryCount)
            assertEquals(0, op.nextRetryAt)
            assertEquals(
                PatchPinnedRequest(pinned = true),
                json.decodeFromString<PatchPinnedRequest>(op.payload),
            )
        }
    }

    @Test
    fun retryFailedCreateReenqueuesMissingCreateFromLocalRecord() = runTest {
        withDatabase { database, repository ->
            val clip = testClip(id = "failed-create", createdAt = 7, syncState = SyncState.FAILED_CREATE)
                .copy(content = "kept locally", lastErrorCode = "VALIDATION_ERROR", lastErrorAt = 8)
            database.clipDao().upsert(clip)

            assertTrue(repository.retry("failed-create"))

            val op = database.outboxDao().opsForClip("failed-create").single()
            assertEquals(OutboxOpType.CREATE, op.opType)
            assertEquals(
                CreateClipRequest(id = "failed-create", content = "kept locally", createdAt = 7),
                json.decodeFromString<CreateClipRequest>(op.payload),
            )
            assertEquals(SyncState.PENDING_CREATE, assertNotNull(database.clipDao().getById("failed-create")).syncState)
        }
    }

    private suspend fun withDatabase(block: suspend (ClipboxDatabase, ClipRepository) -> Unit) {
        val database = createInMemoryDatabase()
        try {
            val settings = SettingsRepository(FakePreferencesDataStore(), diagnosticLoggingDefault = false)
            settings.setPairingInfo(
                deviceToken = "token",
                deviceId = "device-1",
                deviceName = "Pixel",
                lastServerUrl = "http://localhost:8080",
            )
            block(database, ClipRepository(database, settings, clock = { 1_000 }))
        } finally {
            database.close()
        }
    }

    private fun outboxOp(
        clipId: String,
        opType: OutboxOpType,
        payload: String,
        retryCount: Int,
        nextRetryAt: Long,
        errorCode: String?,
    ): OutboxEntity = OutboxEntity(
        clipId = clipId,
        opType = opType,
        payload = payload,
        retryCount = retryCount,
        nextRetryAt = nextRetryAt,
        createdAt = 1,
        lastErrorCode = errorCode,
    )
}
