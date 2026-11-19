/**
 * author : 王星星
 * date : 2026-11-19 11:08
 * email : 1099420259@qq.com
 * description : 官方应用更新接口客户端；独立 HttpClient，不安装鉴权插件，只请求 /api/v1/app/latest。
 *               注意：officialServerUrl 必须来自 PlatformServices.defaultServerUrl，禁止传入配对服务器地址或任何令牌
 */
package com.wangxingxing.clipbox.update

import com.wangxingxing.clipbox.network.ApiJson
import com.wangxingxing.clipbox.network.defaultHttpClientEngine
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.json.Json

private const val API_PATH_SUFFIX = "/api/v1/"
private const val LATEST_PATH = "app/latest"
private const val USER_AGENT = "Clipbox/1.1.0"

class UpdateApi(
    officialServerUrl: String,
    engine: HttpClientEngine = defaultHttpClientEngine(),
    private val json: Json = ApiJson,
) {

    // 更新接口公开且无鉴权：这里刻意不安装 Auth 插件，避免任何设备令牌被携带
    private val client = HttpClient(engine) {
        expectSuccess = false
        defaultRequest {
            url(officialServerUrl.trimEnd('/') + API_PATH_SUFFIX)
            header(HttpHeaders.UserAgent, USER_AGENT)
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 30_000
            socketTimeoutMillis = 30_000
        }
    }

    suspend fun latest(): UpdateApiResult {
        return try {
            val response = client.get(LATEST_PATH)
            when {
                response.status.isSuccess() -> decode(response.bodyAsText())
                response.status == HttpStatusCode.ServiceUnavailable ->
                    UpdateApiResult.Failure(UpdateFailureReason.SERVICE_UNAVAILABLE)
                response.status == HttpStatusCode.TooManyRequests ->
                    UpdateApiResult.Failure(UpdateFailureReason.RATE_LIMITED)
                else -> UpdateApiResult.Failure(UpdateFailureReason.INVALID_RESPONSE)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpRequestTimeoutException) {
            UpdateApiResult.Failure(UpdateFailureReason.TIMEOUT)
        } catch (e: ConnectTimeoutException) {
            UpdateApiResult.Failure(UpdateFailureReason.TIMEOUT)
        } catch (e: SocketTimeoutException) {
            UpdateApiResult.Failure(UpdateFailureReason.TIMEOUT)
        } catch (e: IOException) {
            UpdateApiResult.Failure(UpdateFailureReason.OFFLINE)
        }
    }

    fun close() {
        client.close()
    }

    // 解析失败不保留响应体与异常信息，避免说明内容或文件地址进入日志
    private fun decode(body: String): UpdateApiResult =
        runCatching { json.decodeFromString<UpdateDescriptor>(body) }
            .fold(
                onSuccess = { UpdateApiResult.Success(it) },
                onFailure = { UpdateApiResult.Failure(UpdateFailureReason.INVALID_RESPONSE) },
            )
}
