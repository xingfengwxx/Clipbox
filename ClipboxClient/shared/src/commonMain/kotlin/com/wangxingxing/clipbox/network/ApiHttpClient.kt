/**
 * author : 王星星
 * date : 2026-11-16 15:31
 * email : 1099420259@qq.com
 * description : 构建共享 Ktor HttpClient；统一 baseUrl、JSON、Bearer 与超时配置。注意：服务器地址或令牌变更时必须重建客户端，不能复用旧实例
 */
package com.wangxingxing.clipbox.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json

private const val API_PATH_PREFIX = "/api/v1/"
private const val USER_AGENT = "Clipbox/1.0.0"

fun createApiHttpClient(
    serverUrl: String,
    tokenProvider: suspend () -> String?,
    engine: HttpClientEngine = defaultHttpClientEngine(),
): HttpClient {
    // 末尾斜杠决定相对路径如何拼接到 baseUrl，缺少时 "clips" 会替换掉 /api/v1 段
    val baseUrl = serverUrl.trimEnd('/') + API_PATH_PREFIX
    return HttpClient(engine) {
        // 非 2xx 不抛异常，由 ApiService 统一映射为 ApiFailure
        expectSuccess = false

        install(ContentNegotiation) {
            json(ApiJson)
        }

        defaultRequest {
            url(baseUrl)
            header(HttpHeaders.UserAgent, USER_AGENT)
        }

        install(Auth) {
            bearer {
                loadTokens {
                    tokenProvider()?.let { BearerTokens(it, "") }
                }
                // 配对接口是公开接口，不应携带设备令牌
                sendWithoutRequest { request ->
                    !request.url.build().encodedPath.endsWith("/pair")
                }
            }
        }

        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 30_000
            socketTimeoutMillis = 30_000
        }
    }
}

// 同步长连接单独建客户端：令牌只走首帧，不能装 Bearer；
// 也不设 request / socket 超时，避免服务端 30 秒 ping 间隔被判定为超时并断开
fun createSyncWebSocketClient(
    serverUrl: String,
    engine: HttpClientEngine = defaultHttpClientEngine(),
): HttpClient = HttpClient(engine) {
    expectSuccess = false
    defaultRequest { url(serverUrl) }
    install(WebSockets)
    install(HttpTimeout) {
        connectTimeoutMillis = 10_000
    }
}
