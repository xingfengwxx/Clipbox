/**
 * author : 王星星
 * date : 2026-11-17 11:05
 * email : 1099420259@qq.com
 * description : 日志脱敏测试：请求日志只含白名单字段，正文/设备令牌/配对码/分享令牌不落日志，
 *               /s/{token} 按模板记录，启动日志包含版本、监听地址、数据目录与迁移版本且不含管理令牌。
 *               注意：通过 logback ListAppender 捕获指定 logger，不修改 logback.xml。
 */
package com.wangxingxing.clipbox.server

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.wangxingxing.clipbox.server.config.AppConfig
import com.wangxingxing.clipbox.server.db.DatabaseFactory
import com.wangxingxing.clipbox.server.db.DeviceRepository
import com.wangxingxing.clipbox.server.db.DeviceRow
import com.wangxingxing.clipbox.server.util.Hashing
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.slf4j.LoggerFactory

class LoggingTest {

    @TempDir
    lateinit var tempDir: Path

    private val adminToken = "a".repeat(32)
    private val deviceId = "01J8Y6Z3K4M5N6P7Q8R9S0T1V2"
    private val deviceToken = "d".repeat(43)

    @Test
    fun `request log contains only the whitelisted fields`() = capture(REQUEST_LOGGER) { appender ->
        withApp { client ->
            appender.list.clear()
            assertEquals(HttpStatusCode.OK, client.get("/healthz").status)
        }

        val message = singleRequestLog(appender)
        assertTrue(
            WHITELIST_PATTERN.matches(message),
            "请求日志字段超出白名单：$message",
        )
        assertTrue(message.contains("method=GET"))
        assertTrue(message.contains("pathTemplate=/healthz"))
        assertTrue(message.contains("status=200"))
    }

    @Test
    fun `share token is logged as a path template`() = capture(REQUEST_LOGGER) { appender ->
        val token = "secret-share-token-abc"
        withApp { client ->
            appender.list.clear()
            assertEquals(HttpStatusCode.NotFound, client.get("/s/$token").status)
        }

        val message = singleRequestLog(appender)
        assertTrue(message.contains("pathTemplate=/s/{token}"), message)
        assertFalse(message.contains(token), "分享令牌不得进入日志：$message")
    }

    @Test
    fun `request log never contains bodies or device tokens`() = capture(REQUEST_LOGGER) { appender ->
        val marker = "TOP-SECRET-CONTENT"
        withApp(seedDevice = true) { client ->
            appender.list.clear()
            val response = client.post("/api/v1/clips") {
                header(HttpHeaders.Authorization, "Bearer $deviceToken")
                contentType(ContentType.Application.Json)
                setBody("""{"id":"$CLIP_ID","content":"$marker","createdAt":1}""")
            }
            assertEquals(HttpStatusCode.Created, response.status)
        }

        val message = singleRequestLog(appender)
        assertTrue(message.contains("pathTemplate=/api/v1/clips"), message)
        assertTrue(message.contains("deviceId=$deviceId"), "已认证请求应记录设备 ID：$message")
        assertFalse(message.contains(marker), "正文不得进入日志：$message")
        assertFalse(message.contains(deviceToken), "设备令牌不得进入日志：$message")
    }

    @Test
    fun `pairing request log never contains the submitted code`() = capture(REQUEST_LOGGER) { appender ->
        val code = "12x45"
        withApp { client ->
            appender.list.clear()
            val response = client.post("/api/v1/pair") {
                contentType(ContentType.Application.Json)
                setBody("""{"code":"$code","deviceName":"Pixel 8"}""")
            }
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

        val message = singleRequestLog(appender)
        assertTrue(message.contains("pathTemplate=/api/v1/pair"), message)
        assertTrue(message.contains("errorCode=VALIDATION_ERROR"), message)
        assertFalse(message.contains(code), "配对码不得进入日志：$message")
    }

    @Test
    fun `startup log reports version listen address data dir and schema version`() {
        val appender = capture(APPLICATION_LOGGER) { capture ->
            testApplication {
                val factory = DatabaseFactory.open(tempDir)
                factory.migrate()
                application { module(testConfig(), factory) }
                client.get("/healthz")
            }
            capture
        }

        val messages = appender.list.map { it.formattedMessage }
        val startup = messages.single { it.contains("启动：listen=") }
        assertTrue(startup.contains("1.0.0"), startup)
        assertTrue(startup.contains("listen=http://0.0.0.0:8080"), startup)
        assertTrue(startup.contains("dataDir="), startup)
        assertTrue(startup.contains("schemaVersion=1"), startup)
        assertEquals(1, messages.count { it.contains("[PAIRING-CODE]") }, "自动配对码只应打印一次")
        messages.forEach { assertFalse(it.contains(adminToken), "管理令牌不得进入日志：$it") }
    }

    // --- 夹具 ---

    private fun <T> capture(loggerName: String, block: (ListAppender<ILoggingEvent>) -> T): T {
        val logger = LoggerFactory.getLogger(loggerName) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        try {
            return block(appender)
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }
    }

    private fun withApp(
        seedDevice: Boolean = false,
        block: suspend (HttpClient) -> Unit,
    ) = testApplication {
        val factory = DatabaseFactory.open(tempDir)
        factory.migrate()
        if (seedDevice) {
            factory.writeTransaction {
                DeviceRepository.insert(DeviceRow(deviceId, "Pixel 8", Hashing.sha256Hex(deviceToken), 1_000, 1_000))
            }
        }
        application { module(testConfig(), factory) }
        // 首个请求触发模块启动，之后的请求日志才在清空 appender 后产生。
        client.get("/healthz")
        block(client)
    }

    private fun singleRequestLog(appender: ListAppender<ILoggingEvent>): String {
        val messages = appender.list.map { it.formattedMessage }
        assertEquals(1, messages.size, "用例应只产生一条请求日志：$messages")
        return messages.single()
    }

    private fun testConfig() = AppConfig(
        port = 8080,
        dataDir = tempDir,
        adminToken = adminToken,
        publicBaseUrl = "http://127.0.0.1:8080",
    )

    companion object {
        private const val REQUEST_LOGGER = "com.wangxingxing.clipbox.server.http.RequestLog"
        private const val APPLICATION_LOGGER = "com.wangxingxing.clipbox.server.Application"
        private const val CLIP_ID = "01J8Z6Z3K4M5N6P7Q8R9S0T1V2"
        private val WHITELIST_PATTERN = Regex(
            "^requestId=\\S+ method=\\S+ pathTemplate=\\S+ status=\\d+ latencyMs=\\d+ deviceId=\\S+ errorCode=\\S+$",
        )
    }
}
