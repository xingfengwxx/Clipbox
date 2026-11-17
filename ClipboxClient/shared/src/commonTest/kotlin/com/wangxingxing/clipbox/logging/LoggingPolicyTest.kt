/**
 * author : 王星星
 * date : 2026-11-17 16:35
 * email : 1099420259@qq.com
 * description : 日志开关策略测试；验证 release 默认零输出、debug 默认开启、开关即时生效、设置变更跟随，以及诊断模式仍对令牌脱敏
 */
package com.wangxingxing.clipbox.logging

import com.wangxingxing.clipbox.settings.FakePreferencesDataStore
import com.wangxingxing.clipbox.settings.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LoggingPolicyTest {

    @Test
    fun releaseStartsSilent() {
        val sink = RecordingSink()
        val logger = AppLogger(sink, isDebugBuild = false)

        assertFalse(logger.enabled.value)
        assertTrue(logger.requiresConfirmation)
        logger.log(LogEvent.Business(operation = "sync"))
        logger.log(LogEvent.Network(method = "POST", pathTemplate = "/clips", operation = "createClip", status = 201, durationMillis = 5))

        assertTrue(sink.lines.isEmpty())
    }

    @Test
    fun debugStartsEnabledWithoutConfirmation() {
        val sink = RecordingSink()
        val logger = AppLogger(sink, isDebugBuild = true)

        assertTrue(logger.enabled.value)
        assertFalse(logger.requiresConfirmation)

        logger.log(LogEvent.Business(operation = "sync"))

        assertEquals(1, sink.lines.size)
    }

    @Test
    fun toggleTakesEffectImmediately() {
        val sink = RecordingSink()
        val logger = AppLogger(sink, isDebugBuild = false)

        logger.setEnabled(true)
        logger.log(LogEvent.Business(operation = "sync"))
        logger.setEnabled(false)
        logger.log(LogEvent.Business(operation = "sync"))

        assertEquals(1, sink.lines.size)
    }

    @Test
    fun observeFollowsPersistedSetting() = runTest {
        val settings = SettingsRepository(FakePreferencesDataStore(), diagnosticLoggingDefault = false)
        val sink = RecordingSink()
        val logger = AppLogger(sink, isDebugBuild = false)

        logger.observe(settings, backgroundScope)
        runCurrent()
        assertFalse(logger.enabled.value)

        settings.setDiagnosticLoggingEnabled(true)
        runCurrent()
        assertTrue(logger.enabled.value)

        settings.setDiagnosticLoggingEnabled(false)
        runCurrent()
        assertFalse(logger.enabled.value)
    }

    @Test
    fun networkWhitelistFieldsAreRendered() {
        val sink = RecordingSink()
        val logger = AppLogger(sink, isDebugBuild = true)

        logger.log(
            LogEvent.Network(
                method = "PATCH",
                pathTemplate = "/clips/{id}",
                operation = "updateClip",
                status = 200,
                durationMillis = 17,
                errorCode = null,
                clipId = "clip-1",
            )
        )

        val line = sink.messages.single()
        assertTrue(line.contains("PATCH"))
        assertTrue(line.contains("/clips/{id}"))
        assertTrue(line.contains("status=200"))
        assertTrue(line.contains("durationMs=17"))
        assertTrue(line.contains("operation=updateClip"))
        assertTrue(line.contains("clipId=clip-1"))
    }

    @Test
    fun diagnosticNetworkBodiesAreLoggedWithSecretsRedacted() {
        val sink = RecordingSink()
        val logger = AppLogger(sink, isDebugBuild = true)

        logger.log(
            LogEvent.Network(
                method = "POST",
                pathTemplate = "/shares",
                operation = "createShare",
                status = 201,
                durationMillis = 9,
                requestBody = """{"clipId":"clip-1","token":"$TOKEN"}""",
                responseBody = """{"url":"http://203.0.113.10:8080/s/$SHARE_TOKEN"}""",
            )
        )

        val line = sink.messages.single()
        assertTrue(line.contains("request="))
        assertTrue(line.contains("response="))
        assertFalse(line.contains(TOKEN))
        assertFalse(line.contains(SHARE_TOKEN))
        assertTrue(line.contains(LogRedactor.MASK))
    }

    private companion object {
        const val TOKEN = "Zm9vYmFyYmF6cXV4MTIzNDU2Nzg5MGFiY2RlZg"
        const val SHARE_TOKEN = "c2hhcmUtc2VjcmV0LXRva2VuLTAxMjM0NTY3ODkw"
    }
}
