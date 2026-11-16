/**
 * author : 王星星
 * date : 2026-11-16 15:31
 * email : 1099420259@qq.com
 * description : HTTP 状态码与错误信封到 ApiFailure 的统一映射；4xx 为永久失败、5xx 可重试、429 携带 Retry-After
 */
package com.wangxingxing.clipbox.network

import com.wangxingxing.clipbox.model.ApiErrorCode
import com.wangxingxing.clipbox.model.ApiErrorEnvelope
import com.wangxingxing.clipbox.model.ApiFailure
import kotlinx.serialization.json.Json

// 客户端与服务端都按追加式演进，未知字段一律忽略
internal val ApiJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

// 这些状态码下重试不会改变结果；Outbox 直接标记永久失败
private val PERMANENT_STATUS_CODES = setOf(400, 404, 405, 409, 410, 413, 415)

/** 错误体不是合法 JSON 时返回 null，由调用方落到 INVALID_RESPONSE，不向 UI 抛解析异常 */
internal fun parseErrorEnvelope(body: String, json: Json = ApiJson): ApiErrorEnvelope? =
    runCatching { json.decodeFromString<ApiErrorEnvelope>(body) }.getOrNull()

internal fun mapHttpFailure(
    statusCode: Int,
    code: ApiErrorCode,
    message: String?,
    retryAfterSeconds: Long?,
): ApiFailure = when {
    statusCode == 401 -> ApiFailure.Unauthorized(message)
    statusCode == 429 -> ApiFailure.RateLimited(retryAfterSeconds, message)
    statusCode in PERMANENT_STATUS_CODES -> ApiFailure.Permanent(code, message)
    else -> ApiFailure.Retryable(code, message)
}
