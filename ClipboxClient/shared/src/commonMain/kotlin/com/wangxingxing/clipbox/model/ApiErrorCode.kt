/**
 * author : 王星星
 * date : 2026-11-16 14:01
 * email : 1099420259@qq.com
 * description : 服务端错误码枚举，覆盖 api.md 错误码总表；未知错误码统一落到 UNKNOWN，避免 v1 追加错误码时客户端崩溃
 */
package com.wangxingxing.clipbox.model

enum class ApiErrorCode(val wireValue: String) {
    VALIDATION_ERROR("VALIDATION_ERROR"),
    INVALID_CODE("INVALID_CODE"),
    CODE_EXPIRED("CODE_EXPIRED"),
    CODE_USED("CODE_USED"),
    UNAUTHORIZED("UNAUTHORIZED"),
    NOT_FOUND("NOT_FOUND"),
    METHOD_NOT_ALLOWED("METHOD_NOT_ALLOWED"),
    CONFLICT("CONFLICT"),
    SHARE_EXPIRED("SHARE_EXPIRED"),
    SHARE_CONSUMED("SHARE_CONSUMED"),
    PAYLOAD_TOO_LARGE("PAYLOAD_TOO_LARGE"),
    UNSUPPORTED_MEDIA_TYPE("UNSUPPORTED_MEDIA_TYPE"),
    RATE_LIMITED("RATE_LIMITED"),
    INTERNAL_ERROR("INTERNAL_ERROR"),
    SERVICE_UNAVAILABLE("SERVICE_UNAVAILABLE"),
    INVALID_RESPONSE("INVALID_RESPONSE"),
    UNKNOWN(""),
    ;

    companion object {
        fun fromWire(code: String?): ApiErrorCode =
            entries.firstOrNull { it.wireValue == code } ?: UNKNOWN
    }
}
