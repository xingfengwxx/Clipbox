/**
 * author : 王星星
 * date : 2026-11-17 16:35
 * email : 1099420259@qq.com
 * description : 日志脱敏测试；断言 Bearer、WS 认证令牌键值、JSON 令牌字段、配对码与分享路径令牌全部被遮蔽，普通正文不被误伤
 */
package com.wangxingxing.clipbox.logging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LoggingRedactorTest {

    @Test
    fun bearerHeaderTokenIsMasked() {
        val redacted = LogRedactor.redact("authorization=Bearer sk-live-abcdef123456")

        assertFalse(redacted.contains("sk-live-abcdef123456"))
        assertTrue(redacted.contains(LogRedactor.MASK))
    }

    @Test
    fun socketAuthTokenAssignmentIsMasked() {
        val redacted = LogRedactor.redact(
            "WS auth-sent detail=waiting auth.ok operation=socket token=${TOKEN}",
        )

        assertFalse(redacted.contains(TOKEN))
        assertEquals("WS auth-sent detail=waiting auth.ok operation=socket token=<redacted>", redacted)
    }

    @Test
    fun jsonSecretFieldsAreMasked() {
        val body = """{"token":"$TOKEN","deviceToken":"$TOKEN","shareToken":"$TOKEN"}"""

        val redacted = LogRedactor.redact(body)

        assertFalse(redacted.contains(TOKEN))
        assertEquals(3, Regex(Regex.escape(LogRedactor.MASK)).findAll(redacted).count())
    }

    @Test
    fun pairingCodeIsMaskedButStableErrorCodesSurvive() {
        val pairRequest = """{"code":"123456","deviceName":"Pixel"}"""
        val errorBody = """{"type":"error","error":{"code":"UNAUTHORIZED","message":"invalid token"}}"""

        assertFalse(LogRedactor.redact(pairRequest).contains("123456"))
        // 错误码是稳定标识，不能因为字段名同样叫 code 就被遮蔽
        assertTrue(LogRedactor.redact(errorBody).contains("UNAUTHORIZED"))
    }

    @Test
    fun sharePathTokenIsMasked() {
        val url = "http://203.0.113.10:8080/s/$SHARE_TOKEN"

        val redacted = LogRedactor.redact(url)

        assertFalse(redacted.contains(SHARE_TOKEN))
        assertTrue(redacted.contains("/s/<redacted>"))
    }

    @Test
    fun plainContentIsNotDamaged() {
        val content = """{"type":"clip.created","data":{"content":"token limit is 65536 bytes"}}"""

        assertEquals(content, LogRedactor.redact(content))
    }

    private companion object {
        const val TOKEN = "Zm9vYmFyYmF6cXV4MTIzNDU2Nzg5MGFiY2RlZg"
        const val SHARE_TOKEN = "c2hhcmUtc2VjcmV0LXRva2VuLTAxMjM0NTY3ODkw"
    }
}
