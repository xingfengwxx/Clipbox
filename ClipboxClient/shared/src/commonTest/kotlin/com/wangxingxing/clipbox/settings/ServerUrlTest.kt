/**
 * author : 王星星
 * date : 2026-11-16 15:07
 * email : 1099420259@qq.com
 * description : 服务器地址校验测试；覆盖缺少 scheme、缺少主机、尾斜杠规范化与 http 明文警示
 */
package com.wangxingxing.clipbox.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServerUrlTest {

    @Test
    fun addressWithoutSchemeIsRejected() {
        val result = ServerUrlValidator.validate("203.0.113.10:8080")
        assertError(ServerUrlError.MISSING_SCHEME, result)
    }

    @Test
    fun blankAddressIsRejected() {
        assertError(ServerUrlError.MISSING_SCHEME, ServerUrlValidator.validate("   "))
    }

    @Test
    fun unsupportedSchemeIsRejected() {
        assertError(ServerUrlError.UNSUPPORTED_SCHEME, ServerUrlValidator.validate("ftp://example.com"))
    }

    @Test
    fun missingHostIsRejected() {
        assertError(ServerUrlError.MISSING_HOST, ServerUrlValidator.validate("http://"))
        assertError(ServerUrlError.MISSING_HOST, ServerUrlValidator.validate("http:///path"))
        assertError(ServerUrlError.MISSING_HOST, ServerUrlValidator.validate("http://:8080"))
    }

    @Test
    fun trailingSlashIsRemovedAndHttpIsMarkedInsecure() {
        val result = ServerUrlValidator.validate("http://203.0.113.10:8080/")
        val valid = result as ServerUrlValidation.Valid
        assertEquals("http://203.0.113.10:8080", valid.url)
        assertTrue(valid.insecure)
    }

    @Test
    fun httpsAddressIsNotMarkedInsecure() {
        val result = ServerUrlValidator.validate("https://example.com/")
        val valid = result as ServerUrlValidation.Valid
        assertEquals("https://example.com", valid.url)
        assertFalse(valid.insecure)
    }

    @Test
    fun insecureFlagFollowsScheme() {
        assertTrue(isInsecureServerUrl("http://203.0.113.10:8080"))
        assertFalse(isInsecureServerUrl("https://example.com"))
        assertFalse(isInsecureServerUrl(null))
    }

    private fun assertError(expected: ServerUrlError, result: ServerUrlValidation) {
        val invalid = result as ServerUrlValidation.Invalid
        assertEquals(expected, invalid.error)
    }
}
