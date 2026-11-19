/**
 * author : 王星星
 * date : 2026-11-17 11:03
 * email : 1099420259@qq.com
 * description : 服务端保留清理：非置顶活跃记录按 createdAt 只保留最近 500 条、30 天墓碑物理删除并推进 sync_floor、
 *               过期分享与配对码按 24 小时宽限清理；启动时运行一次，之后每 24 小时运行一次。
 *               注意：软删除必须逐个分配新 server_seq 且只在事务提交后广播；sync_floor 只能由墓碑物理删除推进，
 *                     软删除本身不得推进；单个批次提交即解锁，禁止把整轮清理放进一个长事务。
 */
package com.wangxingxing.clipbox.server.job

import com.wangxingxing.clipbox.server.db.ClipRepository
import com.wangxingxing.clipbox.server.db.ClipsTable
import com.wangxingxing.clipbox.server.db.DatabaseFactory
import com.wangxingxing.clipbox.server.db.DevicePairingCodesTable
import com.wangxingxing.clipbox.server.db.MetaRepository
import com.wangxingxing.clipbox.server.db.SeqAllocator
import com.wangxingxing.clipbox.server.db.SharesTable
import com.wangxingxing.clipbox.server.service.Broadcaster
import java.time.Clock
import java.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.slf4j.LoggerFactory

/** 一轮清理的结果，只含计数与下界，便于日志核对且不携带正文。 */
data class RetentionSummary(
    val softDeleted: Int,
    val purgedTombstones: Int,
    val deletedShares: Int,
    val deletedPairingCodes: Int,
    val syncFloor: Long,
)

