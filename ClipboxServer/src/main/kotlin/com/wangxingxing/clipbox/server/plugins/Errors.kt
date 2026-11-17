/**
 * author : 王星星
 * date : 2026-11-17 10:29
 * email : 1099420259@qq.com
 * description : 统一错误契约与异常映射：所有非 2xx 响应收敛为 {"error":{"code","message"}}，并把未捕获异常映射为稳定错误码。
 *               注意：响应绝不包含堆栈、SQL、令牌或正文；已提交的响应不得二次写入；错误码写入 call attributes 供请求日志使用。
 */
package com.wangxingxing.clipbox.server.plugins

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.isHandled
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.PayloadTooLargeException
import io.ktor.server.plugins.UnsupportedMediaTypeException
import io.ktor.server.request.path
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import io.ktor.util.AttributeKey
import java.sql.SQLException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

@Serializable
data class ApiErrorBody(
    val code: String,
    val message: String,
)

@Serializable
data class ApiErrorResponse(
    val error: ApiErrorBody,
)

/** 错误码属性：由 [respondError] 写入，请求日志读取后输出 errorCode 字段。 */
val ErrorCodeKey = AttributeKey<String>("clipbox.errorCode")

private val logger = LoggerFactory.getLogger("com.wangxingxing.clipbox.server.http.ErrorMapping")

/** 统一的错误响应入口；所有业务错误都必须经过这里，保证结构、日志字段与安全头一致。 */
suspend fun ApplicationCall.respondError(status: HttpStatusCode, code: String, message: String) {
    attributes.put(ErrorCodeKey, code)
    respond(status, ApiErrorResponse(ApiErrorBody(code = code, message = message)))
}

/** 限流响应固定附带 Retry-After（秒），客户端优先使用该值决定重试时机。 */
suspend fun ApplicationCall.respondRateLimited(retryAfterSeconds: Long) {
    response.header(HttpHeaders.RetryAfter, retryAfterSeconds.toString())
    respondError(
        HttpStatusCode.TooManyRequests,
        "RATE_LIMITED",
        "请求过于频繁，请在 $retryAfterSeconds 秒后重试",
    )
}

/**
 * 安装异常映射。拦截 Call 阶段以包住 routing；必须早于 routing 安装。
 * 返回 500 时服务端保留堆栈，客户端只拿到错误码与简短消息。
 */
fun Application.configureErrorMapping() {
    intercept(ApplicationCallPipeline.Call) {
        try {
            proceed()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            call.respondMappedError(e)
        }
    }
}

/** 路由末尾兜底：未知路径返回统一 404 JSON，而不是 Ktor 默认的纯文本。 */
fun Route.notFoundRoute() {
    route("{...}") {
        handle {
            call.respondError(HttpStatusCode.NotFound, "NOT_FOUND", "资源不存在")
        }
    }
}

private suspend fun ApplicationCall.respondMappedError(cause: Throwable) {
    // 响应已提交时不能再写；只记录，不改变客户端已收到的内容。
    if (isHandled) {
        logger.error("响应已提交后发生异常 requestId={}", attributes.getOrNull(RequestIdKey), cause)
        return
    }

    when (cause) {
        is PayloadTooLargeException -> respondError(
            HttpStatusCode.PayloadTooLarge,
            "PAYLOAD_TOO_LARGE",
            "请求体超过 512 KiB",
        )

        is UnsupportedMediaTypeException -> respondError(
            HttpStatusCode.UnsupportedMediaType,
            "UNSUPPORTED_MEDIA_TYPE",
            "请求体必须是 application/json",
        )

        is ContentTransformationException -> respondError(
            HttpStatusCode.BadRequest,
            "VALIDATION_ERROR",
            "请求体不是合法 JSON 或缺少必填字段",
        )

        is BadRequestException -> respondError(
            HttpStatusCode.BadRequest,
            "VALIDATION_ERROR",
            cause.message ?: "请求参数错误",
        )

        is NotFoundException -> respondError(HttpStatusCode.NotFound, "NOT_FOUND", "资源不存在")

        else -> {
            val requestId = attributes.getOrNull(RequestIdKey)
            if (isSqliteBusy(cause)) {
                logger.warn("数据库繁忙 requestId={} path={}", requestId, requestPathTemplate(request.path()))
                respondError(HttpStatusCode.ServiceUnavailable, "SERVICE_UNAVAILABLE", "服务暂时不可用")
            } else {
                // 堆栈只进服务端日志；响应不携带任何异常细节。
                logger.error("未捕获异常 requestId={} path={}", requestId, requestPathTemplate(request.path()), cause)
                respondError(HttpStatusCode.InternalServerError, "INTERNAL_ERROR", "服务端内部错误")
            }
        }
    }
}

private fun isSqliteBusy(cause: Throwable): Boolean =
    generateSequence(cause) { it.cause }
        .filterIsInstance<SQLException>()
        .any { error ->
            val message = error.message ?: return@any false
            message.contains("busy", ignoreCase = true) || message.contains("locked", ignoreCase = true)
        }
