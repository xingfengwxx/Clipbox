/**
 * author : 王星星
 * date : 2026-11-17 10:29
 * email : 1099420259@qq.com
 * description : 请求标识与脱敏访问日志：生成 X-Request-Id，并按白名单字段输出每个请求一条日志。
 *               注意：只允许记录 requestId、方法、路径模板、状态码、耗时、deviceId 与错误码；
 *                     禁止记录请求/响应体、令牌、配对码与分享令牌，/s/{token} 必须按模板记录。
 */
package com.wangxingxing.clipbox.server.plugins

import io.ktor.http.HttpHeaders
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.auth.principal
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.header
import io.ktor.util.AttributeKey
import java.util.UUID
import org.slf4j.LoggerFactory

/** 每个请求生成的标识，写入属性并回显到 X-Request-Id 响应头。 */
val RequestIdKey = AttributeKey<String>("clipbox.requestId")

internal val requestLogger = LoggerFactory.getLogger("com.wangxingxing.clipbox.server.http.RequestLog")

fun Application.configureRequestLogging() {
    intercept(ApplicationCallPipeline.Call) {
        val requestId = UUID.randomUUID().toString()
        call.attributes.put(RequestIdKey, requestId)
        call.response.header(HttpHeaders.XRequestId, requestId)

        val startedAtNanos = System.nanoTime()
        try {
            proceed()
        } finally {
            val latencyMillis = (System.nanoTime() - startedAtNanos) / 1_000_000
            requestLogger.info(
                "requestId={} method={} pathTemplate={} status={} latencyMs={} deviceId={} errorCode={}",
                requestId,
                call.request.httpMethod.value,
                requestPathTemplate(call.request.path()),
                call.response.status()?.value ?: 0,
                latencyMillis,
                call.principal<DevicePrincipal>()?.deviceId ?: "-",
                call.attributes.getOrNull(ErrorCodeKey) ?: "-",
            )
        }
    }
}

/**
 * 把真实路径收敛到固定模板，避免把 ULID、分享 token 等易变或敏感片段写进日志。
 * 未匹配的路径统一记为 /{unmatched}，不记录原始输入。
 */
fun requestPathTemplate(path: String): String = when {
    path == "/healthz" -> "/healthz"
    path == "/api/v1/pair" -> "/api/v1/pair"
    path == "/api/v1/admin/pairing-codes" -> "/api/v1/admin/pairing-codes"
    path == "/api/v1/clips" -> "/api/v1/clips"
    CLIP_ID_PATH.matches(path) -> "/api/v1/clips/{id}"
    path == "/api/v1/shares" -> "/api/v1/shares"
    path == "/api/v1/sync" -> "/api/v1/sync"
    path.startsWith("/s/") && path.length > 3 -> "/s/{token}"
    else -> "/{unmatched}"
}

private val CLIP_ID_PATH = Regex("^/api/v1/clips/[^/]+$")
