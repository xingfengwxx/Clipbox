/**
 * author : 王星星
 * date : 2026-11-17 10:29
 * email : 1099420259@qq.com
 * description : 请求体与路由方法限制：512 KiB 请求体上限、非 JSON 415、已知路径不支持的方法 405。
 *               注意：Content-Length 可被伪造，因此同时又按流式读取上限截断分块请求；
 *                     该插件必须早于 ContentNegotiation 安装，才能先包住原始字节流。
 */
package com.wangxingxing.clipbox.server.plugins

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.plugins.PayloadTooLargeException
import io.ktor.server.request.contentType
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray

const val MAX_REQUEST_BODY_BYTES = 524_288L

private val BODY_METHODS = setOf(HttpMethod.Post, HttpMethod.Patch, HttpMethod.Put)

/** 按流读取上限，覆盖没有 Content-Length 的分块请求；超限抛出 413 对应异常。 */
private val RequestBodyStreamLimit = createApplicationPlugin("RequestBodyStreamLimit") {
    onCallReceive { call ->
        // 无请求体的方法一律跳过：WebSocket 升级在 101 后原始连接已被接管，读取 body 会永久挂起。
        if (call.request.httpMethod !in BODY_METHODS) return@onCallReceive
        transformBody { body ->
            val bytes = body.readRemaining(MAX_REQUEST_BODY_BYTES + 1).readByteArray()
            if (bytes.size > MAX_REQUEST_BODY_BYTES) {
                throw PayloadTooLargeException(bytes.size.toLong())
            }
            ByteReadChannel(bytes)
        }
    }
}

fun Application.configureRequestLimits() {
    install(RequestBodyStreamLimit)
    intercept(ApplicationCallPipeline.Call) {
        if (rejectUnsupportedMethod(call)) return@intercept
        if (rejectOversizedBody(call)) return@intercept
        if (rejectNonJsonBody(call)) return@intercept
        proceed()
    }
}

private suspend fun rejectUnsupportedMethod(call: ApplicationCall): Boolean {
    val allowed = allowedMethods(call.request.path()) ?: return false
    if (call.request.httpMethod in allowed) return false

    call.respondError(HttpStatusCode.MethodNotAllowed, "METHOD_NOT_ALLOWED", "该路径不支持此 HTTP 方法")
    return true
}

private suspend fun rejectOversizedBody(call: ApplicationCall): Boolean {
    val contentLength = call.request.header(HttpHeaders.ContentLength)?.toLongOrNull() ?: return false
    if (contentLength <= MAX_REQUEST_BODY_BYTES) return false

    call.respondError(HttpStatusCode.PayloadTooLarge, "PAYLOAD_TOO_LARGE", "请求体超过 512 KiB")
    return true
}

private suspend fun rejectNonJsonBody(call: ApplicationCall): Boolean {
    if (call.request.httpMethod !in BODY_METHODS) return false
    // 无请求体的 POST（如取配对码）允许缺省 Content-Type，避免把空体当媒体类型错误。
    if (!hasBody(call)) return false

    if (call.request.contentType().withoutParameters().match(ContentType.Application.Json)) return false

    call.respondError(
        HttpStatusCode.UnsupportedMediaType,
        "UNSUPPORTED_MEDIA_TYPE",
        "请求体必须是 application/json",
    )
    return true
}

private fun hasBody(call: ApplicationCall): Boolean {
    val contentLength = call.request.header(HttpHeaders.ContentLength)?.toLongOrNull() ?: 0
    return contentLength > 0 || call.request.header(HttpHeaders.TransferEncoding) != null
}

private val CLIP_ID_PATH = Regex("^/api/v1/clips/[^/]+$")

private fun allowedMethods(path: String): Set<HttpMethod>? = when {
    path == "/healthz" -> setOf(HttpMethod.Get)
    path == "/api/v1/pair" -> setOf(HttpMethod.Post)
    path == "/api/v1/bootstrap" -> setOf(HttpMethod.Post)
    path == "/api/v1/devices/me/pairing-code" -> setOf(HttpMethod.Post)
    path == "/api/v1/clips" -> setOf(HttpMethod.Get, HttpMethod.Post)
    CLIP_ID_PATH.matches(path) -> setOf(HttpMethod.Patch, HttpMethod.Delete)
    path == "/api/v1/shares" -> setOf(HttpMethod.Post)
    path == "/api/v1/sync" -> setOf(HttpMethod.Get)
    path.startsWith("/s/") && path.length > 3 -> setOf(HttpMethod.Get)
    else -> null
}
