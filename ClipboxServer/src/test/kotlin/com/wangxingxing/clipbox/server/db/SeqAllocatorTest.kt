/**
 * author : 王星星
 * date : 2026-11-16 13:44
 * email : 1099420259@qq.com
 * description : 序号分配并发测试：1000 个并发写事务分配 server_seq，断言无重复且最终值与分配数一致。
 */
package com.wangxingxing.clipbox.server.db

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.io.TempDir

class SeqAllocatorTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `concurrent allocation never duplicates a sequence`() = runBlocking {
        DatabaseFactory.open(tempDir).use { factory ->
            factory.migrate()

            val allocations = coroutineScope {
                (1..ALLOCATION_COUNT).map {
                    async(Dispatchers.Default) {
                        factory.writeTransaction { SeqAllocator.nextSeq() }
                    }
                }.awaitAll()
            }

            assertEquals(ALLOCATION_COUNT, allocations.toSet().size)
            assertEquals((1L..ALLOCATION_COUNT.toLong()).toSet(), allocations.toSet())
            assertEquals(
                ALLOCATION_COUNT.toLong(),
                factory.readTransaction { MetaRepository.getLong(MetaRepository.SERVER_SEQ_KEY) },
            )
        }
    }

    private companion object {
        const val ALLOCATION_COUNT = 1000
    }
}
