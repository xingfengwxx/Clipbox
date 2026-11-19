/**
 * author : 王星星
 * date : 2026-11-19 13:08
 * email : 1099420259@qq.com
 * description : 更新状态扩展测试；固定 descriptorOrNull 与 hasUpdate 的取值，防止设置页读数与 Latest 红点语义回退
 */
package com.wangxingxing.clipbox.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateUiStateTest {

    private val descriptor = UpdateDescriptor(
        version = "1.1.0",
        publishedAt = "2026-11-19T08:00:00Z",
        notes = UpdateNotes(zhCN = "简中", zhTW = "繁中", en = "English"),
        platforms = emptyList(),
    )

    private val platform = UpdatePlatform(
        id = "android",
        available = true,
        file = "Theca-v1.1.0-android-release.apk",
        sizeBytes = 1024,
        sha256 = "0".repeat(64),
        downloadUrl = "http://203.0.113.10:8080/downloads/Theca-v1.1.0-android-release.apk",
    )

    @Test
    fun descriptorOnlyExistsAfterCheckResult() {
        assertNull(UpdateUiState.Idle.descriptorOrNull())
        assertNull(UpdateUiState.Checking.descriptorOrNull())
        assertNull(UpdateUiState.Failed(UpdateError.OFFLINE).descriptorOrNull())

        assertEquals(descriptor, UpdateUiState.Latest(descriptor).descriptorOrNull())
        assertEquals(descriptor, UpdateUiState.Available(descriptor, platform).descriptorOrNull())
        assertEquals(descriptor, UpdateUiState.Unsupported(descriptor).descriptorOrNull())
        assertEquals(
            descriptor,
            UpdateUiState.Downloading(descriptor, platform, 0, 1024).descriptorOrNull(),
        )
        assertEquals(descriptor, UpdateUiState.Verifying(descriptor, platform).descriptorOrNull())
        assertEquals(descriptor, UpdateUiState.Ready(descriptor, platform).descriptorOrNull())
        assertEquals(
            descriptor,
            UpdateUiState.Failed(UpdateError.DOWNLOAD_FAILED, descriptor).descriptorOrNull(),
        )
    }

    @Test
    fun updateStatesExcludeLatestAndFailed() {
        assertTrue(UpdateUiState.Available(descriptor, platform).hasUpdate())
        assertTrue(UpdateUiState.Unsupported(descriptor).hasUpdate())
        assertTrue(UpdateUiState.Downloading(descriptor, platform, 0, 1024).hasUpdate())
        assertTrue(UpdateUiState.Verifying(descriptor, platform).hasUpdate())
        assertTrue(UpdateUiState.Ready(descriptor, platform).hasUpdate())

        assertFalse(UpdateUiState.Idle.hasUpdate())
        assertFalse(UpdateUiState.Checking.hasUpdate())
        assertFalse(UpdateUiState.Latest(descriptor).hasUpdate())
        assertFalse(UpdateUiState.Failed(UpdateError.OFFLINE).hasUpdate())
        assertFalse(UpdateUiState.Failed(UpdateError.DOWNLOAD_FAILED, descriptor).hasUpdate())
    }
}
