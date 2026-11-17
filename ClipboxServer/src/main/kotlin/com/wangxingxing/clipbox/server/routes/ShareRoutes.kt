/**
 * author : 王星星
 * date : 2026-11-17 11:36
 * email : 1099420259@qq.com
 * description : 分享路由：Bearer 认证下的 POST /api/v1/shares 与公开只读的 GET /s/{token}。
 *               注意：令牌只出现在成功响应中，不得写入日志或错误信息；413/415 必须原样抛出，
 *                     否则会被解析兜底吞成 400，破坏统一错误映射；分享页为无脚本 HTML，CSP 逐响应追加。
 */
package com.wangxingxing.clipbox.server.routes

import com.wangxingxing.clipbox.server.plugins.DEVICE_AUTH_PROVIDER
import com.wangxingxing.clipbox.server.plugins.respondError
import com.wangxingxing.clipbox.server.service.SHARE_PAGE_CSP
import com.wangxingxing.clipbox.server.service.ShareCreateFailure
import com.wangxingxing.clipbox.server.service.SharePageRenderer
import com.wangxingxing.clipbox.server.service.SharePageResult
import com.wangxingxing.clipbox.server.service.ShareService
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.withCharset
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.PayloadTooLargeException
import io.ktor.server.plugins.UnsupportedMediaTypeException
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val DEFAULT_TTL_SECONDS = 86_400L

@Serializable
data class ShareCreatedResponse(
    val token: String,
    val url: String,
    val expiresAt: Long,
)

fun Route.shareRoutes(shareService: ShareService) {
    authenticate(DEVICE_AUTH_PROVIDER) {
        post("/api/v1/shares") {
            val body = call.receiveJsonOrNull<JsonObject>()
            // kotlinx.serialization 默认把 "3600" / "false" 静默转成数字与布尔，契约要求严格类型，因此逐字段手取。
            val clipId = body?.stringField("clipId")
            val ttlSeconds = when {
                body == null -> null
                !body.containsKey("ttlSeconds") -> DEFAULT_TTL_SECONDS
                else -> body.longField("ttlSeconds")
            }
            val oneTime = when {
                body == null -> null
                !body.containsKey("oneTime") -> false
                else -> body.booleanField("oneTime")
            }
            if (clipId == null || ttlSeconds == null || oneTime == null) {
                call.respondError(
                    HttpStatusCode.BadRequest,
                    "VALIDATION_ERROR",
                    "请求体必须包含字符串 clipId，可选整数 ttlSeconds 与布尔 oneTime",
                )
                return@post
            }

            try {
                val share = shareService.create(
                    clipId = clipId,
                    ttlSeconds = ttlSeconds,
                    oneTime = oneTime,
                )
                call.respond(
                    HttpStatusCode.Created,
                    ShareCreatedResponse(
                        token = share.token,
                        url = share.url,
                        expiresAt = share.expiresAt,
                    ),
                )
            } catch (e: ShareCreateFailure) {
                call.respondError(e.status, e.code, e.message)
            }
        }
    }
}

/**
 * 公开只读分享页，不经过设备认证。状态码与 api.md 一致：不存在 404，过期/已用 410；
 * 错误页同样携带 CSP，且正文只在 [SharePageResult.Page] 分支出现。
 */
fun Route.publicShareRoutes(shareService: ShareService) {
    get("/s/{token}") {
        val token = call.parameters["token"].orEmpty()
        val (status, html) = when (val result = shareService.openPage(token)) {
            is SharePageResult.Page -> HttpStatusCode.OK to
                SharePageRenderer.renderPage(result.content, result.expiresAt, result.oneTime)
            SharePageResult.NotFound -> HttpStatusCode.NotFound to SharePageRenderer.renderNotFound()
            SharePageResult.Expired -> HttpStatusCode.Gone to SharePageRenderer.renderExpired()
            SharePageResult.Consumed -> HttpStatusCode.Gone to SharePageRenderer.renderConsumed()
        }
        call.response.header("Content-Security-Policy", SHARE_PAGE_CSP)
        call.respondText(html, ContentType.Text.Html.withCharset(Charsets.UTF_8), status)
    }
}

private suspend inline fun <reified T : Any> ApplicationCall.receiveJsonOrNull(): T? =
    try {
        receive<T>()
    } catch (e: CancellationException) {
        throw e
    } catch (e: PayloadTooLargeException) {
        // 413/415 必须原样抛出，否则会被这里的兜底吞成参数错误。
        throw e
    } catch (e: UnsupportedMediaTypeException) {
        throw e
    } catch (e: Exception) {
        null
    }

private fun JsonObject.stringField(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonObject.longField(name: String): Long? {
    val primitive = (this[name] as? JsonPrimitive)?.takeUnless { it is JsonNull } ?: return null
    if (primitive.isString) return null
    return primitive.content.toLongOrNull()
}

private fun JsonObject.booleanField(name: String): Boolean? {
    val primitive = (this[name] as? JsonPrimitive)?.takeUnless { it is JsonNull } ?: return null
    if (primitive.isString) return null
    return when (primitive.content) {
        "true" -> true
        "false" -> false
        else -> null
    }
}
