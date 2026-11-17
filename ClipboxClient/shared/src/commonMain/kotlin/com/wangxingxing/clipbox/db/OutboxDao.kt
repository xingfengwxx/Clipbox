/**
 * author : 王星星
 * date : 2026-11-16 15:52
 * email : 1099420259@qq.com
 * description : outbox 表的到期读取、按记录取操作、载荷合并、重试标记与失败统计；重放协程只通过这里取任务，避免多处各自排序
 */
package com.wangxingxing.clipbox.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboxDao {

    @Insert
    suspend fun enqueue(op: OutboxEntity): Long

    @Query("SELECT * FROM outbox WHERE nextRetryAt <= :now ORDER BY opId ASC")
    suspend fun nextDue(now: Long): List<OutboxEntity>

    // 同一记录的未发送操作按入队顺序返回，用于合并 PATCH 与幂等重试
    @Query("SELECT * FROM outbox WHERE clipId = :clipId ORDER BY opId ASC")
    suspend fun opsForClip(clipId: String): List<OutboxEntity>

    @Query("UPDATE outbox SET payload = :payload WHERE opId = :opId")
    suspend fun updatePayload(opId: Long, payload: String)

    @Query("DELETE FROM outbox WHERE opId = :opId")
    suspend fun delete(opId: Long)

    @Query(
        """
        UPDATE outbox
        SET retryCount = :retryCount, nextRetryAt = :nextRetryAt, lastErrorCode = :errorCode
        WHERE opId = :opId
        """
    )
    suspend fun markRetry(opId: Long, retryCount: Int, nextRetryAt: Long, errorCode: String?)

    @Query("SELECT COUNT(*) FROM outbox WHERE lastErrorCode IS NOT NULL")
    suspend fun countFailed(): Int

    // 状态条按记录数而非操作数统计，避免同一条记录的多笔操作被重复计数
    @Query("SELECT COUNT(DISTINCT clipId) FROM outbox WHERE lastErrorCode IS NULL")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(DISTINCT clipId) FROM outbox WHERE lastErrorCode IS NOT NULL")
    fun observeFailedCount(): Flow<Int>
}
