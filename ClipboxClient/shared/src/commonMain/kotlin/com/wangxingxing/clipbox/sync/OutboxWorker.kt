/**
 * author : 王星星
 * date : 2026-11-17 11:22
 * email : 1099420259@qq.com
 * description : Outbox 重放协程：按 nextRetryAt 取到期操作，成功后写本地并出队，网络 / 5xx / 429 退避重试，401 暂停等待重新配对，400 / 409 / 413 / 415 标记 FAILED_* 并停止自动重试。注意：同一条记录必须严格按 opId 顺序发送；网络请求不持有数据库事务，写库与出队在同一事务内完成
 */
package com.wangxingxing.clipbox.sync

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.wangxingxing.clipbox.db.ClipDao
import com.wangxingxing.clipbox.db.ClipboxDatabase
import com.wangxingxing.clipbox.db.OutboxDao
import com.wangxingxing.clipbox.db.OutboxEntity
import com.wangxingxing.clipbox.db.OutboxOpType
import com.wangxingxing.clipbox.model.ApiErrorCode
import com.wangxingxing.clipbox.model.ApiFailure
import com.wangxingxing.clipbox.model.CreateClipRequest
import com.wangxingxing.clipbox.model.PatchPinnedRequest
import com.wangxingxing.clipbox.model.SyncState
import com.wangxingxing.clipbox.network.ApiResult
import com.wangxingxing.clipbox.network.ApiService
import com.wangxingxing.clipbox.settings.SettingsRepository
import com.wangxingxing.clipbox.util.clipNowMillis
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.random.Random

