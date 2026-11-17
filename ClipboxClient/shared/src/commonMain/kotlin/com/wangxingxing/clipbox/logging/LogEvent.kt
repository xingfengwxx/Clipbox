/**
 * author : 王星星
 * date : 2026-11-17 16:07
 * email : 1099420259@qq.com
 * description : 日志事件模型；网络事件承载方法、路径模板、状态码、耗时与可选正文，业务事件只承载错误码、操作类型、记录 ID 与耗时
 */
package com.wangxingxing.clipbox.logging

sealed interface LogEvent {

    val operation: String

    data class Network(
        val method: String,
        val pathTemplate: String,
        override val operation: String,
        val status: Int?,
        val durationMillis: Long,
        val errorCode: String? = null,
        val clipId: String? = null,
        val requestBody: String? = null,
        val responseBody: String? = null,
        val authorization: String? = null,
    ) : LogEvent

    data class Socket(
        val event: String,
        val detail: String,
        override val operation: String = "socket",
        val closeCode: Int? = null,
        val authToken: String? = null,
    ) : LogEvent

    data class Business(
        override val operation: String,
        val errorCode: String? = null,
        val clipId: String? = null,
        val durationMillis: Long? = null,
    ) : LogEvent
}
