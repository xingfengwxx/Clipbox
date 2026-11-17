/**
 * author : 王星星
 * date : 2026-11-17 14:25
 * email : 1099420259@qq.com
 * description : 手动同步入口；把并发 syncNow 合并为一次执行，并向 UI 暴露最近一次成功同步时间。注意：单飞只合并仍在执行的调用，执行结束后再次调用会重新发起
 */
package com.wangxingxing.clipbox.repository

import com.wangxingxing.clipbox.db.MetaDao
import com.wangxingxing.clipbox.db.MetaKeys
import com.wangxingxing.clipbox.sync.SyncNowResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

fun interface SyncNowRunner {
    suspend fun runSync(): SyncNowResult
}

class SyncRepository(
    private val metaDao: MetaDao,
    private val runner: SyncNowRunner,
) {

    private val gate = Mutex()
    private var inFlight: CompletableDeferred<SyncNowResult>? = null

    /** null 表示从未成功同步；0 是有效时间戳，不应被当成空值 */
    val lastSyncAt: Flow<Long?> = metaDao.observe(MetaKeys.LAST_SYNC_AT)
        .map { value -> value?.takeIf { it > 0 } }

    suspend fun syncNow(): SyncNowResult {
        val (flight, owner) = gate.withLock {
            val active = inFlight
            if (active != null) {
                active to false
            } else {
                CompletableDeferred<SyncNowResult>().also { inFlight = it } to true
            }
        }
        if (!owner) return flight.await()

        try {
            flight.complete(runner.runSync())
        } catch (failure: Throwable) {
            flight.completeExceptionally(failure)
            throw failure
        } finally {
            gate.withLock {
                if (inFlight === flight) inFlight = null
            }
        }
        return flight.await()
    }
}
