/**
 * author : 王星星
 * date : 2026-11-19 11:04
 * email : 1099420259@qq.com
 * description : 更新描述接口集成测试：覆盖正常投影、HEAD、schema 1、清单缺失、坏 JSON、平台缺失/重复、说明回退与超限、429 与无认证。
 *               注意：夹具按测试临时目录写入 downloads.json，断言严格对齐 api.md §13，不接受字符串数字或宽松回退。
 */
package com.wangxingxing.clipbox.server.integration

import com.wangxingxing.clipbox.server.config.AppConfig
import com.wangxingxing.clipbox.server.db.DatabaseFactory
import com.wangxingxing.clipbox.server.module
import com.wangxingxing.clipbox.server.plugins.RateLimitSettings
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.io.TempDir

class UpdateApiIntegrationTest {

    @TempDir
    lateinit var tempDir: Path

    private val downloadsDir: Path get() = tempDir.resolve("downloads")

    @Test
    fun `latest projects schema 2 manifest`() = updateApp {
        writeManifest(manifest())

        val response = client.get(LATEST_PATH)

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        assertTrue(response.headers[HttpHeaders.ContentType]!!.startsWith("application/json"))
        val body = json(response)
        assertEquals("1.1.0", body.str("version"))
        assertEquals("2026-11-19T08:00:00Z", body.str("publishedAt"))
        val notes = body.getValue("notes").jsonObject
        assertEquals(setOf("zh-CN", "zh-TW", "en"), notes.keys)
        val platforms = body.getValue("platforms").jsonArray.map { it.jsonObject }
        assertEquals(
            listOf("android", "ios", "macos", "windows", "linux"),
            platforms.map { it.str("id") },
        )

        val android = platforms.first()
        assertEquals(
            setOf("id", "available", "file", "sizeBytes", "sha256", "downloadUrl"),
            android.keys,
        )
        assertEquals(ANDROID_FILE, android.str("file"))
        assertEquals(123L, android.long("sizeBytes"))
        assertEquals(SHA, android.str("sha256"))
        assertEquals("$PUBLIC_BASE_URL/downloads/$ANDROID_FILE", android.str("downloadUrl"))

        val ios = platforms[1]
        assertEquals(setOf("id", "available"), ios.keys)
        assertFalse(ios.bool("available"))
    }

    @Test
    fun `head mirrors get without body`() = updateApp {
        writeManifest(manifest())

        val response = client.head(LATEST_PATH)

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        assertEquals("", response.bodyAsText())
    }

    @Test
    fun `schema 1 manifest is unavailable`() = updateApp {
        writeManifest(manifest(schemaVersion = 1))
        assertServiceUnavailable()
    }

    @Test
    fun `missing manifest is unavailable`() = updateApp {
        assertServiceUnavailable()
    }

    @Test
    fun `invalid json is unavailable`() = updateApp {
        writeManifest("{ not-json")
        assertServiceUnavailable()
    }

    @Test
    fun `invalid version and generatedAt are unavailable`() = updateApp {
        writeManifest(manifest(version = "1.1"))
        assertServiceUnavailable()
        writeManifest(manifest(generatedAt = "2026-11-19 08:00:00"))
        assertServiceUnavailable()
    }

    @Test
    fun `invalid platform fields are unavailable`() = updateApp {
        writeManifest(manifest(platforms = withAndroid(sha = "A".repeat(64))))
        assertServiceUnavailable()
        writeManifest(manifest(platforms = withAndroid(size = 0)))
        assertServiceUnavailable()
        writeManifest(manifest(platforms = withAndroid(file = "updates/$ANDROID_FILE")))
        assertServiceUnavailable()
        writeManifest(manifest(platforms = withAndroid(sizeLiteral = "\"123\"")))
        assertServiceUnavailable()
    }

    @Test
    fun `missing or duplicate platforms are unavailable`() = updateApp {
        writeManifest(manifest(platforms = defaultPlatforms().filterNot { it.contains("\"ios\"") }))
        assertServiceUnavailable()
        writeManifest(
            manifest(
                platforms = defaultPlatforms().toMutableList().also {
                    it[2] = available("android", ANDROID_FILE)
                },
            ),
        )
        assertServiceUnavailable()
    }

    @Test
    fun `notes fall back to zh-CN`() = updateApp {
        writeManifest(manifest(notes = """{"zh-CN":"只有简中。"}"""))

        val notes = json(client.get(LATEST_PATH)).getValue("notes").jsonObject

        assertEquals("只有简中。", notes.str("zh-TW"))
        assertEquals("只有简中。", notes.str("en"))
    }

    @Test
    fun `zh-CN notes are required`() = updateApp {
        writeManifest(manifest(notes = """{"zh-TW":"只有繁中。","en":"Only English."}"""))
        assertServiceUnavailable()
        writeManifest(manifest(notes = """{"zh-CN":"  ","zh-TW":"只有繁中。"}"""))
        assertServiceUnavailable()
    }

