/**
 * author : 王星星
 * date : 2026-11-18 16:45
 * email : 1099420259@qq.com
 * description : 设备取码仓库测试：未配对匿名请求不带 Authorization、已配对请求带 Bearer 头、
 *               成功返回码与过期时间、401/429/离线映射，并验证配对码不会写入设置存储（本类也不依赖 Room）。
 *               注意：断言中出现的码为测试固定值，不得来自真实服务端。
 */
package com.wangxingxing.clipbox.repository

import com.wangxingxing.clipbox.network.TEST_TOKEN
import com.wangxingxing.clipbox.network.respondJson
import com.wangxingxing.clipbox.network.stubService
import com.wangxingxing.clipbox.settings.FakePreferencesDataStore
import com.wangxingxing.clipbox.settings.SettingsRepository
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class PairingCodeRepositoryTest {

    private fun repository(
        requests: MutableList<HttpRequestData> = mutableListOf(),
        token: String? = TEST_TOKEN,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = PairingCodeRepository(stubService(token = token, requests = requests, handler = handler))

    @Test
    fun `create returns code and expiry from the authenticated endpoint`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val repo = repository(requests) {
            respondJson(
                """{"code":"$CODE","expiresAt":$EXPIRES_AT}""",
                status = HttpStatusCode.Created,
            )
        }

        val result = repo.create()

        assertEquals(
            PairingCodeResult.Success(PairingCode(code = CODE, expiresAt = EXPIRES_AT)),
            result,
        )
        val request = requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/v1/devices/me/pairing-code", request.url.encodedPath)
        assertEquals("Bearer $TEST_TOKEN", request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `create without a device token omits authorization`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val repo = repository(requests, token = null) {
            respondJson(
                """{"code":"$CODE","expiresAt":$EXPIRES_AT}""",
                status = HttpStatusCode.Created,
            )
        }

        assertEquals(PairingCodeResult.Success(PairingCode(code = CODE, expiresAt = EXPIRES_AT)), repo.create())

        val request = requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/v1/devices/me/pairing-code", request.url.encodedPath)
        assertNull(request.headers[HttpHeaders.Authorization], "未配对设备取码不得携带 Authorization")
    }

    @Test
    fun `create maps unauthorized responses`() = runTest {
        val repo = repository {
            respondJson(
                """{"error":{"code":"UNAUTHORIZED","message":"invalid token"}}""",
                status = HttpStatusCode.Unauthorized,
            )
        }

        assertEquals(PairingCodeResult.Failure(PairingCodeError.Unauthorized), repo.create())
    }

    @Test
    fun `create maps rate limiting with retry after`() = runTest {
        val repo = repository {
            respondJson(
                """{"error":{"code":"RATE_LIMITED","message":"slow down"}}""",
                status = HttpStatusCode.TooManyRequests,
                retryAfterSeconds = "30",
            )
        }

        assertEquals(PairingCodeResult.Failure(PairingCodeError.RateLimited(30)), repo.create())
    }

    @Test
    fun `create maps transport failures to offline`() = runTest {
        val repo = repository {
            throw IllegalStateException("connection refused")
        }

        assertEquals(PairingCodeResult.Failure(PairingCodeError.Network), repo.create())
    }

    @Test
    fun `pairing code is never written to settings`() = runTest {
        val settings = SettingsRepository(FakePreferencesDataStore(), diagnosticLoggingDefault = false)
        settings.setServerUrl("http://203.0.113.10:8080")
        settings.setPairingInfo(
            deviceToken = "device-token",
            deviceId = "01J8Y6Z3K4M5N6P7Q8R9S0T1V2",
            deviceName = "Pixel 8",
            lastServerUrl = "http://203.0.113.10:8080",
        )
        val before = settings.current()
        val repo = repository {
            respondJson(
                """{"code":"$CODE","expiresAt":$EXPIRES_AT}""",
                status = HttpStatusCode.Created,
            )
        }

        assertEquals(PairingCodeResult.Success(PairingCode(code = CODE, expiresAt = EXPIRES_AT)), repo.create())

        val after = settings.current()
        assertEquals(before, after, "取码不得改动任何设置项")
        assertFalse(after.toString().contains(CODE), "配对码不得进入设置存储")
    }

    companion object {
        private const val CODE = "654321"
        private const val EXPIRES_AT = 1_730_000_600_000L
    }
}
