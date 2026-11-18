/**
 * author : 王星星
 * date : 2026-11-16 10:33
 * email : 1099420259@qq.com
 * description : 启动配置校验的单元测试；数据目录使用临时目录，避免污染仓库。
 */
package com.wangxingxing.clipbox.server.config

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class AppConfigTest {

    @TempDir
    lateinit var tempDir: Path

    private fun validEnv(): MutableMap<String, String> = mutableMapOf(
        "PUBLIC_BASE_URL" to "http://203.0.113.10:8080",
        "DATA_DIR" to tempDir.resolve("data").toString(),
    )

    @Test
    fun `missing public base url fails`() {
        val env = validEnv().apply { remove("PUBLIC_BASE_URL") }

        val error = assertFailsWith<ConfigException> { AppConfig.fromEnv(env) }

        assertTrue(error.message.orEmpty().contains("PUBLIC_BASE_URL"))
    }

    @Test
    fun `public base url without scheme fails`() {
        val env = validEnv().apply { put("PUBLIC_BASE_URL", "203.0.113.10:8080") }

        val error = assertFailsWith<ConfigException> { AppConfig.fromEnv(env) }

        assertTrue(error.message.orEmpty().contains("http://"))
    }

    @Test
    fun `public base url with trailing slash fails`() {
        val env = validEnv().apply { put("PUBLIC_BASE_URL", "http://203.0.113.10:8080/") }

        assertFailsWith<ConfigException> { AppConfig.fromEnv(env) }
    }

    @Test
    fun `valid http address passes and data dir is created`() {
        val env = validEnv()

        val config = AppConfig.fromEnv(env)

        assertEquals(8080, config.port)
        assertEquals("http://203.0.113.10:8080", config.publicBaseUrl)
        assertTrue(Files.isDirectory(config.dataDir))
    }

    @Test
    fun `invalid port fails`() {
        val env = validEnv().apply { put("PORT", "70000") }

        assertFailsWith<ConfigException> { AppConfig.fromEnv(env) }
    }

    @Test
    fun `web and download dirs use relative defaults`() {
        val config = AppConfig.fromEnv(validEnv())

        assertEquals(Paths.get("./web"), config.webDir)
        assertEquals(Paths.get("./downloads"), config.downloadDir)
    }

    @Test
    fun `missing static dirs are accepted and not created`() {
        val web = tempDir.resolve("site")
        val downloads = tempDir.resolve("downloads")
        val env = validEnv().apply {
            put("WEB_DIR", web.toString())
            put("DOWNLOAD_DIR", downloads.toString())
        }

        val config = AppConfig.fromEnv(env)

        assertEquals(web, config.webDir)
        assertEquals(downloads, config.downloadDir)
        assertFalse(Files.exists(web))
        assertFalse(Files.exists(downloads))
    }
}
