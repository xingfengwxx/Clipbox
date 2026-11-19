/**
 * author : 王星星
 * date : 2026-11-19 11:08
 * email : 1099420259@qq.com
 * description : 更新接口契约测试；用 MockEngine 校验固定官方地址、无 Authorization、说明回退与超时 / 503 分类，不发起真实网络请求
 */
package com.wangxingxing.clipbox.update

import com.wangxingxing.clipbox.settings.AppLanguage
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val OFFICIAL_URL = "http://official.local:8080"
private const val ZERO_SHA = "0000000000000000000000000000000000000000000000000000000000000000"

class UpdateApiTest {

    private fun api(
        requests: MutableList<HttpRequestData> = mutableListOf(),
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): UpdateApi {
        val engine = MockEngine { request ->
            requests += request
            handler(request)
        }
        return UpdateApi(OFFICIAL_URL, engine)
    }

    private fun MockRequestHandleScope.jsonResponse(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ): HttpResponseData = respond(
        content = body,
        status = status,
        headers = Headers.build { append(HttpHeaders.ContentType, "application/json") },
    )

    private fun updateBody(
        version: String = "1.1.0",
        notes: String = """{"zh-CN":"新增检查更新。","zh-TW":"新增檢查更新。","en":"Added update checks."}""",
    ): String = """
        {
          "version": "$version",
          "publishedAt": "2026-11-19T08:00:00Z",
          "futureField": {"ignored": true},
          "notes": $notes,
          "platforms": [
            {
              "id": "android",
              "available": true,
              "file": "Theca-v1.1.0-android-release.apk",
              "sizeBytes": 12345678,
              "sha256": "$ZERO_SHA",
              "downloadUrl": "http://203.0.113.10:8080/downloads/Theca-v1.1.0-android-release.apk",
              "futureField": 1
            },
            {"id": "ios", "available": false},
            {"id": "macos", "available": false},
            {"id": "windows", "available": false},
            {"id": "linux", "available": false}
          ]
        }
    """.trimIndent()

    @Test
    fun latestRequestsFixedOfficialUrlWithoutAuthorization() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val api = api(requests) { jsonResponse(updateBody()) }

        assertIs<UpdateApiResult.Success>(api.latest())

        val request = requests.single()
        assertEquals("$OFFICIAL_URL/api/v1/app/latest", request.url.toString())
        assertNull(request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun latestParsesDescriptorAndIgnoresUnknownFields() = runTest {
        val api = api { jsonResponse(updateBody()) }

        val descriptor = assertIs<UpdateApiResult.Success>(api.latest()).descriptor
        assertEquals("1.1.0", descriptor.version)
        assertEquals("2026-11-19T08:00:00Z", descriptor.publishedAt)
        assertEquals("新增檢查更新。", descriptor.notes.forLanguage(AppLanguage.ZH_TW))

        val android = descriptor.platform(UpdatePlatformId.ANDROID)
        assertEquals("Theca-v1.1.0-android-release.apk", android?.file)
        assertEquals(12_345_678L, android?.sizeBytes)
        assertEquals(ZERO_SHA, android?.sha256)
        assertTrue(android?.available == true)
        assertFalse(descriptor.platform(UpdatePlatformId.IOS)?.available == true)
    }

    @Test
    fun missingTraditionalChineseAndEnglishFallBackToSimplified() = runTest {
        val api = api { jsonResponse(updateBody(notes = """{"zh-CN":"仅简中说明"}""")) }

        val notes = assertIs<UpdateApiResult.Success>(api.latest()).descriptor.notes
        assertEquals("仅简中说明", notes.zhCN)
        assertEquals("仅简中说明", notes.zhTW)
        assertEquals("仅简中说明", notes.en)
    }

    @Test
    fun missingSimplifiedChineseIsInvalidResponse() = runTest {
        val api = api { jsonResponse(updateBody(notes = """{"en":"only english"}""")) }

        val failure = assertIs<UpdateApiResult.Failure>(api.latest())
        assertEquals(UpdateFailureReason.INVALID_RESPONSE, failure.reason)
    }

    @Test
    fun serviceUnavailableIsDistinctFromOtherFailures() = runTest {
        val api = api { jsonResponse("""{"error":{"code":"SERVICE_UNAVAILABLE"}}""", HttpStatusCode.ServiceUnavailable) }

        val failure = assertIs<UpdateApiResult.Failure>(api.latest())
        assertEquals(UpdateFailureReason.SERVICE_UNAVAILABLE, failure.reason)
    }

    @Test
    fun rateLimitedIsDistinctFromTimeout() = runTest {
        val api = api { jsonResponse("""{"error":{"code":"RATE_LIMITED"}}""", HttpStatusCode.TooManyRequests) }

        val failure = assertIs<UpdateApiResult.Failure>(api.latest())
        assertEquals(UpdateFailureReason.RATE_LIMITED, failure.reason)
    }

    @Test
    fun serverErrorAndMalformedBodyAreInvalidResponses() = runTest {
        val serverError = api { jsonResponse("""{"error":{"code":"INTERNAL_ERROR"}}""", HttpStatusCode.InternalServerError) }
        assertEquals(
            UpdateFailureReason.INVALID_RESPONSE,
            assertIs<UpdateApiResult.Failure>(serverError.latest()).reason,
        )

        val malformed = api { jsonResponse("not json") }
        assertEquals(
            UpdateFailureReason.INVALID_RESPONSE,
            assertIs<UpdateApiResult.Failure>(malformed.latest()).reason,
        )
    }

    @Test
    fun requestTimeoutIsDistinctFromOffline() = runTest {
        val timeoutApi = api { request -> throw HttpRequestTimeoutException(request.url.toString(), 1_000L) }
        assertEquals(
            UpdateFailureReason.TIMEOUT,
            assertIs<UpdateApiResult.Failure>(timeoutApi.latest()).reason,
        )

        val offlineApi = api { throw IOException("connect failed") }
        assertEquals(
            UpdateFailureReason.OFFLINE,
            assertIs<UpdateApiResult.Failure>(offlineApi.latest()).reason,
        )
    }
}
