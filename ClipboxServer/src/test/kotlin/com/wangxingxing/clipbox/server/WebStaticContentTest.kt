/**
 * author : 王星星
 * date : 2026-11-18 14:57
 * email : 1099420259@qq.com
 * description : 官网静态托管与下载 Range 的集成测试：首页与资源缓存头、下载字节与 206、HEAD 复用 GET 语义、
 *               未知 API 与未知站点路径的统一 JSON 404、分享页 CSP/no-store 不被站点策略覆盖，
 *               以及静态目录缺失时仍可启动。
 *               注意：静态目录与下载包都建在临时目录内，不依赖仓库里的 dist/ 或 downloads/。
 */
package com.wangxingxing.clipbox.server

import com.wangxingxing.clipbox.server.config.AppConfig
import com.wangxingxing.clipbox.server.db.DatabaseFactory
import com.wangxingxing.clipbox.server.module
import com.wangxingxing.clipbox.server.plugins.SITE_PAGE_CSP
import com.wangxingxing.clipbox.server.service.SHARE_PAGE_CSP
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.io.TempDir

class WebStaticContentTest {

    @TempDir
    lateinit var tempDir: Path

    private val webDir: Path get() = tempDir.resolve("web")
    private val downloadDir: Path get() = tempDir.resolve("downloads")
    private val downloadBytes = ByteArray(2048) { (it % 251).toByte() }

    private fun config(
        web: Path = webDir,
        downloads: Path = downloadDir,
    ) = AppConfig(
        port = 8080,
        dataDir = tempDir,
        adminToken = "a".repeat(32),
        publicBaseUrl = "http://127.0.0.1:8080",
        webDir = web,
        downloadDir = downloads,
    )

    @Test
    fun `site home is served with no-cache and strict csp`() = withSite { client ->
        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("site-home"))
        assertEquals("text/html", response.headers[HttpHeaders.ContentType]?.substringBefore(';'))
        assertEquals("no-cache", response.headers[HttpHeaders.CacheControl])
        assertEquals(SITE_PAGE_CSP, response.headers["Content-Security-Policy"])
        assertEquals("nosniff", response.headers["X-Content-Type-Options"])
    }

    @Test
    fun `hashed assets are immutable`() = withSite { client ->
        val response = client.get("/assets/index-app.css")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(
            "public, max-age=31536000, immutable",
            response.headers[HttpHeaders.CacheControl],
        )
    }

    @Test
    fun `download serves exact bytes with one day cache`() = withSite { client ->
        val response = client.get("/downloads/Theca-test.bin")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(downloadBytes.contentEquals(response.bodyAsBytes()))
        assertEquals("public, max-age=86400", response.headers[HttpHeaders.CacheControl])
        assertEquals("bytes", response.headers[HttpHeaders.AcceptRanges])
    }

    @Test
    fun `range request returns 206 with content range`() = withSite { client ->
        val response = client.get("/downloads/Theca-test.bin") {
            header(HttpHeaders.Range, "bytes=0-15")
        }
        assertEquals(HttpStatusCode.PartialContent, response.status)
        assertEquals("bytes 0-15/${downloadBytes.size}", response.headers[HttpHeaders.ContentRange])
        assertEquals("bytes", response.headers[HttpHeaders.AcceptRanges])
        val body = response.bodyAsBytes()
        assertEquals(16, body.size)
        assertTrue(downloadBytes.copyOfRange(0, 16).contentEquals(body))
    }

    @Test
    fun `unknown api path keeps the unified json 404`() = withSite { client ->
        val response = client.get("/api/v1/does-not-exist")
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        assertEquals("NOT_FOUND", errorCode(response.bodyAsText()))
    }

    @Test
    fun `share page keeps its own csp and no-store`() = withSite { client ->
        val response = client.get("/s/not-a-real-token")
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(SHARE_PAGE_CSP, response.headers["Content-Security-Policy"])
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
    }

    @Test
    fun `head requests reuse get semantics`() = withSite { client ->
        val home = client.head("/")
        assertEquals(HttpStatusCode.OK, home.status)
        assertEquals("text/html", home.headers[HttpHeaders.ContentType]?.substringBefore(';'))
        assertEquals("no-cache", home.headers[HttpHeaders.CacheControl])
        assertEquals(SITE_PAGE_CSP, home.headers["Content-Security-Policy"])

        val asset = client.head("/assets/index-app.css")
        assertEquals(HttpStatusCode.OK, asset.status)
        assertEquals("public, max-age=31536000, immutable", asset.headers[HttpHeaders.CacheControl])

        val download = client.head("/downloads/Theca-test.bin")
        assertEquals(HttpStatusCode.OK, download.status)
        assertEquals(downloadBytes.size.toString(), download.headers[HttpHeaders.ContentLength])

        val unauthorized = client.head("/api/v1/clips")
        assertEquals(HttpStatusCode.Unauthorized, unauthorized.status)
        assertEquals("no-store", unauthorized.headers[HttpHeaders.CacheControl])
    }

    @Test
    fun `unknown site path returns the unified json 404`() = withSite { client ->
        val response = client.get("/no-such-page")
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("no-cache", response.headers[HttpHeaders.CacheControl])
        assertEquals(SITE_PAGE_CSP, response.headers["Content-Security-Policy"])
        assertEquals("NOT_FOUND", errorCode(response.bodyAsText()))
    }

    @Test
    fun `missing static dirs do not block startup`() = testApplication {
        val factory = DatabaseFactory.open(tempDir)
        factory.migrate()
        application {
            module(
                config(
                    web = tempDir.resolve("missing-web"),
                    downloads = tempDir.resolve("missing-downloads"),
                ),
                factory,
            )
        }

        assertEquals(HttpStatusCode.OK, client.get("/healthz").status)
        val unknown = client.get("/api/v1/does-not-exist")
        assertEquals(HttpStatusCode.NotFound, unknown.status)
        assertEquals("NOT_FOUND", errorCode(unknown.bodyAsText()))
    }

    private fun withSite(block: suspend (HttpClient) -> Unit) = testApplication {
        Files.createDirectories(webDir.resolve("assets"))
        Files.writeString(
            webDir.resolve("index.html"),
            "<!doctype html><html><body>site-home</body></html>",
        )
        Files.writeString(webDir.resolve("assets/index-app.css"), "body{margin:0}")
        Files.createDirectories(downloadDir)
        Files.write(downloadDir.resolve("Theca-test.bin"), downloadBytes)
        val factory = DatabaseFactory.open(tempDir)
        factory.migrate()
        application { module(config(), factory) }
        // 首个请求触发模块启动，确保启动自助配对码落库后再执行用例。
        client.get("/healthz")
        block(client)
    }

    private fun errorCode(body: String): String =
        Json.parseToJsonElement(body)
            .jsonObject.getValue("error").jsonObject
            .getValue("code").jsonPrimitive.content
}
