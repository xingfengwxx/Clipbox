/**
 * author : 王星星
 * date : 2026-11-16 16:05
 * email : 1099420259@qq.com
 * description : outbox 表 DAO 测试；验证入队顺序、到期过滤、同一记录多操作保序、重试标记与失败统计
 */
package com.wangxingxing.clipbox.db

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OutboxDaoTest {

    @Test
    fun nextDueReturnsOpsInEnqueueOrderAndSkipsFutureRetries() = runTest {
        val database = createInMemoryDatabase()
        try {
            val dao = database.outboxDao()
            val first = dao.enqueue(testOp("clip-1", OutboxOpType.CREATE, createdAt = 1))
            val second = dao.enqueue(
                testOp("clip-1", OutboxOpType.PATCH_PIN, createdAt = 2, nextRetryAt = 500),
            )
            val third = dao.enqueue(testOp("clip-2", OutboxOpType.CREATE, createdAt = 3))

            val due = dao.nextDue(now = 100)

            assertEquals(listOf(first, third), due.map { it.opId })
            assertTrue(second > first)
        } finally {
            database.close()
        }
    }

    @Test
    fun sameClipOperationsKeepEnqueueOrder() = runTest {
        val database = createInMemoryDatabase()
        try {
            val dao = database.outboxDao()
            dao.enqueue(testOp("clip-1", OutboxOpType.CREATE, createdAt = 1))
            dao.enqueue(testOp("clip-1", OutboxOpType.PATCH_PIN, createdAt = 2))
            dao.enqueue(testOp("clip-1", OutboxOpType.DELETE, createdAt = 3))

            val due = dao.nextDue(now = 0)

            assertEquals(
                listOf(OutboxOpType.CREATE, OutboxOpType.PATCH_PIN, OutboxOpType.DELETE),
                due.map { it.opType },
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun markRetryUpdatesScheduleAndFailureCount() = runTest {
        val database = createInMemoryDatabase()
        try {
            val dao = database.outboxDao()
            val opId = dao.enqueue(testOp("clip-1", OutboxOpType.CREATE, createdAt = 1))

            dao.markRetry(
                opId = opId,
                retryCount = 3,
                nextRetryAt = 2_000,
                errorCode = "RATE_LIMITED",
            )

            assertEquals(1, dao.countFailed())
            assertTrue(dao.nextDue(now = 1_999).isEmpty())
            val retried = dao.nextDue(now = 2_000).single()
            assertEquals(3, retried.retryCount)
            assertEquals("RATE_LIMITED", retried.lastErrorCode)
        } finally {
            database.close()
        }
    }

    @Test
    fun deleteRemovesOnlyTargetOp() = runTest {
        val database = createInMemoryDatabase()
        try {
            val dao = database.outboxDao()
            val first = dao.enqueue(testOp("clip-1", OutboxOpType.CREATE, createdAt = 1))
            val second = dao.enqueue(testOp("clip-2", OutboxOpType.CREATE, createdAt = 2))

            dao.delete(first)

            assertEquals(listOf(second), dao.nextDue(now = 0).map { it.opId })
            assertEquals(0, dao.countFailed())
        } finally {
            database.close()
        }
    }
}

private fun testOp(
    clipId: String,
    opType: OutboxOpType,
    createdAt: Long,
    nextRetryAt: Long = 0,
): OutboxEntity = OutboxEntity(
    clipId = clipId,
    opType = opType,
    payload = "{}",
    nextRetryAt = nextRetryAt,
    createdAt = createdAt,
)
