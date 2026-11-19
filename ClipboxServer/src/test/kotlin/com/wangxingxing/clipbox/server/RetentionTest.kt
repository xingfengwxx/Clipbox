/**
 * author : 王星星
 * date : 2026-11-17 11:03
 * email : 1099420259@qq.com
 * description : 保留清理测试：非置顶保留 500 条、置顶豁免、30 天墓碑物理删除与 sync_floor 推进、软删除可被 since 拉到、
 *               分享与配对码按过期 24 小时清理。
 *               注意：每个用例使用独立临时目录与真实 SQLite；清理任务直接调用 runOnce，注入可变时钟，
 *                     不经过 HTTP 装配，避免后台调度与固定时间断言互相干扰。
 */
package com.wangxingxing.clipbox.server

import com.wangxingxing.clipbox.server.db.ClipRepository
import com.wangxingxing.clipbox.server.db.ClipRow
import com.wangxingxing.clipbox.server.db.ClipsTable
import com.wangxingxing.clipbox.server.db.DatabaseFactory
import com.wangxingxing.clipbox.server.db.DeviceRepository
import com.wangxingxing.clipbox.server.db.DeviceRow
import com.wangxingxing.clipbox.server.db.MetaRepository
import com.wangxingxing.clipbox.server.db.PairingRepository
import com.wangxingxing.clipbox.server.db.SeqAllocator
import com.wangxingxing.clipbox.server.db.ShareRepository
import com.wangxingxing.clipbox.server.db.ShareRow
import com.wangxingxing.clipbox.server.job.RetentionJob
import com.wangxingxing.clipbox.server.service.Broadcaster
import com.wangxingxing.clipbox.server.service.ClipService
import com.wangxingxing.clipbox.server.service.SyncItem
import com.wangxingxing.clipbox.server.ws.ConnectionRegistry
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir

class RetentionTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var factory: DatabaseFactory
    private lateinit var job: RetentionJob
    private val clock = MutableClock(NOW)

    @BeforeEach
    fun setUp() {
        factory = DatabaseFactory.open(tempDir).also { it.migrate() }
        job = RetentionJob(factory, Broadcaster(ConnectionRegistry()), clock)
    }

    @Test
    fun `501 active unpinned clips keep the newest 500 by createdAt`() = runBlocking {
        seedUnpinned(count = 501)

        val summary = job.runOnce()

        assertEquals(1, summary.softDeleted)
        assertEquals(500L, activeUnpinnedCount())
        assertEquals(501L, totalClipCount())
        assertEquals(502L, serverSeq())
        assertEquals(0L, syncFloor())

        val oldest = assertNotNull(clip("CLIP-1"))
        assertEquals(NOW, oldest.deletedAt, "软删除时间取本轮清理的时钟")
        assertEquals(502L, oldest.serverSeq, "软删除必须分配新序号，而不是保留旧序号")
        assertNull(assertNotNull(clip("CLIP-2")).deletedAt)
    }

    @Test
    fun `soft delete runs in batches and never reuses a server seq`() = runBlocking {
        seedUnpinned(count = 560)

        val summary = job.runOnce()

        assertEquals(60, summary.softDeleted)
        assertEquals(500L, activeUnpinnedCount())
        assertEquals(560L, totalClipCount())
        assertEquals(620L, serverSeq())

        val tombstoneSeqs = factory.readTransaction {
            ClipsTable.select(ClipsTable.serverSeq)
                .where { ClipsTable.deletedAt.isNotNull() }
                .map { row -> row[ClipsTable.serverSeq] }
        }
        assertEquals(60, tombstoneSeqs.size)
        assertEquals(60, tombstoneSeqs.toSet().size, "同一轮清理不得产生重复序号")
        assertEquals(561L, tombstoneSeqs.min())
        assertEquals(620L, tombstoneSeqs.max())
    }

    @Test
    fun `pinned clips are never trimmed even when they are the oldest`() = runBlocking {
        seedPinned(count = 20, createdAtBase = 0)
        seedUnpinned(count = 600, createdAtBase = 1_000, idPrefix = "U")

        val summary = job.runOnce()

        assertEquals(100, summary.softDeleted)
        assertEquals(500L, activeUnpinnedCount())
        assertEquals(0L, activePinnedDeletedCount())
        assertEquals(620L, totalClipCount())
        assertEquals(720L, serverSeq())
        assertNull(assertNotNull(clip("P-1")).deletedAt)
        assertEquals(NOW, assertNotNull(clip("U-1")).deletedAt)
    }

    @Test
    fun `tombstones are purged only after strictly more than 30 days`() = runBlocking {
        factory.writeTransaction {
            ClipRepository.insert(ClipRow("ACTIVE", "content", null, false, 1_000, null, 1))
            ClipRepository.insert(ClipRow("T-OLD", "content", null, false, 1_000, NOW - TOMBSTONE - 1, 2))
            ClipRepository.insert(ClipRow("T-EDGE", "content", null, false, 1_000, NOW - TOMBSTONE, 3))
            ClipRepository.insert(ClipRow("T-NEW", "content", null, false, 1_000, NOW - TOMBSTONE + 1, 4))
            seedMeta(serverSeq = 4)
        }

        val first = job.runOnce()

        assertEquals(1, first.purgedTombstones)
        assertEquals(2L, first.syncFloor)
        assertNull(clip("T-OLD"))
        assertNotNull(clip("T-EDGE"), "恰好 30 天的墓碑必须保留")
        assertNotNull(clip("T-NEW"))
        assertEquals(4L, serverSeq(), "物理清理不分配新序号")

        val second = job.runOnce()
        assertEquals(0, second.purgedTombstones)
        assertEquals(2L, second.syncFloor)
        assertEquals(3L, totalClipCount())
    }

    @Test
    fun `sync floor only moves forward with physical purge`() = runBlocking {
        factory.writeTransaction {
            seedMeta(serverSeq = 8, syncFloor = 5)
            ClipRepository.insert(ClipRow("BELOW-FLOOR", "content", null, false, 1_000, NOW - TOMBSTONE - 1, 3))
        }

        assertEquals(1, job.runOnce().purgedTombstones)
        assertEquals(5L, syncFloor(), "较小序号的墓碑不得降低同步下界")

        factory.writeTransaction {
            ClipRepository.insert(ClipRow("ABOVE-FLOOR", "content", null, false, 1_000, NOW - TOMBSTONE - 1, 9))
            seedMeta(serverSeq = 9)
        }

        assertEquals(1, job.runOnce().purgedTombstones)
        assertEquals(9L, syncFloor())
    }

    @Test
    fun `soft deleted clips stay visible to since until the tombstone is purged`() = runBlocking {
        seedUnpinned(count = 501)
        assertEquals(1, job.runOnce().softDeleted)

        val service = ClipService(factory, Broadcaster(ConnectionRegistry())) { clock.millis() }
        val beforePurge = service.sync(since = 501, limit = 200)
        assertFalse(beforePurge.resyncRequired)
        assertEquals(502L, beforePurge.nextSince)
        val tombstone = beforePurge.items.single()
        assertTrue(tombstone is SyncItem.Tombstone, "软删除必须通过 since 以墓碑形式下发：$tombstone")
        assertEquals("CLIP-1", tombstone.id)
        assertEquals(502L, tombstone.serverSeq)
        assertEquals(1L, beforePurge.minAvailableSeq, "软删除本身不得推进 sync_floor")

        clock.advance(Duration.ofDays(31))
        assertEquals(1, job.runOnce().purgedTombstones)

        val afterPurge = service.sync(since = 501, limit = 200)
        assertTrue(afterPurge.resyncRequired, "游标早于 sync_floor 时必须要求全量重同步")
        assertTrue(afterPurge.items.isEmpty())
        assertEquals(503L, afterPurge.minAvailableSeq)

        val recovered = service.sync(since = 502, limit = 200)
        assertFalse(recovered.resyncRequired)
        assertTrue(recovered.items.isEmpty())
        assertEquals(502L, recovered.nextSince)
    }

    @Test
    fun `shares are cleaned 24 hours after expiry or one-time opening`() = runBlocking {
        factory.writeTransaction {
            ShareRepository.insert(share("expired-old", expiresAt = NOW - GRACE - 1))
            ShareRepository.insert(share("expired-recent", expiresAt = NOW - GRACE + 1))
            ShareRepository.insert(share("active", expiresAt = NOW + 60_000))
            ShareRepository.insert(
                share("opened-old", expiresAt = NOW + 60_000, oneTime = true, openedAt = NOW - GRACE - 1),
            )
            ShareRepository.insert(share("opened-recent", expiresAt = NOW + 60_000, oneTime = true, openedAt = NOW - 1_000))
            ShareRepository.insert(share("unopened-one-time", expiresAt = NOW + 60_000, oneTime = true))
        }

        val summary = job.runOnce()

        assertEquals(2, summary.deletedShares)
        assertNull(shareOrNull("expired-old"))
        assertNull(shareOrNull("opened-old"))
        assertNotNull(shareOrNull("expired-recent"))
        assertNotNull(shareOrNull("active"))
        assertNotNull(shareOrNull("opened-recent"))
        assertNotNull(shareOrNull("unopened-one-time"))
        assertEquals(0L, totalClipCount())
    }

    @Test
    fun `pairing codes are cleaned 24 hours after expiry whether used or not`() = runBlocking {
        factory.writeTransaction {
            repeat(4) { index ->
                DeviceRepository.insert(
                    DeviceRow(
                        id = "DEVICE-$index",
                        name = "Device $index",
                        tokenHash = "device-hash-$index",
                        createdAt = 1_000,
                        lastSeenAt = null,
                    ),
                )
            }
            PairingRepository.upsertCode(
                issuerId = "DEVICE-0",
                codeHash = "hash-100000",
                createdAt = NOW - 2 * DAY,
                expiresAt = NOW - GRACE - 1,
            )
            PairingRepository.upsertCode(
                issuerId = "DEVICE-1",
                codeHash = "hash-200000",
                createdAt = NOW - DAY,
                expiresAt = NOW - 60_000,
            )
            PairingRepository.upsertCode(
                issuerId = "DEVICE-2",
                codeHash = "hash-300000",
                createdAt = NOW,
                expiresAt = NOW + 600_000,
            )
            PairingRepository.markUsedIfUnused("hash-300000", NOW - 1_000)
            PairingRepository.upsertCode(
                issuerId = "DEVICE-3",
                codeHash = "hash-400000",
                createdAt = NOW - 2 * DAY,
                expiresAt = NOW - GRACE - 1,
            )
            PairingRepository.markUsedIfUnused("hash-400000", NOW - 2 * DAY)
        }

        val summary = job.runOnce()

        assertEquals(2, summary.deletedPairingCodes)
        assertNull(factory.readTransaction { PairingRepository.findByCodeHash("hash-100000") })
        assertNull(factory.readTransaction { PairingRepository.findByCodeHash("hash-400000") })
        assertNotNull(factory.readTransaction { PairingRepository.findByCodeHash("hash-200000") })
        assertNotNull(factory.readTransaction { PairingRepository.findByCodeHash("hash-300000") })
    }

    @Test
    fun `empty database sweep is a no-op`() = runBlocking {
        val summary = job.runOnce()

        assertEquals(0, summary.softDeleted)
        assertEquals(0, summary.purgedTombstones)
        assertEquals(0, summary.deletedShares)
        assertEquals(0, summary.deletedPairingCodes)
        assertEquals(0L, summary.syncFloor)
        assertEquals(0L, serverSeq())
    }

    // --- 夹具 ---

    private suspend fun seedUnpinned(
        count: Int,
        createdAtBase: Long = 1_000,
        idPrefix: String = "CLIP",
    ) {
        factory.writeTransaction {
            repeat(count) { index ->
                val n = index + 1
                ClipRepository.insert(
                    ClipRow(
                        id = "$idPrefix-$n",
                        content = "content-$n",
                        sourceDeviceId = null,
                        pinned = false,
                        createdAt = createdAtBase + n,
                        deletedAt = null,
                        serverSeq = SeqAllocator.nextSeq(),
                    ),
                )
            }
        }
    }

    private suspend fun seedPinned(count: Int, createdAtBase: Long) {
        factory.writeTransaction {
            repeat(count) { index ->
                val n = index + 1
                ClipRepository.insert(
                    ClipRow(
                        id = "P-$n",
                        content = "pinned-$n",
                        sourceDeviceId = null,
                        pinned = true,
                        createdAt = createdAtBase + n,
                        deletedAt = null,
                        serverSeq = SeqAllocator.nextSeq(),
                    ),
                )
            }
        }
    }

    private fun seedMeta(serverSeq: Long? = null, syncFloor: Long? = null) {
        serverSeq?.let { MetaRepository.put(MetaRepository.SERVER_SEQ_KEY, it.toString()) }
        syncFloor?.let { MetaRepository.put(MetaRepository.SYNC_FLOOR_KEY, it.toString()) }
    }

    private fun share(tokenHash: String, expiresAt: Long, oneTime: Boolean = false, openedAt: Long? = null) =
        ShareRow(
            tokenHash = tokenHash,
            clipId = "CLIP-1",
            content = "shared",
            createdAt = NOW - DAY,
            expiresAt = expiresAt,
            oneTime = oneTime,
            openedAt = openedAt,
        )

    private suspend fun shareOrNull(tokenHash: String) =
        factory.readTransaction { ShareRepository.findByTokenHash(tokenHash) }

    private suspend fun clip(id: String) = factory.readTransaction { ClipRepository.findById(id) }

    private suspend fun totalClipCount(): Long =
        factory.readTransaction { ClipsTable.selectAll().count() }

    private suspend fun activeUnpinnedCount(): Long = factory.readTransaction {
        ClipsTable.selectAll()
            .where { ClipsTable.deletedAt.isNull() and (ClipsTable.pinned eq 0) }
            .count()
    }

    private suspend fun activePinnedDeletedCount(): Long = factory.readTransaction {
        ClipsTable.selectAll()
            .where { ClipsTable.deletedAt.isNotNull() and (ClipsTable.pinned eq 1) }
            .count()
    }

    private suspend fun serverSeq(): Long =
        factory.readTransaction { MetaRepository.getLong(MetaRepository.SERVER_SEQ_KEY) ?: -1L }

    private suspend fun syncFloor(): Long =
        factory.readTransaction { MetaRepository.getLong(MetaRepository.SYNC_FLOOR_KEY) ?: -1L }

    /** 只用于时间推进，让 runOnce 的 30 天/24 小时判断可被精确控制。 */
    private class MutableClock(private var currentMillis: Long) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = Instant.ofEpochMilli(currentMillis)

        fun advance(duration: Duration) {
            currentMillis += duration.toMillis()
        }
    }

    companion object {
        private const val NOW = 1_800_000_000_000L
        private const val DAY = 86_400_000L
        private const val GRACE = DAY
        private const val TOMBSTONE = 30 * DAY
    }
}
