/**
 * author : 王星星
 * date : 2026-11-17 16:38
 * email : 1099420259@qq.com
 * description : ApiService 日志接入测试；用 MockEngine 驱动真实请求链路，断言白名单字段、请求/响应正文、配对码与分享令牌的脱敏，以及开关关闭时零输出
 */
package com.wangxingxing.clipbox.network

import com.wangxingxing.clipbox.logging.AppLogger
import com.wangxingxing.clipbox.logging.RecordingSink
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApiLoggingTest {

    private val clipId = "01J8Z6Z3K4M5N6P7Q8R9S0T1V2"
    private val deviceId = "01J8Y6Z3K4M5N6P7Q8R9S0T1V2"

    @Test
    fun successRequestLogsWhitelistAndBodies() = runTest {
        val sink = RecordingSink()
        val service = stubService(logger = AppLogger(sink, isDebugBuild = true)) {
            respondJson(
                """
                    {"id":"$clipId","serverSeq":41,"createdAt":1730000000000,"pinned":false,"deleted":false}
                """.trimIndent(),
                status = HttpStatusCode.Created,
            )
        }

        service.createClip(id = clipId, content = "hello", createdAt = 1730000000000)

        val line = sink.messages.single()
        assertTrue(line.contains("NET POST /clips"))
        assertTrue(line.contains("status=201"))
        assertTrue(line.contains("operation=createClip"))
        assertTrue(line.contains("clipId=$clipId"))
        assertTrue(line.contains("authorization=<redacted>"))
        assertFalse(line.contains(TEST_TOKEN))
        assertTrue(line.contains("\"content\":\"hello\""))
        assertTrue(line.contains("serverSeq"))
    }

    @Test
    fun pairLogsMaskPairingCodeAndReturnedToken() = runTest {
        val sink = RecordingSink()
        val service = stubService(logger = AppLogger(sink, isDebugBuild = true)) {
            respondJson(
                """{"deviceId":"$deviceId","token":"$RAW_TOKEN","deviceName":"Pixel 8"}""",
                status = HttpStatusCode.Created,
            )
        }

        service.pair(code = "123456", deviceName = "Pixel 8")

        val line = sink.messages.single()
        assertFalse(line.contains("123456"))
        assertFalse(line.contains(RAW_TOKEN))
        assertTrue(line.contains("\"code\":\"<redacted>\""))
        assertTrue(line.contains("\"token\":\"<redacted>\""))
    }

    @Test
    fun shareLogsMaskTokenAndUrlPath() = runTest {
        val sink = RecordingSink()
        val service = stubService(logger = AppLogger(sink, isDebugBuild = true)) {
            respondJson(
                """{"token":"$RAW_TOKEN","url":"http://203.0.113.10:8080/s/$SHARE_TOKEN","expiresAt":1730086400000}""",
                status = HttpStatusCode.Created,
            )
        }

        service.createShare(clipId = clipId, ttlSeconds = 3_600, oneTime = false)

        val line = sink.messages.single()
        assertFalse(line.contains(RAW_TOKEN))
        assertFalse(line.contains(SHARE_TOKEN))
        assertTrue(line.contains("/s/<redacted>"))
    }

    @Test
    fun disabledLoggerWritesNothing() = runTest {
        val sink = RecordingSink()
        val service = stubService(logger = AppLogger(sink, isDebugBuild = false)) {
            respondJson(
                """{"items":[],"nextSince":0,"hasMore":false,"resyncRequired":false,"minAvailableSeq":0,"serverMaxSeq":0}""",
            )
        }

        service.getClips(since = 0)

        assertTrue(sink.lines.isEmpty())
    }

    private companion object {
        const val RAW_TOKEN = "raw-device-or-share-token-0123456789"
        const val SHARE_TOKEN = "share-path-token-0123456789abcdef"
    }
}
