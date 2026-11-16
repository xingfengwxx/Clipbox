/**
 * author : 王星星
 * date : 2026-11-16 14:01
 * email : 1099420259@qq.com
 * description : 网络层统一失败分类；Outbox 与同步引擎按类型分支，不解析服务端 message 决定行为
 */
package com.wangxingxing.clipbox.model

sealed interface ApiFailure {
    val message: String?

    data class Retryable(
        val code: ApiErrorCode? = null,
        override val message: String? = null,
    ) : ApiFailure

    data class Permanent(
        val code: ApiErrorCode,
        override val message: String? = null,
    ) : ApiFailure

    data class Unauthorized(
        override val message: String? = null,
    ) : ApiFailure

    data class RateLimited(
        // 服务端 Retry-After 的秒数；缺失时由调用方使用默认退避
        val retryAfterSeconds: Long?,
        override val message: String? = null,
    ) : ApiFailure

    data object Offline : ApiFailure {
        override val message: String? = null
    }
}
