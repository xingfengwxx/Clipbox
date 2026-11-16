/**
 * author : 王星星
 * date : 2026-11-16 13:44
 * email : 1099420259@qq.com
 * description : 数据目录启动约束测试：目录不存在时自动创建，目录不可写时启动校验失败并说明原因。
 */
package com.wangxingxing.clipbox.server.db

import com.wangxingxing.clipbox.server.config.AppConfig
import com.wangxingxing.clipbox.server.config.ConfigException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class DatabaseStartupTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `missing data directory is created`() {
        val dataDir = tempDir.resolve("nested/data")

        val config = AppConfig.fromEnv(validEnv(dataDir))

        assertTrue(Files.isDirectory(config.dataDir))
    }

    @Test
    fun `unwritable data directory fails with reason`() {
        val dataDir = tempDir.resolve("readonly").also { Files.createDirectories(it) }
        Files.setPosixFilePermissions(dataDir, PosixFilePermissions.fromString("r-xr-xr-x"))
        try {
            val error = assertFailsWith<ConfigException> {
                AppConfig.fromEnv(validEnv(dataDir))
            }
            assertTrue(error.message.orEmpty().contains("不可写"))
        } finally {
            Files.setPosixFilePermissions(dataDir, PosixFilePermissions.fromString("rwx------"))
        }
    }

    private fun validEnv(dataDir: Path): Map<String, String> = mapOf(
        "ADMIN_TOKEN" to "a".repeat(32),
        "PUBLIC_BASE_URL" to "http://203.0.113.10:8080",
        "DATA_DIR" to dataDir.toString(),
    )
}
