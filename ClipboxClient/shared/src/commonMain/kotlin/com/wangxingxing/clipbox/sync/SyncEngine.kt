/**
 * author : 王星星
 * date : 2026-11-17 11:28
 * email : 1099420259@qq.com
 * description : 增量拉取与全量重同步；按 serverSeq 分页应用变更并推进 meta.lastSeq，墓碑只删除无待发操作的本地记录。注意：lastSeq 只能由 nextSince 推进，写接口响应不得参与；syncMutex 必须与 OutboxWorker 使用同一实例，否则重放与拉取无法串行改本地库
 */
package com.wangxingxing.clipbox.sync

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.wangxingxing.clipbox.db.ClipDao
import com.wangxingxing.clipbox.db.ClipboxDatabase
import com.wangxingxing.clipbox.db.MetaDao
import com.wangxingxing.clipbox.db.MetaKeys
import com.wangxingxing.clipbox.db.OutboxDao
import com.wangxingxing.clipbox.model.ApiErrorCode
import com.wangxingxing.clipbox.model.ApiFailure
import com.wangxingxing.clipbox.model.ClipDto
import com.wangxingxing.clipbox.model.SyncResponse
import com.wangxingxing.clipbox.model.WsClipEvent
import com.wangxingxing.clipbox.network.ApiResult
import com.wangxingxing.clipbox.network.ApiService
import com.wangxingxing.clipbox.settings.SettingsRepository
import com.wangxingxing.clipbox.util.clipNowMillis
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SyncEngine(
    private val database: ClipboxDatabase,
    private val apiService: ApiService,
    private val settingsRepository: SettingsRepository,
    private val syncMutex: Mutex = Mutex(),
    private val statusReporter: SyncStatusReporter = SyncStatusReporter.None,
    private val clock: () -> Long = ::clipNowMillis,
) : SyncGateway {

    /**
     * 从 meta.lastSeq 拉取增量变更，遇到缺口或 `resyncRequired` 时自动全量重同步。
     * 返回的 [PullResult.Success.lastSeq] 是本次成功提交的游标；失败时游标停在最后一页成功值。
     */
    override suspend fun pull(): PullResult = syncMutex.withLock { pullLocked() }

    /**
     * 应用一条 WS 事件。事件不重放：只有 `seq == lastSeq + 1` 才写入记录并在同一事务推进游标；
     * `seq > lastSeq + 1` 视为缺口，在锁内执行一次 REST 补拉，事件是否已被覆盖由补拉后的游标判定。
     */
    override suspend fun applyEvent(event: WsClipEvent): SyncEventResult = syncMutex.withLock {
        val lastSeq = database.metaDao().get(MetaKeys.LAST_SEQ)
        when {
            event.seq <= lastSeq -> SyncEventResult.Ignored
            event.seq == lastSeq + 1 -> {
                applyEventItem(event.data, event.seq)
                SyncEventResult.Applied(event.seq)
            }

            else -> when (val result = pullLocked()) {
                is PullResult.Failure -> SyncEventResult.GapPullFailed(result.failure)
                PullResult.WaitingForPairing -> SyncEventResult.NotPaired
                is PullResult.Success -> {
                    val advanced = database.metaDao().get(MetaKeys.LAST_SEQ)
                    // 补拉通常已包含该事件；只有它恰好紧跟新游标时才单独应用，避免乱序推进
                    if (event.seq == advanced + 1) {
                        applyEventItem(event.data, event.seq)
                        SyncEventResult.Applied(event.seq)
                    } else {
                        SyncEventResult.GapPulled(advanced)
                    }
                }
            }
        }
    }

    private suspend fun pullLocked(): PullResult {
        if (settingsRepository.current().deviceToken.isNullOrEmpty()) return PullResult.WaitingForPairing

        var since = database.metaDao().get(MetaKeys.LAST_SEQ)
        // lastSeq=0 表示首次配对或本地已清空，整轮分页都是全量语义，续页同样要跳过 syncFloor 判定
        val fullSync = since == 0L
        var appliedItems = 0
        while (true) {
            val page = when (val result = apiService.getClips(since = since, limit = PAGE_LIMIT, full = fullSync)) {
                is ApiResult.Failure -> return PullResult.Failure(result.failure)
                is ApiResult.Success -> result.value
            }
            // 游标早于服务端可保证范围，或来自被重置的服务端（恢复旧备份）时，只能全量重拉
            if (page.resyncRequired || since > page.serverMaxSeq) {
                return resyncLocked()
            }
            appliedItems += applyPage(page, since)
            val advanced = page.nextSince > since
            since = page.nextSince
            if (!page.hasMore) break
            // 服务端承诺空页 hasMore=false；游标不再前进时直接退出，避免无限请求
            if (!advanced) break
        }
        database.clipDao().trimNonPinned()
        database.metaDao().put(MetaKeys.LAST_SYNC_AT, clock())
        return PullResult.Success(appliedItems = appliedItems, lastSeq = since, resynced = false)
    }

    // 重同步期间保留 Outbox 与待发 / 失败记录，只清空已同步记录后从 0 重拉
    private suspend fun resyncLocked(): PullResult {
        statusReporter.onResyncing()
        writeTransaction { clipDao, _, metaDao ->
            clipDao.deleteSynced()
            metaDao.put(MetaKeys.LAST_SEQ, 0)
        }

        var since = 0L
        var appliedItems = 0
        while (true) {
            // 全量分页的续页游标可能落在 sync_floor 之前，必须显式携带 full=true 才能继续拉取
            val page = when (val result = apiService.getClips(since = since, limit = PAGE_LIMIT, full = true)) {
                is ApiResult.Failure -> return PullResult.Failure(result.failure)
                is ApiResult.Success -> result.value
            }
            // full=true 下仍出现重同步说明游标超出 serverMaxSeq（服务端被重置），终止以免反复全量拉取
            if (page.resyncRequired) {
                return PullResult.Failure(ApiFailure.Retryable(ApiErrorCode.INVALID_RESPONSE))
            }
            appliedItems += applyPage(page, since)
            val advanced = page.nextSince > since
            since = page.nextSince
            if (!page.hasMore) break
            if (!advanced) break
        }
        database.clipDao().trimNonPinned()
        database.metaDao().put(MetaKeys.LAST_SYNC_AT, clock())
        return PullResult.Success(appliedItems = appliedItems, lastSeq = since, resynced = true)
    }

    // 整页应用与游标写入同事务：避免记录已写而游标未存导致重复拉取，或游标越过后丢失记录
    private suspend fun applyPage(page: SyncResponse, since: Long): Int =
        writeTransaction { clipDao, outboxDao, metaDao ->
            var applied = 0
            for (item in page.items) {
                if (applyItem(clipDao, outboxDao, item)) applied++
            }
            if (page.nextSince >= since) metaDao.put(MetaKeys.LAST_SEQ, page.nextSince)
            applied
        }

    // WS 事件与拉取共用同一条写入规则；游标与记录同事务推进，保证事件不重放
    private suspend fun applyEventItem(item: ClipDto, seq: Long) {
        writeTransaction { clipDao, outboxDao, metaDao ->
            applyItem(clipDao, outboxDao, item)
            metaDao.put(MetaKeys.LAST_SEQ, seq)
        }
    }

    // 返回是否真正写入或删除了本地记录；本地意图受保护而跳过时游标仍会前进
    private suspend fun applyItem(clipDao: ClipDao, outboxDao: OutboxDao, item: ClipDto): Boolean {
        val local = clipDao.getById(item.id)
        val ops = if (local == null) emptyList() else outboxDao.opsForClip(item.id)
        return when (val merged = ClipMerger.merge(local, item, ops)) {
            is MergeResult.Upsert -> {
                clipDao.upsert(merged.entity)
                true
            }

            MergeResult.Delete -> {
                clipDao.deleteById(item.id)
                true
            }

            MergeResult.Ignore -> false
        }
    }

    private suspend fun <T> writeTransaction(
        block: suspend (ClipDao, OutboxDao, MetaDao) -> T,
    ): T = database.useWriterConnection { transactor ->
        transactor.immediateTransaction {
            block(database.clipDao(), database.outboxDao(), database.metaDao())
        }
    }

    private companion object {
        const val PAGE_LIMIT = 200
    }
}

sealed interface PullResult {
    data class Success(
        val appliedItems: Int,
        val lastSeq: Long,
        val resynced: Boolean,
    ) : PullResult

    data class Failure(val failure: ApiFailure) : PullResult

    data object WaitingForPairing : PullResult
}

/**
 * WS 传输层与同步内核之间的窄接口：传输层只负责协议与重连，
 * 拉取与事件应用必须经过同一实现，才能保证共享同步锁与游标规则不被绕过。
 */
interface SyncGateway {
    suspend fun pull(): PullResult
    suspend fun applyEvent(event: WsClipEvent): SyncEventResult
}

sealed interface SyncEventResult {
    data class Applied(val seq: Long) : SyncEventResult
    data object Ignored : SyncEventResult

    /** 缺口补拉完成；游标按 nextSince 前进，事件本身不单独应用 */
    data class GapPulled(val lastSeq: Long) : SyncEventResult
    data class GapPullFailed(val failure: ApiFailure) : SyncEventResult
    data object NotPaired : SyncEventResult
}
