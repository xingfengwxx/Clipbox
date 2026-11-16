/**
 * author : 王星星
 * date : 2026-11-16 16:05
 * email : 1099420259@qq.com
 * description : clips 表 DAO 测试；覆盖历史列表裁剪与排序、搜索通配符转义、单条增删改与写事务回滚
 */
package com.wangxingxing.clipbox.db

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClipDaoTest {

    @Test
    fun historyKeepsAllPinnedAndLatestNonPinned() = runTest {
        val database = createInMemoryDatabase()
        try {
            val dao = database.clipDao()
            repeat(205) { index ->
                val createdAt = 10L + index
                dao.upsert(testClip(id = "synced-$createdAt", createdAt = createdAt))
            }
            dao.upsert(testClip(id = "pinned-1", createdAt = 1, pinned = true))
            dao.upsert(testClip(id = "pinned-2", createdAt = 2, pinned = true))
            dao.upsert(testClip(id = "pinned-3", createdAt = 3, pinned = true))

            val history = dao.observeHistory().first()

            assertEquals(203, history.size)
            assertEquals(listOf("pinned-3", "pinned-2", "pinned-1"), history.take(3).map { it.id })
            val nonPinned = history.drop(3)
            assertEquals(200, nonPinned.size)
            assertEquals("synced-214", nonPinned.first().id)
            assertEquals("synced-15", nonPinned.last().id)
            assertTrue(nonPinned.none { it.createdAt < 15 })
        } finally {
            database.close()
        }
    }

    @Test
    fun sameCreatedAtOrdersByIdDescending() = runTest {
        val database = createInMemoryDatabase()
        try {
            val dao = database.clipDao()
            dao.upsert(testClip(id = "clip-a", createdAt = 100))
            dao.upsert(testClip(id = "clip-b", createdAt = 100))

            val history = dao.observeHistory().first()

            assertEquals(listOf("clip-b", "clip-a"), history.map { it.id })
        } finally {
            database.close()
        }
    }

    @Test
    fun searchTreatsLikeWildcardsLiterally() = runTest {
        val database = createInMemoryDatabase()
        try {
            val dao = database.clipDao()
            dao.upsert(testClip(id = "ascii", createdAt = 1, content = "Hello World"))
            dao.upsert(testClip(id = "cjk", createdAt = 2, content = "中文 emoji 😀 测试"))
            dao.upsert(testClip(id = "percent", createdAt = 3, content = "100% done"))
            dao.upsert(testClip(id = "underscore", createdAt = 4, content = "a_b"))
            dao.upsert(testClip(id = "plain", createdAt = 5, content = "axb"))
            dao.upsert(
                testClip(id = "removed", createdAt = 6, content = "hello deleted", deleted = true),
            )

            assertEquals(listOf("ascii"), dao.search("hello").first().map { it.id })
            assertEquals(listOf("cjk"), dao.search("中文").first().map { it.id })
            assertEquals(listOf("cjk"), dao.search("😀").first().map { it.id })
            assertEquals(listOf("percent"), dao.search("%").first().map { it.id })
            assertEquals(listOf("underscore"), dao.search("_").first().map { it.id })
            assertEquals(listOf("underscore"), dao.search("a_b").first().map { it.id })
            assertTrue(dao.search("hello").first().none { it.id == "removed" })
        } finally {
            database.close()
        }
    }

    @Test
    fun upsertMarkDeletedAndDeleteByIdFollowLifecycle() = runTest {
        val database = createInMemoryDatabase()
        try {
            val dao = database.clipDao()
            dao.upsert(testClip(id = "clip-1", createdAt = 1, content = "draft"))

            dao.upsert(testClip(id = "clip-1", createdAt = 1, content = "final", pinned = true))
            val updated = dao.getById("clip-1")
            assertNotNull(updated)
            assertEquals("final", updated.content)
            assertTrue(updated.pinned)

            dao.markDeleted("clip-1")
            assertTrue(dao.observeHistory().first().isEmpty())

            dao.deleteById("clip-1")
            assertNull(dao.getById("clip-1"))
        } finally {
            database.close()
        }
    }

    @Test
    fun writeTransactionRollsBackOnFailure() = runTest {
        val database = createInMemoryDatabase()
        try {
            val dao = database.clipDao()

            assertFailsWith<IllegalStateException> {
                database.useWriterConnection { transactor ->
                    transactor.immediateTransaction {
                        dao.upsert(testClip(id = "rolled-back", createdAt = 1))
                        throw IllegalStateException("rollback")
                    }
                }
            }

            assertNull(dao.getById("rolled-back"))
            assertTrue(dao.observeHistory().first().isEmpty())
        } finally {
            database.close()
        }
    }
}
