/**
 * author : 王星星
 * date : 2026-11-18 16:40
 * email : 1099420259@qq.com
 * description : 配对仓库测试：空库初始化成功落盘、409 已初始化、429 与离线映射、地址/设备名本地校验零网络请求，
 *               以及配对入口重构后的成功与非法码行为。
 *               注意：通过注入 MockEngine 保持测试完全离线；断言只检查请求路径、方法与公开接口不携带 Authorization。
 */
package com.wangxingxing.clipbox.repository

import com.wangxingxing.clipbox.network.bodyText
import com.wangxingxing.clipbox.network.respondJson
import com.wangxingxing.clipbox.settings.FakePreferencesDataStore
import com.wangxingxing.clipbox.settings.SettingsRepository
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PairingRepositoryTest {

    private fun settingsRepository() =
        SettingsRepository(FakePreferencesDataStore(), diagnosticLoggingDefault = false)

    private fun repository(
        settings: SettingsRepository,
        requests: MutableList<HttpRequestData> = mutableListOf(),
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = PairingRepository(settings) {
        MockEngine { request ->
            requests += request
            handler(request)
        }
    }

    private fun MockRequestHandleScope.respondCreatedDevice() = respondJson(
        """
            {
              "deviceId": "$DEVICE_ID",
              "token": "$DEVICE_TOKEN",
              "deviceName": "Pixel 8"
            }
        """.trimIndent(),
        status = HttpStatusCode.Created,
    )

    @Test
    fun `initialize stores url and credentials after server accepts the first device`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val settings = settingsRepository()
        val repo = repository(settings, requests) { respondCreatedDevice() }

        val result = repo.initialize("http://203.0.113.10:8080/", "  Pixel 8  ")

        assertEquals(PairResult.Success, result)
        val stored = settings.current()
        assertEquals("http://203.0.113.10:8080", stored.serverUrl)
        assertEquals(DEVICE_TOKEN, stored.deviceToken)
        assertEquals(DEVICE_ID, stored.deviceId)
        assertEquals("Pixel 8", stored.deviceName)
        assertEquals("http://203.0.113.10:8080", stored.lastServerUrl)

        val request = requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/v1/bootstrap", request.url.encodedPath)
        assertNull(request.headers[HttpHeaders.Authorization], "公开初始化接口不得携带设备令牌")
        assertTrue(request.bodyText().contains("\"deviceName\":\"Pixel 8\""), request.bodyText())
    }

    @Test
    fun `initialize surfaces already initialized without writing settings`() = runTest {
        val settings = settingsRepository()
        val repo = repository(settings) {
            respondJson(
                """{"error":{"code":"ALREADY_INITIALIZED","message":"server initialized"}}""",
                status = HttpStatusCode.Conflict,
            )
        }

        val result = repo.initialize("http://203.0.113.10:8080", "Pixel 8")

        assertEquals(PairResult.Failure(PairingError.AlreadyInitialized), result)
        assertNull(settings.current().serverUrl)
        assertNull(settings.current().deviceToken)
    }

    @Test
    fun `initialize maps rate limiting and offline`() = runTest {
        var call = 0
        val settings = settingsRepository()
        val repo = repository(settings) {
            call++
            if (call == 1) {
                respondJson(
                    """{"error":{"code":"RATE_LIMITED","message":"slow down"}}""",
                    status = HttpStatusCode.TooManyRequests,
                    retryAfterSeconds = "42",
                )
            } else {
                throw IllegalStateException("connection refused")
            }
        }

        assertEquals(
            PairResult.Failure(PairingError.RateLimited(42)),
            repo.initialize("http://203.0.113.10:8080", "Pixel 8"),
        )
        assertEquals(
            PairResult.Failure(PairingError.Network),
            repo.initialize("http://203.0.113.10:8080", "Pixel 8"),
        )
        assertNull(settings.current().deviceToken)
    }

    @Test
    fun `initialize validates input before any network request`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val repo = repository(settingsRepository(), requests) { respondCreatedDevice() }

        assertEquals(
            PairResult.Failure(PairingError.ServerUrl),
            repo.initialize("203.0.113.10:8080", "Pixel 8"),
        )
        assertEquals(
            PairResult.Failure(PairingError.DeviceName),
            repo.initialize("http://203.0.113.10:8080", "   "),
        )
        assertEquals(
            PairResult.Failure(PairingError.DeviceName),
            repo.initialize("http://203.0.113.10:8080", "x".repeat(65)),
        )
        assertTrue(requests.isEmpty(), "本地校验失败必须零网络请求")
    }

    @Test
    fun `pair still stores credentials after the refactor`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val settings = settingsRepository()
        val repo = repository(settings, requests) { respondCreatedDevice() }

        val result = repo.pair("http://203.0.113.10:8080", "123456", "Pixel 8")

        assertEquals(PairResult.Success, result)
        assertEquals(DEVICE_TOKEN, settings.current().deviceToken)
        val request = requests.single()
        assertEquals("/api/v1/pair", request.url.encodedPath)
        assertNull(request.headers[HttpHeaders.Authorization])
        assertTrue(request.bodyText().contains("\"code\":\"123456\""), request.bodyText())
    }

    @Test
    fun `pair rejects malformed codes without network`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val repo = repository(settingsRepository(), requests) { respondCreatedDevice() }

        assertEquals(
            PairResult.Failure(PairingError.InvalidCode),
            repo.pair("http://203.0.113.10:8080", "12345", "Pixel 8"),
        )
        assertEquals(
            PairResult.Failure(PairingError.InvalidCode),
            repo.pair("http://203.0.113.10:8080", "12a456", "Pixel 8"),
        )
        assertTrue(requests.isEmpty())
    }

    companion object {
        private const val DEVICE_ID = "01J8Y6Z3K4M5N6P7Q8R9S0T1V2"
        private val DEVICE_TOKEN = "d".repeat(43)
    }
}
