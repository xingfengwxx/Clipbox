/**
 * author : 王星星
 * date : 2026-11-19 11:08
 * email : 1099420259@qq.com
 * description : 版本比较与新版本判定测试；非法版本必须返回不可比较而不是抛异常，避免坏版本号打断更新检查
 */
package com.wangxingxing.clipbox.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VersionTest {

    private fun descriptor(version: String) = UpdateDescriptor(
        version = version,
        publishedAt = "2026-11-19T08:00:00Z",
        notes = UpdateNotes(zhCN = "简", zhTW = "繁", en = "en"),
        platforms = emptyList(),
    )

    @Test
    fun equalVersionsCompareZero() {
        assertEquals(0, compareVersions("1.0.0", "1.0.0"))
        assertEquals(0, compareVersions("1.0.01", "1.0.1"))
    }

    @Test
    fun segmentsCompareNumerically() {
        assertTrue(compareVersions("1.1.0", "1.0.0")!! > 0)
        assertTrue(compareVersions("1.0.10", "1.0.9")!! > 0)
        assertTrue(compareVersions("0.9.0", "0.10.0")!! < 0)
        assertTrue(compareVersions("10.0.0", "9.99.99")!! > 0)
    }

    @Test
    fun invalidVersionsAreIncomparable() {
        val invalid = listOf("1.0", "v1.0.0", "1.0.0-beta", "1..0", "1.0.0.0", "", "99999999999999999999.0.0")
        for (version in invalid) {
            assertNull(compareVersions(version, "1.0.0"), "应不可比较：$version")
            assertNull(compareVersions("1.0.0", version), "应不可比较：$version")
        }
    }

    @Test
    fun checkForUpdateReportsAvailableOnlyForNewerVersion() {
        val result = checkForUpdate("1.0.0", descriptor("1.1.0"))
        assertEquals("1.1.0", assertIs<UpdateCheckResult.Available>(result).descriptor.version)

        assertIs<UpdateCheckResult.Latest>(checkForUpdate("1.1.0", descriptor("1.1.0")))
        assertIs<UpdateCheckResult.Latest>(checkForUpdate("1.2.0", descriptor("1.1.0")))
    }

    @Test
    fun checkForUpdateIsIncomparableWhenEitherVersionIsMalformed() {
        assertIs<UpdateCheckResult.Incomparable>(checkForUpdate("1.0.0", descriptor("latest")))
        assertIs<UpdateCheckResult.Incomparable>(checkForUpdate("dev", descriptor("1.1.0")))
    }
}
