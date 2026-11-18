/**
 * author : 王星星
 * date : 2026-11-16 10:33
 * email : 1099420259@qq.com
 * description : /healthz 契约测试：断言状态码、Content-Type 与响应字段。
 */
package com.wangxingxing.clipbox.server

import com.wangxingxing.clipbox.server.config.AppConfig
import com.wangxingxing.clipbox.server.routes.HealthResponse
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.io.TempDir

class HealthRoutesTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `healthz returns ok payload`() = testApplication {
        application {
            module(
                AppConfig(
                    port = 8080,
                    dataDir = tempDir,
                    publicBaseUrl = "http://203.0.113.10:8080",
                ),
            )
        }

        val response = client.get("/healthz")

        assertEquals(HttpStatusCode.OK, response.status)
        val contentType = response.headers[HttpHeaders.ContentType]
        assertNotNull(contentType)
        assertTrue(contentType.startsWith("application/json"))
        val body = Json.decodeFromString<HealthResponse>(response.bodyAsText())
        assertEquals("ok", body.status)
        assertEquals("1.0.0", body.version)
        assertTrue(body.time > 0)
    }
}
