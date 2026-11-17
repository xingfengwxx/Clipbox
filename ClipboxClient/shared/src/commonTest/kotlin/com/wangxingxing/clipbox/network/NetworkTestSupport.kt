/**
 * author : 王星星
 * date : 2026-11-16 15:31
 * email : 1099420259@qq.com
 * description : 网络层测试支撑；用 MockEngine 构造不触网的 ApiService，并记录每个请求供断言
 */
package com.wangxingxing.clipbox.network

import com.wangxingxing.clipbox.logging.AppLogger
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent

internal const val TEST_SERVER_URL = "http://test.local:8080"
internal const val TEST_TOKEN = "test-device-token"

internal fun stubService(
    token: String? = TEST_TOKEN,
    requests: MutableList<HttpRequestData> = mutableListOf(),
    logger: AppLogger? = null,
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
): ApiService {
    val engine = MockEngine { request ->
        requests += request
        handler(request)
    }
    val client = createApiHttpClient(
        serverUrl = TEST_SERVER_URL,
        tokenProvider = { token },
        engine = engine,
    )
    return ApiService(client, logger = logger)
}

// 可选 Retry-After 需要在同一份响应头里给出
internal fun MockRequestHandleScope.respondJson(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
    retryAfterSeconds: String? = null,
): HttpResponseData {
    val headers = Headers.build {
        append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        retryAfterSeconds?.let { append(HttpHeaders.RetryAfter, it) }
    }
    return respond(content = body, status = status, headers = headers)
}

internal fun HttpRequestData.bodyText(): String = when (val content = body) {
    is TextContent -> content.text
    is OutgoingContent.ByteArrayContent -> content.bytes().decodeToString()
    else -> ""
}
