/**
 * author : 王星星
 * date : 2026-11-17 15:30
 * email : 1099420259@qq.com
 * description : 剪贴板回环与稳定窗口纯逻辑测试；覆盖 2 秒 applied 窗口边界、sent 哈希不参与跳过、800ms debounce 与哈希跨平台稳定性
 */
package com.wangxingxing.clipbox.clipboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClipboardGuardTest {

    @Test
    fun hashIsStableAcrossPlatformsAndUsesUtf8Bytes() {
        assertEquals(-3750763034362895579L, ClipboardHash.of(""))
        assertEquals(-5808556873153909620L, ClipboardHash.of("a"))
        assertEquals(-6615550055289275125L, ClipboardHash.of("hello"))
        assertEquals(8376348503340329717L, ClipboardHash.of("剪贴板"))
    }

    @Test
    fun remoteAppliedHashSkipsUploadInsideWindow() {
        val guard = ClipboardGuard()
        guard.onRemoteApplied("hello", now = 1_000L)

        assertTrue(guard.shouldSkipUpload("hello", now = 1_000L))
        assertTrue(guard.shouldSkipUpload("hello", now = 3_000L))
        assertFalse(guard.shouldSkipUpload("hello", now = 3_001L))
        assertFalse(guard.shouldSkipUpload("other", now = 1_001L))
    }

    @Test
    fun sentHashAloneNeverSkipsUpload() {
        val guard = ClipboardGuard()
        guard.onSent("hello")

        assertFalse(guard.shouldSkipUpload("hello", now = 1_000L))
        assertFalse(guard.shouldSkipUpload("hello", now = 1_200L))
    }

    @Test
    fun manualCopyAfterWindowIsUploadedAgain() {
        val guard = ClipboardGuard()
        guard.onRemoteApplied("copy me", now = 10_000L)
        assertTrue(guard.shouldSkipUpload("copy me", now = 11_999L))
        // 2 秒后用户重新复制同样内容，必须允许发送
        assertFalse(guard.shouldSkipUpload("copy me", now = 12_001L))
    }

    @Test
    fun resetClearsAppliedAndSentState() {
        val guard = ClipboardGuard()
        guard.onRemoteApplied("hello", now = 0L)
        guard.onSent("hello")
        guard.reset()

        assertFalse(guard.shouldSkipUpload("hello", now = 1L))
    }

    @Test
    fun debouncerEmitsLatestTextOnlyAfterStableWindow() {
        val debouncer = ClipboardDebouncer()
        debouncer.offer("a", now = 0L)
        assertNull(debouncer.poll(now = 399L))

        debouncer.offer("ab", now = 400L)
        // 新内容重置截止时间：原截止 800 不再有效
        assertNull(debouncer.poll(now = 800L))
        assertNull(debouncer.poll(now = 1_199L))
        assertEquals("ab", debouncer.poll(now = 1_200L))
        assertNull(debouncer.poll(now = 1_201L))
    }

    @Test
    fun debouncerResetDropsPendingText() {
        val debouncer = ClipboardDebouncer()
        debouncer.offer("hello", now = 0L)
        debouncer.reset()

        assertNull(debouncer.poll(now = 10_000L))
    }
}
