/**
 * author : 王星星
 * date : 2026-11-16 14:01
 * email : 1099420259@qq.com
 * description : WebSocket 认证与事件 DTO，字段与 api.md §4 一致；事件共用 ClipDto，墓碑事件只带 id/serverSeq/deleted
 */
package com.wangxingxing.clipbox.model

import kotlinx.serialization.Serializable

object WsMessageType {
    const val AUTH = "auth"
    const val AUTH_OK = "auth.ok"
    const val ERROR = "error"
    const val CLIP_CREATED = "clip.created"
    const val CLIP_UPDATED = "clip.updated"
    const val CLIP_DELETED = "clip.deleted"
}

@Serializable
data class WsAuthRequest(
    val type: String,
    val token: String,
) {
    companion object {
        fun auth(token: String): WsAuthRequest = WsAuthRequest(WsMessageType.AUTH, token)
    }
}

@Serializable
data class WsAuthOk(
    val type: String,
    val deviceId: String,
    val serverSeq: Long,
)

@Serializable
data class WsErrorFrame(
    val type: String,
    val error: ApiErrorBody,
)

@Serializable
data class WsClipEvent(
    val type: String,
    val seq: Long,
    val data: ClipDto,
)
