/**
 * author : 王星星
 * date : 2026-11-16 16:05
 * email : 1099420259@qq.com
 * description : 本地裁剪测试；只允许删除已同步的非置顶记录，不得触碰置顶、待同步、失败记录或 outbox
 */
package com.wangxingxing.clipbox.db

import com.wangxingxing.clipbox.model.SyncState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrimTest {

    @Test
    fun trimOnlyRemovesOldestSyncedNonPinnedClips() = runTest {
        val database = createInMemoryDatabase()
        try {
            val clipDao = database.clipDao()
            val outboxDao = database.outboxDao()

            repeat(205) { index ->
                val createdAt = 1L + index
                clipDao.upsert(testClip(id = "synced-$createdAt", createdAt = createdAt))
            }
            clipDao.upsert(testClip(id = "pinned-1", createdAt = 1, pinned = true))
            clipDao.upsert(testClip(id = "pinned-2", createdAt = 2, pinned = true))
            clipDao.upsert(testClip(id = "pinned-3", createdAt = 3, pinned = true))
            clipDao.upsert(
                testClip(id = "pending-1", createdAt = 4, syncState = SyncState.PENDING_CREATE),
            )
            clipDao.upsert(
                testClip(id = "pending-2", createdAt = 5, syncState = SyncState.PENDING_UPDATE),
            )
            clipDao.upsert(
                testClip(id = "failed-1", createdAt = 6, syncState = SyncState.FAILED_CREATE),
            )
            outboxDao.enqueue(
                OutboxEntity(
                    clipId = "pending-1",
                    opType = OutboxOpType.CREATE,
                    payload = "{}",
                    createdAt = 1,
                ),
            )
            outboxDao.enqueue(
                OutboxEntity(
                    clipId = "pending-2",
                    opType = OutboxOpType.PATCH_PIN,
                    payload = "{}",
                    createdAt = 2,
                ),
            )

            val before = clipDao.search("").first()
            assertEquals(211, before.size)
            val outboxBefore = outboxDao.nextDue(now = Long.MAX_VALUE).size
            assertEquals(2, outboxBefore)

            val removed = clipDao.trimNonPinned()

            assertEquals(5, removed)
            val after = clipDao.search("").first()
            assertEquals(206, after.size)
            val keptIds = after.map { it.id }.toSet()
            val removedIds = before.map { it.id }.toSet() - keptIds
            assertEquals(
                setOf("synced-1", "synced-2", "synced-3", "synced-4", "synced-5"),
                removedIds,
            )
            assertTrue(keptIds.containsAll(listOf("pinned-1", "pinned-2", "pinned-3")))
            assertTrue(keptIds.containsAll(listOf("pending-1", "pending-2", "failed-1")))
            assertEquals(200, after.count { it.id.startsWith("synced-") })
            assertEquals(outboxBefore, outboxDao.nextDue(now = Long.MAX_VALUE).size)
        } finally {
            database.close()
        }
    }

    @Test
    fun trimIsNoOpWhenWithinLimit() = runTest {
        val database = createInMemoryDatabase()
        try {
            val clipDao = database.clipDao()
            repeat(10) { index ->
                clipDao.upsert(testClip(id = "synced-$index", createdAt = index.toLong()))
            }

            assertEquals(0, clipDao.trimNonPinned())

            assertEquals(10, clipDao.search("").first().size)
        } finally {
            database.close()
        }
    }
}
