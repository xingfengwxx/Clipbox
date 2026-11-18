/**
 * author : 王星星
 * date : 2026-11-16 13:44
 * email : 1099420259@qq.com
 * description : /healthz 数据库探测测试：数据库可达返回 200，连接池关闭后返回 503 且 status=error。
 */
package com.wangxingxing.clipbox.server.db

import com.wangxingxing.clipbox.server.config.AppConfig
import com.wangxingxing.clipbox.server.module
import com.wangxingxing.clipbox.server.routes.HealthResponse
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.io.TempDir

class DatabaseHealthTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `healthz returns ok when database is reachable`() = testApplication {
        val factory = DatabaseFactory.open(tempDir).also { it.migrate() }
        application { module(config(), factory) }

        val response = client.get("/healthz")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<HealthResponse>(response.bodyAsText())
        assertEquals("ok", body.status)
        assertEquals("1.0.0", body.version)
    }

    @Test
    fun `healthz returns 503 after database closes`() = testApplication {
        val factory = DatabaseFactory.open(tempDir).also { it.migrate() }
        application { module(config(), factory) }
        assertEquals(HttpStatusCode.OK, client.get("/healthz").status)

        factory.close()

        val response = client.get("/healthz")

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        val body = Json.decodeFromString<HealthResponse>(response.bodyAsText())
        assertEquals("error", body.status)
    }

    private fun config() = AppConfig(
        port = 8080,
        dataDir = tempDir,
        publicBaseUrl = "http://203.0.113.10:8080",
    )
}
