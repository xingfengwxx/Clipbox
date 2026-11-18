/**
 * author : 王星星
 * date : 2026-11-16 15:31
 * email : 1099420259@qq.com
 * description : 错误映射测试；校验状态码分类、429 Retry-After 解析、未知错误码回退与网络异常归类离线
 */
package com.wangxingxing.clipbox.network

import com.wangxingxing.clipbox.model.ApiErrorCode
import com.wangxingxing.clipbox.model.ApiFailure
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ErrorMappingTest {

    @Test
    fun unauthorizedMapsToUnauthorized() {
        assertEquals(
            ApiFailure.Unauthorized("invalid token"),
            mapHttpFailure(401, ApiErrorCode.UNAUTHORIZED, "invalid token", null),
        )
    }

    @Test
    fun conflictMapsToPermanent() {
        assertEquals(
            ApiFailure.Permanent(ApiErrorCode.CONFLICT, "same id different content"),
            mapHttpFailure(409, ApiErrorCode.CONFLICT, "same id different content", null),
        )
    }

    @Test
    fun alreadyInitializedConflictKeepsItsCode() {
        assertEquals(
            ApiFailure.Permanent(ApiErrorCode.ALREADY_INITIALIZED, "server initialized"),
            mapHttpFailure(409, ApiErrorCode.ALREADY_INITIALIZED, "server initialized", null),
        )
        assertEquals(
            ApiErrorCode.ALREADY_INITIALIZED,
            ApiErrorCode.fromWire("ALREADY_INITIALIZED"),
        )
    }

    @Test
    fun payloadTooLargeMapsToPermanent() {
        assertEquals(
            ApiFailure.Permanent(ApiErrorCode.PAYLOAD_TOO_LARGE, null),
            mapHttpFailure(413, ApiErrorCode.PAYLOAD_TOO_LARGE, null, null),
        )
    }

    @Test
    fun rateLimitedCarriesRetryAfter() {
        assertEquals(
            ApiFailure.RateLimited(60, "too many requests"),
            mapHttpFailure(429, ApiErrorCode.RATE_LIMITED, "too many requests", 60),
        )
    }

    @Test
    fun serverErrorMapsToRetryable() {
        assertEquals(
            ApiFailure.Retryable(ApiErrorCode.INTERNAL_ERROR, "boom"),
            mapHttpFailure(500, ApiErrorCode.INTERNAL_ERROR, "boom", null),
        )
    }

    @Test
    fun unknownErrorCodeFallsBackToUnknown() {
        val envelope = parseErrorEnvelope(
            """{"error": {"code": "FUTURE_CODE", "message": "new", "extra": 1}}"""
        )

        assertEquals(
            ApiErrorCode.UNKNOWN,
            ApiErrorCode.fromWire(envelope?.error?.code),
        )
    }

    @Test
    fun rateLimitResponseParsesRetryAfterHeader() = runTest {
        val service = stubService {
            respondJson(
                """{"error": {"code": "RATE_LIMITED", "message": "slow down"}}""",
                status = HttpStatusCode.TooManyRequests,
                retryAfterSeconds = "60",
            )
        }

        assertEquals(
            ApiResult.Failure(ApiFailure.RateLimited(60, "slow down")),
            service.pair(code = "123456", deviceName = "Pixel 8"),
        )
    }

    @Test
    fun rateLimitWithoutRetryAfterKeepsNull() = runTest {
        val service = stubService {
            respondJson(
                """{"error": {"code": "RATE_LIMITED", "message": "slow down"}}""",
                status = HttpStatusCode.TooManyRequests,
            )
        }

        val result = service.pair(code = "123456", deviceName = "Pixel 8")

        val failure = (result as ApiResult.Failure).failure
        assertEquals(ApiFailure.RateLimited(null, "slow down"), failure)
        assertNull((failure as ApiFailure.RateLimited).retryAfterSeconds)
    }

    @Test
    fun malformedErrorBodyStillClassifiedByStatus() = runTest {
        val service = stubService {
            respondJson("oops", status = HttpStatusCode.Unauthorized)
        }

        assertEquals(
            ApiResult.Failure(ApiFailure.Unauthorized(null)),
            service.getClips(since = 0),
        )
    }

    @Test
    fun networkExceptionMapsToOffline() = runTest {
        val service = stubService {
            throw IllegalStateException("connection refused")
        }

        assertEquals(
            ApiResult.Failure(ApiFailure.Offline),
            service.getClips(since = 0),
        )
    }
}
