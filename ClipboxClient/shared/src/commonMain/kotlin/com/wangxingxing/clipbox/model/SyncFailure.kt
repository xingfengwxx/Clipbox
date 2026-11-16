/**
 * author : 王星星
 * date : 2026-11-16 14:01
 * email : 1099420259@qq.com
 * description : 供 UI 与 SyncEngine 共用的稳定失败分类；不携带 HTTP 细节，UI 只按 ApiErrorCode 映射当前语言文案
 */
package com.wangxingxing.clipbox.model

sealed interface SyncFailure {
    val code: ApiErrorCode?

    data object Offline : SyncFailure {
        override val code: ApiErrorCode? = null
    }

    data object Unauthorized : SyncFailure {
        override val code: ApiErrorCode = ApiErrorCode.UNAUTHORIZED
    }

    data class RateLimited(val retryAfterSeconds: Long?) : SyncFailure {
        override val code: ApiErrorCode = ApiErrorCode.RATE_LIMITED
    }

    data class Retryable(override val code: ApiErrorCode? = null) : SyncFailure

    data class Permanent(override val code: ApiErrorCode) : SyncFailure
}

fun ApiFailure.toSyncFailure(): SyncFailure = when (this) {
    is ApiFailure.Offline -> SyncFailure.Offline
    is ApiFailure.Unauthorized -> SyncFailure.Unauthorized
    is ApiFailure.RateLimited -> SyncFailure.RateLimited(retryAfterSeconds)
    is ApiFailure.Retryable -> SyncFailure.Retryable(code)
    is ApiFailure.Permanent -> SyncFailure.Permanent(code)
}
