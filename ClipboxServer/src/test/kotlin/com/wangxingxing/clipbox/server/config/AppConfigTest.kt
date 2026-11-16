package com.wangxingxing.clipbox.server.config

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class AppConfigTest {

    @TempDir
    lateinit var tempDir: Path

    private fun validEnv(): MutableMap<String, String> = mutableMapOf(
        "ADMIN_TOKEN" to "a".repeat(32),
        "PUBLIC_BASE_URL" to "http://203.0.113.10:8080",
        "DATA_DIR" to tempDir.resolve("data").toString(),
    )

    @Test
    fun `missing admin token fails`() {
        val env = validEnv().apply { remove("ADMIN_TOKEN") }

        val error = assertFailsWith<ConfigException> { AppConfig.fromEnv(env) }

        assertTrue(error.message.orEmpty().contains("ADMIN_TOKEN"))
    }

    @Test
    fun `short admin token fails`() {
        val env = validEnv().apply { put("ADMIN_TOKEN", "a".repeat(31)) }

        val error = assertFailsWith<ConfigException> { AppConfig.fromEnv(env) }

        assertTrue(error.message.orEmpty().contains("32"))
    }

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
        assertEquals("a".repeat(32), config.adminToken)
        assertTrue(Files.isDirectory(config.dataDir))
    }

    @Test
    fun `invalid port fails`() {
        val env = validEnv().apply { put("PORT", "70000") }

        assertFailsWith<ConfigException> { AppConfig.fromEnv(env) }
    }
}
