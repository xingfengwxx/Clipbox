/**
 * author : 王星星
 * date : 2026-11-17 14:32
 * email : 1099420259@qq.com
 * description : SyncRepository 单飞测试；验证并发调用共享一次执行、完成后可再次同步、失败原样返回以及最近同步时间流
 */
package com.wangxingxing.clipbox.repository

import com.wangxingxing.clipbox.db.MetaDao
import com.wangxingxing.clipbox.db.MetaKeys
import com.wangxingxing.clipbox.model.ApiFailure
import com.wangxingxing.clipbox.sync.SyncNowResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SyncRepositoryTest {

    @Test
    fun concurrentCallsShareOneExecution() = runTest {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val result = SyncNowResult.Success(appliedItems = 2, resynced = false, outboxApplied = 1)
        val repository = SyncRepository(FakeMetaDao()) {
            calls++
            gate.await()
            result
        }

        val first = async { repository.syncNow() }
        runCurrent()
        val second = async { repository.syncNow() }
        runCurrent()

        assertEquals(1, calls)
        gate.complete(Unit)
        assertEquals(result, first.await())
        assertEquals(result, second.await())
    }

    @Test
    fun completedSyncCanRunAgain() = runTest {
        var calls = 0
        val repository = SyncRepository(FakeMetaDao()) {
            calls++
            SyncNowResult.Success(appliedItems = calls, resynced = false, outboxApplied = 0)
        }

        assertEquals(1, (repository.syncNow() as SyncNowResult.Success).appliedItems)
        assertEquals(2, (repository.syncNow() as SyncNowResult.Success).appliedItems)
        assertEquals(2, calls)
    }

    @Test
    fun failureIsReturnedWithoutTranslation() = runTest {
        val repository = SyncRepository(FakeMetaDao()) {
            SyncNowResult.Failure(ApiFailure.Offline)
        }

        assertEquals(SyncNowResult.Failure(ApiFailure.Offline), repository.syncNow())
    }

    @Test
    fun lastSyncAtIsNullBeforeFirstSuccessAndUpdatesAfterMetaWrite() = runTest {
        val meta = FakeMetaDao()
        val repository = SyncRepository(meta) {
            SyncNowResult.WaitingForPairing
        }

        assertEquals(null, repository.lastSyncAt.first())

        meta.put(MetaKeys.LAST_SYNC_AT, 1_700_000_000_000)
        assertEquals(1_700_000_000_000, repository.lastSyncAt.first())
    }

    private class FakeMetaDao : MetaDao {
        private val values = MutableStateFlow<Map<String, Long>>(emptyMap())

        override suspend fun get(key: String): Long = values.value[key] ?: 0

        override fun observe(key: String): Flow<Long?> = values.map { it[key] }

        override suspend fun put(key: String, value: Long) {
            values.value = values.value + (key to value)
        }

        override suspend fun clearAll() {
            values.value = emptyMap()
        }
    }
}