class OutboxWorker(
    private val database: ClipboxDatabase,
    private val apiService: ApiService,
    private val settingsRepository: SettingsRepository,
    private val clock: () -> Long = ::clipNowMillis,
    private val jitterSample: () -> Double = { Random.nextDouble() },
    private val syncMutex: Mutex = Mutex(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    private val payloadJson = Json { ignoreUnknownKeys = true }
    private val wakeUps = Channel<Unit>(Channel.CONFLATED)
    private val requiresPairingState = MutableStateFlow(false)
    private var loopJob: Job? = null

    /** 401 后为 true；重新配对成功后调用 [onPaired] 清除 */
    val requiresPairing: StateFlow<Boolean> = requiresPairingState.asStateFlow()

    /** 与增量拉取、全量重同步共享的同步锁，保证三者串行改本地库 */
    val mutex: Mutex get() = syncMutex

    fun start(scope: CoroutineScope) {
        if (loopJob?.isActive == true) return
        loopJob = scope.launch(dispatcher) { runLoop() }
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
    }

    /** 入队、网络恢复或手动同步后调用，立即唤醒等待中的循环 */
    fun requestReplay() {
        wakeUps.trySend(Unit)
    }

    /** 重新配对成功后调用：解除 401 暂停并立即尝试重放 */
    fun onPaired() {
        requiresPairingState.value = false
        requestReplay()
    }

    /**
     * 执行一轮到期操作；返回本轮成功数与下一次可执行时间。
     * 未配对或 401 暂停时 [OutboxRunReport.waitingForPairing] 为 true，且不产生任何网络请求。
     */
    suspend fun replayDue(): OutboxRunReport = syncMutex.withLock { replayLocked() }

    private suspend fun replayLocked(): OutboxRunReport {
        if (requiresPairingState.value) return OutboxRunReport(applied = 0, waitingForPairing = true, nextDueAt = null)
        if (settingsRepository.current().deviceToken.isNullOrEmpty()) {
            return OutboxRunReport(applied = 0, waitingForPairing = true, nextDueAt = null)
        }

        val now = clock()
        var applied = 0
        for (op in database.outboxDao().nextDue(now)) {
            // 前序操作未出队时本轮跳过，保证同一记录严格按入队顺序发送
            if (isBlocked(op)) continue
            when (execute(op, now)) {
                OpResult.Applied -> applied++
                OpResult.Unauthorized -> return OutboxRunReport(applied, waitingForPairing = true, nextDueAt = null)
                OpResult.RetryScheduled, OpResult.FailedPermanently -> Unit
            }
        }
        return OutboxRunReport(applied = applied, waitingForPairing = false, nextDueAt = nextScheduledAt())
    }

    private suspend fun isBlocked(op: OutboxEntity): Boolean =
        database.outboxDao().opsForClip(op.clipId).any { it.opId < op.opId }

    private suspend fun execute(op: OutboxEntity, now: Long): OpResult = when (op.opType) {
        OutboxOpType.CREATE -> executeCreate(op, now)
        OutboxOpType.PATCH_PIN -> executePatch(op, now)
        OutboxOpType.DELETE -> executeDelete(op, now)
    }

    private suspend fun executeCreate(op: OutboxEntity, now: Long): OpResult {
        val request = payloadJson.decodeFromString<CreateClipRequest>(op.payload)
        return when (val result = apiService.createClip(request.id, request.content, request.createdAt)) {
            is ApiResult.Success -> {
                val response = result.value
                writeTransaction { clipDao, outboxDao ->
                    outboxDao.delete(op.opId)
                    val clip = clipDao.getById(op.clipId) ?: return@writeTransaction
                    val remaining = outboxDao.opsForClip(op.clipId)
                    val patchPending = remaining.any { it.opType == OutboxOpType.PATCH_PIN }
                    clipDao.upsert(
                        clip.copy(
                            serverSeq = response.serverSeq,
                            // 本地还有待发 PATCH 时保留本地置顶意图，不被 POST 的旧值覆盖
                            pinned = if (patchPending) clip.pinned else response.pinned,
                            syncState = stateAfterSuccess(remaining),
                            lastErrorCode = null,
                            lastErrorAt = null,
                        )
                    )
                }
                OpResult.Applied
            }

            is ApiResult.Failure -> handleFailure(op, result.failure, now)
        }
    }

    private suspend fun executePatch(op: OutboxEntity, now: Long): OpResult {
        val request = payloadJson.decodeFromString<PatchPinnedRequest>(op.payload)
        return when (val result = apiService.updateClip(op.clipId, request.pinned)) {
            is ApiResult.Success -> {
                val response = result.value
                writeTransaction { clipDao, outboxDao ->
                    outboxDao.delete(op.opId)
                    val clip = clipDao.getById(op.clipId) ?: return@writeTransaction
                    clipDao.upsert(
                        clip.copy(
                            serverSeq = response.serverSeq,
                            pinned = response.pinned,
                            syncState = stateAfterSuccess(outboxDao.opsForClip(op.clipId)),
                            lastErrorCode = null,
                            lastErrorAt = null,
                        )
                    )
                }
                OpResult.Applied
            }

            is ApiResult.Failure -> handleFailure(op, result.failure, now)
        }
    }

    private suspend fun executeDelete(op: OutboxEntity, now: Long): OpResult =
        when (val result = apiService.deleteClip(op.clipId)) {
            is ApiResult.Success -> {
                removeLocalClip(op)
                OpResult.Applied
            }

            is ApiResult.Failure -> {
                val failure = result.failure
                // 服务端从未有过该记录等价于删除成功，避免失败项永远卡在队列里
                if (failure is ApiFailure.Permanent && failure.code == ApiErrorCode.NOT_FOUND) {
                    removeLocalClip(op)
                    OpResult.Applied
                } else {
                    handleFailure(op, failure, now)
                }
            }
        }

    private suspend fun removeLocalClip(op: OutboxEntity) {
        writeTransaction { clipDao, outboxDao ->
            outboxDao.delete(op.opId)
            clipDao.deleteById(op.clipId)
        }
    }

    private suspend fun handleFailure(op: OutboxEntity, failure: ApiFailure, now: Long): OpResult =
        when (failure) {
            // 令牌失效不改变记录状态：保留 Outbox，等待重新配对后继续
            is ApiFailure.Unauthorized -> {
                requiresPairingState.value = true
                OpResult.Unauthorized
            }

            is ApiFailure.RateLimited -> {
                val retryAfterSeconds = failure.retryAfterSeconds
                if (retryAfterSeconds != null) {
                    // 服务端给出的冷却时间优先于本地退避，且不再叠加抖动
                    writeTransaction { _, outboxDao ->
                        outboxDao.markRetry(
                            opId = op.opId,
                            retryCount = op.retryCount,
                            nextRetryAt = now + retryAfterSeconds * 1_000,
                            errorCode = ApiErrorCode.RATE_LIMITED.wireValue,
                        )
                    }
                } else {
                    scheduleRetry(op, now, ApiErrorCode.RATE_LIMITED.wireValue)
                }
                OpResult.RetryScheduled
            }

            is ApiFailure.Offline -> {
                scheduleRetry(op, now, errorCode = null)
                OpResult.RetryScheduled
            }

            is ApiFailure.Retryable -> {
                scheduleRetry(op, now, failure.code?.wireValue)
                OpResult.RetryScheduled
            }

            is ApiFailure.Permanent -> {
                markPermanentFailure(op, failure.code, now)
                OpResult.FailedPermanently
            }
        }

    private suspend fun scheduleRetry(op: OutboxEntity, now: Long, errorCode: String?) {
        val retryCount = op.retryCount + 1
        val nextRetryAt = now + Backoff.delayMillis(retryCount, jitterSample())
        writeTransaction { _, outboxDao ->
            outboxDao.markRetry(op.opId, retryCount, nextRetryAt, errorCode)
        }
    }

    private suspend fun markPermanentFailure(op: OutboxEntity, code: ApiErrorCode, now: Long) {
        val failedState = when (op.opType) {
            OutboxOpType.CREATE -> SyncState.FAILED_CREATE
            OutboxOpType.PATCH_PIN -> SyncState.FAILED_UPDATE
            OutboxOpType.DELETE -> SyncState.FAILED_DELETE
        }
        val wireCode = code.wireValue
        writeTransaction { clipDao, outboxDao ->
            clipDao.getById(op.clipId)?.let { clip ->
                clipDao.upsert(
                    clip.copy(syncState = failedState, lastErrorCode = wireCode, lastErrorAt = now)
                )
            }
            // 该操作与同记录后续操作都不再自动重试；用户 retry 会把 nextRetryAt 重置为 0
            outboxDao.opsForClip(op.clipId).forEach { queued ->
                outboxDao.markRetry(queued.opId, queued.retryCount, NEVER_RETRY_AT, wireCode)
            }
        }
    }

    private fun stateAfterSuccess(remaining: List<OutboxEntity>): SyncState = when {
        remaining.any { it.opType == OutboxOpType.DELETE } -> SyncState.PENDING_DELETE
        remaining.any { it.opType == OutboxOpType.PATCH_PIN } -> SyncState.PENDING_UPDATE
        else -> SyncState.SYNCED
    }

    // 每条记录只看最早一项：后续操作被它阻塞，下一轮可执行时间由它决定
    private suspend fun nextScheduledAt(): Long? =
        database.outboxDao().nextDue(now = NEVER_RETRY_AT)
            .groupBy { it.clipId }
            .values
            .mapNotNull { ops -> ops.minBy { it.opId }.nextRetryAt.takeUnless { it == NEVER_RETRY_AT } }
            .minOrNull()

    private suspend fun runLoop() {
        while (currentCoroutineContext().isActive) {
            val report = replayDue()
            val dueAt = report.nextDueAt
            if (report.waitingForPairing || dueAt == null) {
                // 无到期任务或等待重新配对时不做轮询，只在显式唤醒时重查
                wakeUps.receive()
                continue
            }
            val waitMillis = (dueAt - clock()).coerceAtLeast(1)
            withTimeoutOrNull(waitMillis) { wakeUps.receive() }
        }
    }

    private suspend fun <T> writeTransaction(block: suspend (ClipDao, OutboxDao) -> T): T =
        database.useWriterConnection { transactor ->
            transactor.immediateTransaction { block(database.clipDao(), database.outboxDao()) }
        }

    private enum class OpResult {
        Applied,
        RetryScheduled,
        FailedPermanently,
        Unauthorized,
    }

    private companion object {
        // 永久失败后不再自动重试的哨兵值；用户手动 retry 会重置为 0
        const val NEVER_RETRY_AT = Long.MAX_VALUE
    }
}

data class OutboxRunReport(
    val applied: Int,
    val waitingForPairing: Boolean,
    val nextDueAt: Long?,
)
