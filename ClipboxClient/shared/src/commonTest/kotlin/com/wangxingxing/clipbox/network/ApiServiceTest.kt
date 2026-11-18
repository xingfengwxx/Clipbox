/**
 * author : 王星星
 * date : 2026-11-16 15:31
 * email : 1099420259@qq.com
 * description : ApiService 契约测试；用 MockEngine 校验路径、方法、Bearer 头与 DTO 解码，不发起真实网络请求
 */
package com.wangxingxing.clipbox.network

import com.wangxingxing.clipbox.model.ApiErrorCode
import com.wangxingxing.clipbox.model.ApiFailure
import com.wangxingxing.clipbox.model.CreateClipResponse
import com.wangxingxing.clipbox.model.CreateShareResponse
import com.wangxingxing.clipbox.model.DeleteClipResponse
import com.wangxingxing.clipbox.model.PairResponse
import com.wangxingxing.clipbox.model.PatchPinnedResponse
import com.wangxingxing.clipbox.model.SyncResponse
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApiServiceTest {

    private val clipId = "01J8Z6Z3K4M5N6P7Q8R9S0T1V2"
    private val deviceId = "01J8Y6Z3K4M5N6P7Q8R9S0T1V2"

    private fun syncBody(): String = """
        {
          "items": [
            {
              "id": "$clipId",
              "content": "hello",
              "sourceDeviceId": "$deviceId",
              "sourceDeviceName": "Pixel 8",
              "pinned": false,
              "createdAt": 1730000000000,
              "serverSeq": 41,
              "deleted": false,
              "futureField": "ignored"
            }
          ],
          "nextSince": 41,
          "hasMore": false,
          "resyncRequired": false,
          "minAvailableSeq": 1,
          "serverMaxSeq": 41,
          "futureEnvelope": {"a": 1}
        }
    """.trimIndent()

    @Test
    fun pairPostsWithoutAuthorizationHeader() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val service = stubService(requests = requests) {
            respondJson(
                """
                    {
                      "deviceId": "$deviceId",
                      "token": "new-device-token",
                      "deviceName": "Pixel 8",
                      "futureField": true
                    }
                """.trimIndent(),
                status = HttpStatusCode.Created,
            )
        }

        val result = service.pair(code = "123456", deviceName = "Pixel 8")

        val success = assertIs<ApiResult.Success<PairResponse>>(result)
        assertEquals(deviceId, success.value.deviceId)
        assertEquals("new-device-token", success.value.token)

        val request = requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/v1/pair", request.url.encodedPath)
        assertNull(request.headers[HttpHeaders.Authorization])
        assertTrue(request.bodyText().contains("\"code\":\"123456\""))
        assertTrue(request.bodyText().contains("\"deviceName\":\"Pixel 8\""))
    }

    @Test
    fun getClipsSendsSinceLimitAndBearerToken() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val service = stubService(requests = requests) {
            respondJson(syncBody())
        }

        val result = service.getClips(since = 42, limit = 200)

        val success = assertIs<ApiResult.Success<SyncResponse>>(result)
        assertEquals(41L, success.value.nextSince)
        assertEquals(1, success.value.items.size)
        assertEquals("hello", success.value.items.first().content)

        val request = requests.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/api/v1/clips", request.url.encodedPath)
        assertEquals("42", request.url.parameters["since"])
        assertEquals("200", request.url.parameters["limit"])
        assertNull(request.url.parameters["full"], "增量拉取不得携带 full")
        assertEquals("Bearer $TEST_TOKEN", request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun getClipsFullSyncSendsFullFlag() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val service = stubService(requests = requests) {
            respondJson(syncBody())
        }

        service.getClips(since = 0, limit = 200, full = true)

        assertEquals("true", requests.single().url.parameters["full"])
    }

    @Test
    fun createClipAcceptsIdempotentCreatedAndOk() = runTest {
        listOf(HttpStatusCode.Created, HttpStatusCode.OK).forEach { status ->
            val requests = mutableListOf<HttpRequestData>()
            val service = stubService(requests = requests) {
                respondJson(
                    """
                        {
                          "id": "$clipId",
                          "serverSeq": 41,
                          "createdAt": 1730000000000,
                          "pinned": false,
                          "deleted": false
                        }
                    """.trimIndent(),
                    status = status,
                )
            }

            val result = service.createClip(id = clipId, content = "hello", createdAt = 1730000000000)

            val success = assertIs<ApiResult.Success<CreateClipResponse>>(result)
            assertEquals(41L, success.value.serverSeq)

            val request = requests.single()
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/v1/clips", request.url.encodedPath)
            assertEquals("Bearer $TEST_TOKEN", request.headers[HttpHeaders.Authorization])
            assertTrue(request.bodyText().contains("\"content\":\"hello\""))
        }
    }

    @Test
    fun updateClipPatchesPinnedState() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val service = stubService(requests = requests) {
            respondJson(
                """
                    {"id": "$clipId", "pinned": true, "serverSeq": 42, "deleted": false}
                """.trimIndent()
            )
        }

        val result = service.updateClip(id = clipId, pinned = true)

        val success = assertIs<ApiResult.Success<PatchPinnedResponse>>(result)
        assertEquals(42L, success.value.serverSeq)

        val request = requests.single()
        assertEquals(HttpMethod.Patch, request.method)
        assertEquals("/api/v1/clips/$clipId", request.url.encodedPath)
        assertTrue(request.bodyText().contains("\"pinned\":true"))
    }

    @Test
    fun deleteClipSendsDeleteToClipPath() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val service = stubService(requests = requests) {
            respondJson("""{"id": "$clipId", "deleted": true, "serverSeq": 43}""")
        }

        val result = service.deleteClip(id = clipId)

        val success = assertIs<ApiResult.Success<DeleteClipResponse>>(result)
        assertEquals(43L, success.value.serverSeq)

        val request = requests.single()
        assertEquals(HttpMethod.Delete, request.method)
        assertEquals("/api/v1/clips/$clipId", request.url.encodedPath)
        assertEquals("Bearer $TEST_TOKEN", request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun createSharePostsRequestAndReturnsServerUrl() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val service = stubService(requests = requests) {
            respondJson(
                """
                    {
                      "token": "share-token",
                      "url": "http://203.0.113.10:8080/s/share-token",
                      "expiresAt": 1730086400000
                    }
                """.trimIndent(),
                status = HttpStatusCode.Created,
            )
        }

        val result = service.createShare(clipId = clipId, ttlSeconds = 3_600, oneTime = true)

        val success = assertIs<ApiResult.Success<CreateShareResponse>>(result)
        assertEquals("http://203.0.113.10:8080/s/share-token", success.value.url)

        val request = requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/v1/shares", request.url.encodedPath)
        assertTrue(request.bodyText().contains("\"clipId\":\"$clipId\""))
        assertTrue(request.bodyText().contains("\"ttlSeconds\":3600"))
        assertTrue(request.bodyText().contains("\"oneTime\":true"))
    }

    @Test
    fun invalidJsonSuccessBodyMapsToInvalidResponse() = runTest {
        val service = stubService {
            respondJson("this is not json", status = HttpStatusCode.OK)
        }

        val result = service.getClips(since = 0)

        assertEquals(
            ApiResult.Failure(ApiFailure.Retryable(ApiErrorCode.INVALID_RESPONSE)),
            result,
        )
    }
}
