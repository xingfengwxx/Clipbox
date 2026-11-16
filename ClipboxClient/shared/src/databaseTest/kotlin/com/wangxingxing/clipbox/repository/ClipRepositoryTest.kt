/**
 * author : 王星星
 * date : 2026-11-16 16:14
 * email : 1099420259@qq.com
 * description : ClipRepository 数据库测试；覆盖本地立即可见、内容校验、置顶合并、删除幂等、失败重试与写事务回滚
 */
package com.wangxingxing.clipbox.repository

import com.wangxingxing.clipbox.db.ClipboxDatabase
import com.wangxingxing.clipbox.db.OutboxEntity
import com.wangxingxing.clipbox.db.OutboxOpType
import com.wangxingxing.clipbox.db.createInMemoryDatabase
import com.wangxingxing.clipbox.db.testClip
import com.wangxingxing.clipbox.model.CreateClipRequest
import com.wangxingxing.clipbox.model.PatchPinnedRequest
import com.wangxingxing.clipbox.model.SyncState
import com.wangxingxing.clipbox.settings.FakePreferencesDataStore
import com.wangxingxing.clipbox.settings.SettingsRepository
import com.wangxingxing.clipbox.util.TextSize
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClipRepositoryTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun sendInsertsPendingClipAndCreateOpImmediately() = runTest {
        withDatabase { database ->
            val repository = createRepository(database)

            val result = repository.send("  hello\nworld  ")

            val success = assertIs<SendResult.Success>(result)
            val clip = repository.observeClips().first().single()
            assertEquals(success.clipId, clip.id)
            assertEquals("hello\nworld", clip.content)
            assertEquals(SyncState.PENDING_CREATE, clip.syncState)
            assertEquals(0, clip.serverSeq)
            assertEquals("device-1", clip.sourceDeviceId)
            assertEquals("Pixel", clip.sourceDeviceName)

            val op = database.outboxDao().opsForClip(clip.id).single()
            assertEquals(OutboxOpType.CREATE, op.opType)
            assertEquals(
                CreateClipRequest(id = clip.id, content = "hello\nworld", createdAt = FIXED_NOW),
                json.decodeFromString<CreateClipRequest>(op.payload),
            )
        }
    }

    @Test
    fun sendRejectsBlankAndKeepsNoRows() = runTest {
        withDatabase { database ->
            val repository = createRepository(database)

            assertEquals(SendResult.Empty, repository.send(" \n\t\u3000 "))
            assertTrue(repository.observeClips().first().isEmpty())
            assertTrue(database.outboxDao().nextDue(now = 0).isEmpty())
        }
    }

    @Test
    fun sendAcceptsExactly64KiBAndRejectsOneMoreByte() = runTest {
        withDatabase { database ->
            val repository = createRepository(database)

            assertIs<SendResult.Success>(
                repository.send("a".repeat(TextSize.MAX_CLIP_UTF8_BYTES)),
            )
            assertEquals(
                SendResult.TooLarge,
                repository.send("a".repeat(TextSize.MAX_CLIP_UTF8_BYTES + 1)),
            )
            assertEquals(1, repository.observeClips().first().size)
        }
    }

    @Test
    fun pinOnSyncedClipUpdatesLocallyAndEnqueuesPatch() = runTest {
        withDatabase { database ->
            val repository = createRepository(database)
            database.clipDao().upsert(testClip(id = "synced", createdAt = 1))

            assertTrue(repository.pin("synced", true))

            val clip = assertNotNull(database.clipDao().getById("synced"))
            assertTrue(clip.pinned)
            assertEquals(SyncState.PENDING_UPDATE, clip.syncState)
            val op = database.outboxDao().opsForClip("synced").single()
            assertEquals(OutboxOpType.PATCH_PIN, op.opType)
            assertEquals(
                PatchPinnedRequest(pinned = true),
                json.decodeFromString<PatchPinnedRequest>(op.payload),
            )
        }
    }

    @Test
    fun repeatedPinKeepsSinglePatchWithFinalValue() = runTest {
        withDatabase { database ->
            val repository = createRepository(database)
            val clipId = assertIs<SendResult.Success>(repository.send("hello")).clipId

            assertTrue(repository.pin(clipId, true))
            assertTrue(repository.pin(clipId, false))
            assertTrue(repository.pin(clipId, true))
            assertTrue(repository.pin(clipId, true))

            val ops = database.outboxDao().opsForClip(clipId)
            assertEquals(listOf(OutboxOpType.CREATE, OutboxOpType.PATCH_PIN), ops.map { it.opType })
            assertEquals(
                PatchPinnedRequest(pinned = true),
                json.decodeFromString<PatchPinnedRequest>(ops.last().payload),
            )
            val clip = assertNotNull(database.clipDao().getById(clipId))
            assertTrue(clip.pinned)
            assertEquals(SyncState.PENDING_CREATE, clip.syncState)
        }
    }

    @Test
    fun deleteHidesLocallyAndEnqueuesSingleDelete() = runTest {
        withDatabase { database ->
            val repository = createRepository(database)
            val clipId = assertIs<SendResult.Success>(repository.send("hello")).clipId

            assertTrue(repository.delete(clipId))
            assertTrue(repository.delete(clipId))

            assertTrue(repository.observeClips().first().isEmpty())
            val clip = assertNotNull(database.clipDao().getById(clipId))
            assertTrue(clip.deleted)
            assertEquals(SyncState.PENDING_DELETE, clip.syncState)
            assertEquals(
                listOf(OutboxOpType.CREATE, OutboxOpType.DELETE),
                database.outboxDao().opsForClip(clipId).map { it.opType },
            )
        }
    }

    @Test
    fun retryFailedDeleteReArmsDeleteWithoutDuplicating() = runTest {
        withDatabase { database ->
            val repository = createRepository(database)
            database.clipDao().upsert(
                testClip(id = "failed", createdAt = 1, syncState = SyncState.FAILED_DELETE, deleted = true)
                    .copy(lastErrorCode = "NOT_FOUND", lastErrorAt = 5),
            )
            database.outboxDao().enqueue(
                OutboxEntity(
                    clipId = "failed",
                    opType = OutboxOpType.DELETE,
                    payload = "{}",
                    retryCount = 3,
                    nextRetryAt = 9_999,
                    createdAt = 1,
                    lastErrorCode = "NOT_FOUND",
                ),
            )

            assertTrue(repository.retry("failed"))

            val clip = assertNotNull(database.clipDao().getById("failed"))
            assertEquals(SyncState.PENDING_DELETE, clip.syncState)
            assertNull(clip.lastErrorCode)
            assertNull(clip.lastErrorAt)
            val op = database.outboxDao().opsForClip("failed").single()
            assertEquals(OutboxOpType.DELETE, op.opType)
            assertEquals(0, op.retryCount)
            assertEquals(0, op.nextRetryAt)
            assertNull(op.lastErrorCode)
        }
    }

    @Test
    fun retryIgnoresRecordWithoutPermanentFailure() = runTest {
        withDatabase { database ->
            val repository = createRepository(database)
            val clipId = assertIs<SendResult.Success>(repository.send("hello")).clipId

            assertFalse(repository.retry(clipId))
            assertEquals(1, database.outboxDao().opsForClip(clipId).size)
        }
    }

    @Test
    fun writeTransactionRollsBackClipsAndOutboxTogether() = runTest {
        withDatabase { database ->
            val repository = createRepository(database)

            assertFailsWith<IllegalStateException> {
                repository.writeInTransaction { clipDao, outboxDao ->
                    clipDao.upsert(testClip(id = "rolled-back", createdAt = 1))
                    outboxDao.enqueue(
                        OutboxEntity(
                            clipId = "rolled-back",
                            opType = OutboxOpType.CREATE,
                            payload = "{}",
                            createdAt = 1,
                        ),
                    )
                    throw IllegalStateException("rollback")
                }
            }

            assertNull(database.clipDao().getById("rolled-back"))
            assertTrue(database.outboxDao().nextDue(now = 0).isEmpty())
        }
    }

    private suspend fun createRepository(database: ClipboxDatabase): ClipRepository {
        val settings = SettingsRepository(FakePreferencesDataStore(), diagnosticLoggingDefault = false)
        settings.setPairingInfo(
            deviceToken = "token",
            deviceId = "device-1",
            deviceName = "Pixel",
            lastServerUrl = "http://localhost:8080",
        )
        return ClipRepository(database, settings, clock = { FIXED_NOW })
    }

    private suspend fun withDatabase(block: suspend (ClipboxDatabase) -> Unit) {
        val database = createInMemoryDatabase()
        try {
            block(database)
        } finally {
            database.close()
        }
    }

    private companion object {
        const val FIXED_NOW = 1_000L
    }
}
