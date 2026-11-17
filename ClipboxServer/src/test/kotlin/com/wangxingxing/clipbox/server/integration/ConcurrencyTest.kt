/**
 * author : 王星星
 * date : 2026-11-17 13:58
 * email : 1099420259@qq.com
 * description : 并发测试：并行创建的序号唯一性、保留清理与写入并发、WebSocket 注册注销收敛、一次性分享并发消费。
 *               注意：断言只依赖公开接口与库内状态，不假设并发完成顺序；允许参与协程互相穿插，但不得出现重复序号或两页正文。
 */
package com.wangxingxing.clipbox.server.integration

import com.wangxingxing.clipbox.server.db.ClipRepository
import com.wangxingxing.clipbox.server.db.ClipRow
import com.wangxingxing.clipbox.server.db.ClipsTable
import com.wangxingxing.clipbox.server.db.DatabaseFactory
import com.wangxingxing.clipbox.server.db.SeqAllocator
import com.wangxingxing.clipbox.server.job.RetentionJob
import com.wangxingxing.clipbox.server.service.Broadcaster
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.websocket.close
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.junit.jupiter.api.io.TempDir

class ConcurrencyTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `two hundred parallel creates keep server seq unique`() = integrationApp(tempDir) {
        val device = pairDevice()
        val ids = (1..200).map(::clipIdFor)

        val results = coroutineScope {
            ids.mapIndexed { index, id ->
                async {
                    val response = createClip(device.token, id, content = "content-$index")
                    response.status to json(response).long("serverSeq")
                }
            }.awaitAll()
        }

        results.forEach { (status, _) -> assertEquals(HttpStatusCode.Created, status) }
        val seqs = results.map { (_, seq) -> seq }
        assertEquals(200, seqs.toSet().size, "并行创建不得产生重复序号")
        assertEquals((1L..200L).toSet(), seqs.toSet())

        val page = clips(device.token, since = 0, limit = 500)
        assertEquals(200, page.items().size)
        assertEquals(200L, page.long("serverMaxSeq"))
        assertEquals(200L, page.long("nextSince"))
        assertEquals(ids.toSet(), page.items().map { it.str("id") }.toSet())
    }

    @Test
    fun `retention cleanup runs safely alongside writes`() = integrationApp(tempDir) {
        val device = pairDevice()
        factory.writeTransaction {
            repeat(510) { index ->
                ClipRepository.insert(
                    ClipRow(
                        id = clipIdFor(1_000 + index),
                        content = "seed-$index",
                        sourceDeviceId = null,
                        pinned = false,
                        createdAt = 1_000L + index,
                        deletedAt = null,
                        serverSeq = SeqAllocator.nextSeq(),
                    ),
                )
            }
        }
        val job = RetentionJob(factory, Broadcaster(registry))
        val createdIds = (1..40).map(::clipIdFor)

        val (writeStatuses, firstSummary) = coroutineScope {
            val cleanup = async { job.runOnce() }
            val writes = createdIds.mapIndexed { index, id ->
                async { createClip(device.token, id, content = "race-$index").status }
            }
            writes.awaitAll() to cleanup.await()
        }
        writeStatuses.forEach { assertEquals(HttpStatusCode.Created, it) }
        assertTrue(firstSummary.softDeleted >= 10, "清理必须处理超过 500 条的活跃记录")

        // 写入停止后再跑一轮，活跃非置顶记录必须精确收敛到 500 条。
        job.runOnce()
        assertEquals(500L, activeUnpinnedCount(factory))

        val rows = factory.readTransaction {
            ClipsTable.selectAll().map { row -> row[ClipsTable.id] to row[ClipsTable.serverSeq] }
        }
        assertEquals(550, rows.size, "软删除只更新行，不新增物理行")
        val seqs = rows.map { (_, seq) -> seq }
        assertEquals(seqs.size, seqs.toSet().size, "清理与写入并发时序号不得重复")
        assertTrue(rows.map { (id, _) -> id }.containsAll(createdIds), "并发写入的记录不得丢失")
        val maxSeq = rows.maxOf { (_, seq) -> seq }
        val page = clips(device.token, since = 0, limit = 500)
        assertEquals(maxSeq, page.long("serverMaxSeq"))
    }

    @Test
    fun `websocket registry converges after concurrent connect and close`() = integrationApp(tempDir) {
        val devices = (1..6).map { index -> pairDevice("Device $index") }
        val sessions = coroutineScope {
            devices.map { device -> async { openSocket(device.token) } }.awaitAll()
        }
        withTimeout(5_000) {
            while (registry.activeCount() < sessions.size) delay(10)
        }
        assertEquals(sessions.size, registry.activeCount())

        coroutineScope {
            sessions.map { session -> async { session.close() } }.awaitAll()
        }
        withTimeout(5_000) {
            while (registry.activeCount() > 0) delay(10)
        }
        assertEquals(0, registry.activeCount(), "全部关闭后注册表必须归零")
    }

    @Test
    fun `one time share is consumed by exactly one parallel reader`() = integrationApp(tempDir) {
        val device = pairDevice()
        val id = clipIdFor(1)
        assertEquals(HttpStatusCode.Created, createClip(device.token, id, content = "一次性正文").status)
        val shareToken = json(createShare(device.token, id, ttlSeconds = 600, oneTime = true)).str("token")

        val results = coroutineScope {
            (1..10).map {
                async {
                    val response = openShare(shareToken)
                    response.status to response.bodyAsText()
                }
            }.awaitAll()
        }

        val served = results.filter { (status, _) -> status == HttpStatusCode.OK }
        val gone = results.filter { (status, _) -> status == HttpStatusCode.Gone }
        assertEquals(1, served.size, "并发访问只能有一次拿到正文")
        assertEquals(9, gone.size)
        assertTrue(served.single().second.contains("一次性正文"))
        assertTrue(gone.none { (_, body) -> body.contains("一次性正文") }, "410 页面不得泄露正文")
    }

    private suspend fun activeUnpinnedCount(factory: DatabaseFactory): Long = factory.readTransaction {
        ClipsTable.selectAll()
            .where { ClipsTable.deletedAt.isNull() and (ClipsTable.pinned eq 0) }
            .count()
    }
}