    @Test
    fun `oversized notes are unavailable`() = updateApp {
        writeManifest(manifest(notes = """{"zh-CN":"${"x".repeat(MAX_NOTE_BYTES + 1)}"}"""))
        assertServiceUnavailable()
    }

    @Test
    fun `unknown fields are ignored`() = updateApp {
        writeManifest(manifest().replaceFirst("{", """{ "extra": "ignored","""))

        assertEquals(HttpStatusCode.OK, client.get(LATEST_PATH).status)
    }

    @Test
    fun `authorization header is ignored`() = updateApp {
        writeManifest(manifest())

        val anonymous = client.get(LATEST_PATH)
        val withHeader = client.get(LATEST_PATH) {
            header(HttpHeaders.Authorization, "Bearer not-a-device-token")
        }

        assertEquals(HttpStatusCode.OK, anonymous.status)
        assertEquals(HttpStatusCode.OK, withHeader.status)
    }

    @Test
    fun `get and head share the rate limit`() = updateApp(RateLimitSettings(appLatestPerMinute = 2)) {
        writeManifest(manifest())

        assertEquals(HttpStatusCode.OK, client.get(LATEST_PATH).status)
        assertEquals(HttpStatusCode.OK, client.head(LATEST_PATH).status)
        val denied = client.get(LATEST_PATH)
        assertEquals(HttpStatusCode.TooManyRequests, denied.status)
        assertEquals("RATE_LIMITED", json(denied).errorCode())
        val retryAfter = denied.headers[HttpHeaders.RetryAfter]
        assertNotNull(retryAfter)
        assertTrue(retryAfter.toLong() >= 1)
    }

    private fun updateApp(
        rateLimitSettings: RateLimitSettings = LOOSE_LIMITS,
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        val factory = DatabaseFactory.open(tempDir)
        factory.migrate()
        application {
            module(
                config = AppConfig(
                    port = 8080,
                    dataDir = tempDir,
                    publicBaseUrl = PUBLIC_BASE_URL,
                    webDir = tempDir.resolve("web"),
                    downloadDir = downloadsDir,
                ),
                databaseFactory = factory,
                rateLimitSettings = rateLimitSettings,
            )
        }
        // 首个请求触发模块初始化，避免用例在模块装配前发起调用。
        client.get("/healthz")
        block()
    }

    private fun writeManifest(json: String) {
        Files.createDirectories(downloadsDir)
        Files.writeString(downloadsDir.resolve("downloads.json"), json)
    }

    private suspend fun ApplicationTestBuilder.assertServiceUnavailable() {
        val response = client.get(LATEST_PATH)
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals("SERVICE_UNAVAILABLE", json(response).errorCode())
    }

    private fun manifest(
        schemaVersion: Int = 2,
        version: String = "1.1.0",
        generatedAt: String = "2026-11-19T08:00:00Z",
        notes: String = DEFAULT_NOTES,
        platforms: List<String> = defaultPlatforms(),
    ): String = """
        {
          "schemaVersion": $schemaVersion,
          "version": "$version",
          "generatedAt": "$generatedAt",
          "notes": $notes,
          "platforms": [${platforms.joinToString(",\n            ")}]
        }
    """.trimIndent()

    private fun defaultPlatforms(): List<String> = listOf(
        available("android", ANDROID_FILE),
        """{ "id": "ios", "available": false }""",
        available("macos", MACOS_FILE),
        available("windows", WINDOWS_FILE),
        available("linux", LINUX_FILE),
    )

    private fun withAndroid(
        file: String = ANDROID_FILE,
        size: Long = 123,
        sizeLiteral: String = size.toString(),
        sha: String = SHA,
    ): List<String> = defaultPlatforms().toMutableList().also {
        it[0] = """{ "id": "android", "available": true, "file": "$file", "sizeBytes": $sizeLiteral, "sha256": "$sha" }"""
    }

    private fun available(
        id: String,
        file: String,
        size: Long = 123,
        sha: String = SHA,
    ): String =
        """{ "id": "$id", "available": true, "file": "$file", "sizeBytes": $size, "sha256": "$sha" }"""

    private companion object {
        const val LATEST_PATH = "/api/v1/app/latest"
        const val ANDROID_FILE = "Theca-v1.1.0-android-release.apk"
        const val MACOS_FILE = "Theca-v1.1.0-macos-arm64-release.dmg"
        const val WINDOWS_FILE = "Theca-v1.1.0-windows-amd64-release.msi"
        const val LINUX_FILE = "Theca-v1.1.0-linux-amd64-release.deb"
        const val SHA = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        const val DEFAULT_NOTES =
            """{"zh-CN":"新增检查更新。","zh-TW":"新增檢查更新。","en":"Added update checks."}"""
        const val MAX_NOTE_BYTES = 64 * 1024
    }
}
