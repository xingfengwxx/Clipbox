/**
 * author : 王星星
 * date : 2026-11-16 16:05
 * email : 1099420259@qq.com
 * description : 文件数据库持久化测试；验证关闭并重新打开同一路径后数据仍在，防止误用内存库
 */
package com.wangxingxing.clipbox.db

import androidx.room.Room
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ClipDaoPersistenceTest {

    @Test
    fun dataSurvivesCloseAndReopen() = runTest {
        val directory = Files.createTempDirectory("clipbox-db")
        val path = directory.resolve(DATABASE_NAME).toAbsolutePath().toString()
        try {
            val first = openDatabase(path)
            try {
                first.clipDao().upsert(testClip(id = "persisted", createdAt = 42, content = "kept"))
            } finally {
                first.close()
            }

            val second = openDatabase(path)
            try {
                val restored = second.clipDao().getById("persisted")
                assertNotNull(restored)
                assertEquals("kept", restored.content)
                assertEquals(42, restored.createdAt)
            } finally {
                second.close()
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    private fun openDatabase(path: String): ClipboxDatabase =
        buildClipboxDatabase(Room.databaseBuilder<ClipboxDatabase>(name = path))
}