class RetentionJob(
    private val databaseFactory: DatabaseFactory,
    private val broadcaster: Broadcaster,
    private val clock: Clock = Clock.systemUTC(),
) {

    suspend fun runOnce(): RetentionSummary {
        val now = clock.millis()
        val softDeleted = softDeleteBeyondLimit(now)
        val purgedTombstones = purgeTombstones(now)
        val deletedShares = deleteExpiredShares(now)
        val deletedPairingCodes = deleteExpiredPairingCodes(now)
        val syncFloor = databaseFactory.readTransaction {
            MetaRepository.getLong(MetaRepository.SYNC_FLOOR_KEY) ?: 0L
        }
        return RetentionSummary(softDeleted, purgedTombstones, deletedShares, deletedPairingCodes, syncFloor)
    }

    /**
     * 调度入口：立即运行一次，之后按间隔循环。单个批次失败只记录日志并等待下一轮，
     * 不得让后台任务异常冒泡到 Ktor 协程导致服务停止。
     */
    fun start(scope: CoroutineScope, interval: Duration = DEFAULT_INTERVAL) {
        scope.launch {
            while (isActive) {
                try {
                    val summary = runOnce()
                    logger.info(
                        "保留清理完成：softDeleted={} tombstonePurged={} sharesDeleted={} pairingCodesDeleted={} syncFloor={}",
                        summary.softDeleted,
                        summary.purgedTombstones,
                        summary.deletedShares,
                        summary.deletedPairingCodes,
                        summary.syncFloor,
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.warn("保留清理失败，等待下次运行：{}", e.message ?: e.javaClass.simpleName)
                }
                delay(interval.toMillis())
            }
        }
    }

    /** 每批最多 50 条；批提交后才广播，避免客户端收到尚未落库的删除事件。 */
    private suspend fun softDeleteBeyondLimit(now: Long): Int {
        var total = 0
        while (true) {
            val batch: List<Pair<String, Long>> = databaseFactory.writeTransaction {
                val active = ClipsTable.selectAll()
                    .where { ClipsTable.deletedAt.isNull() and (ClipsTable.pinned eq 0) }
                    .count()
                val overflow = active - ACTIVE_LIMIT
                if (overflow <= 0) {
                    emptyList()
                } else {
                    val take = minOf(overflow, ACTIVE_BATCH.toLong()).toInt()
                    ClipsTable.select(ClipsTable.id, ClipsTable.serverSeq)
                        .where { ClipsTable.deletedAt.isNull() and (ClipsTable.pinned eq 0) }
                        .orderBy(ClipsTable.createdAt, SortOrder.ASC)
                        .orderBy(ClipsTable.serverSeq, SortOrder.ASC)
                        .limit(take)
                        .map { row ->
                            val id = row[ClipsTable.id]
                            val serverSeq = SeqAllocator.nextSeq()
                            ClipRepository.markDeleted(id, now, serverSeq)
                            id to serverSeq
                        }
                }
            }
            if (batch.isEmpty()) break
            batch.forEach { (id, serverSeq) -> broadcaster.clipDeleted(id, serverSeq) }
            total += batch.size
        }
        return total
    }

    /**
     * 单批最多 100 条墓碑；删除与 sync_floor 更新必须同事务，保证客户端看到的 minAvailableSeq
     * 不会早于已消失的墓碑。
     */
    private suspend fun purgeTombstones(now: Long): Int {
        val cutoff = now - TOMBSTONE_TTL.toMillis()
        var total = 0
        while (true) {
            val purged = databaseFactory.writeTransaction {
                val rows = ClipsTable.select(ClipsTable.id, ClipsTable.serverSeq)
                    .where { ClipsTable.deletedAt.isNotNull() and (ClipsTable.deletedAt less cutoff) }
                    .orderBy(ClipsTable.serverSeq, SortOrder.ASC)
                    .limit(TOMBSTONE_BATCH)
                    .map { row -> row[ClipsTable.id] to row[ClipsTable.serverSeq] }
                if (rows.isEmpty()) return@writeTransaction 0

                val ids = rows.map { it.first }
                val maxSeq = rows.maxOf { it.second }
                ClipsTable.deleteWhere { ClipsTable.id inList ids }

                val currentFloor = MetaRepository.getLong(MetaRepository.SYNC_FLOOR_KEY) ?: 0L
                if (maxSeq > currentFloor) {
                    MetaRepository.put(MetaRepository.SYNC_FLOOR_KEY, maxSeq.toString())
                }
                rows.size
            }
            if (purged == 0) break
            total += purged
        }
        return total
    }

    /** 分享在过期或一次性链接打开后保留 24 小时，便于排查；之后按批物理删除。 */
    private suspend fun deleteExpiredShares(now: Long): Int {
        val cutoff = now - EXPIRED_GRACE.toMillis()
        var total = 0
        while (true) {
            val deleted = databaseFactory.writeTransaction {
                val tokenHashes = SharesTable.select(SharesTable.tokenHash)
                    .where {
                        (SharesTable.expiresAt less cutoff) or (
                            (SharesTable.oneTime eq 1) and SharesTable.openedAt.isNotNull() and
                                (SharesTable.openedAt less cutoff)
                            )
                    }
                    .limit(EXPIRY_BATCH)
                    .map { row -> row[SharesTable.tokenHash] }
                if (tokenHashes.isEmpty()) return@writeTransaction 0

                SharesTable.deleteWhere { SharesTable.tokenHash inList tokenHashes }
            }
            if (deleted == 0) break
            total += deleted
        }
        return total
    }

    /** 设备配对码无论是否使用，过期 24 小时后删除；使用记录保留到该时刻以便排查。 */
    private suspend fun deleteExpiredPairingCodes(now: Long): Int {
        val cutoff = now - EXPIRED_GRACE.toMillis()
        var total = 0
        while (true) {
            val deleted = databaseFactory.writeTransaction {
                val issuerIds = DevicePairingCodesTable.select(DevicePairingCodesTable.issuerId)
                    .where { DevicePairingCodesTable.expiresAt less cutoff }
                    .limit(EXPIRY_BATCH)
                    .map { row -> row[DevicePairingCodesTable.issuerId] }
                if (issuerIds.isEmpty()) return@writeTransaction 0

                DevicePairingCodesTable.deleteWhere { DevicePairingCodesTable.issuerId inList issuerIds }
            }
            if (deleted == 0) break
            total += deleted
        }
        return total
    }

    companion object {
        private const val ACTIVE_LIMIT = 500L
        private const val ACTIVE_BATCH = 50
        private const val TOMBSTONE_BATCH = 100
        private const val EXPIRY_BATCH = 100

        private val TOMBSTONE_TTL: Duration = Duration.ofDays(30)
        private val EXPIRED_GRACE: Duration = Duration.ofHours(24)
        private val DEFAULT_INTERVAL: Duration = Duration.ofDays(24)

        private val logger = LoggerFactory.getLogger(RetentionJob::class.java)
    }
}
