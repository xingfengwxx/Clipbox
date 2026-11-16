/**
 * author : 王星星
 * date : 2026-11-16 15:52
 * email : 1099420259@qq.com
 * description : outbox 表的到期读取、重试标记与失败统计；重放协程只通过这里取任务，避免多处各自排序
 */
package com.wangxingxing.clipbox.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface OutboxDao {

    @Insert
    suspend fun enqueue(op: OutboxEntity): Long

    @Query("SELECT * FROM outbox WHERE nextRetryAt <= :now ORDER BY opId ASC")
    suspend fun nextDue(now: Long): List<OutboxEntity>

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
}
