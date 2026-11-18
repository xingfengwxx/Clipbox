/**
 * author : 王星星
 * date : 2026-11-16 14:01
 * email : 1099420259@qq.com
 * description : REST 接口 DTO，字段名与 api.md §2、§3、§5 逐字一致；墓碑记录只包含 id/serverSeq/deleted，其余字段为 null
 */
package com.wangxingxing.clipbox.model

import kotlinx.serialization.Serializable

@Serializable
data class PairRequest(
    val code: String,
    val deviceName: String,
)

@Serializable
data class PairResponse(
    val deviceId: String,
    val token: String,
    val deviceName: String,
)

@Serializable
data class BootstrapRequest(
    val deviceName: String,
)

@Serializable
data class PairingCodeResponse(
    val code: String,
    val expiresAt: Long,
)

@Serializable
data class ClipDto(
    val id: String,
    val content: String? = null,
    val sourceDeviceId: String? = null,
    val sourceDeviceName: String? = null,
    val pinned: Boolean? = null,
    val createdAt: Long? = null,
    val serverSeq: Long,
    val deleted: Boolean,
)

@Serializable
data class SyncResponse(
    val items: List<ClipDto>,
    val nextSince: Long,
    val hasMore: Boolean,
    val resyncRequired: Boolean,
    val minAvailableSeq: Long,
    val serverMaxSeq: Long,
)

@Serializable
data class CreateClipRequest(
    val id: String,
    val content: String,
    val createdAt: Long,
)

@Serializable
data class CreateClipResponse(
    val id: String,
    val serverSeq: Long,
    val createdAt: Long,
    val pinned: Boolean,
    val deleted: Boolean,
)

@Serializable
data class PatchPinnedRequest(
    val pinned: Boolean,
)

@Serializable
data class PatchPinnedResponse(
    val id: String,
    val pinned: Boolean,
    val serverSeq: Long,
    val deleted: Boolean,
)

@Serializable
data class DeleteClipResponse(
    val id: String,
    val deleted: Boolean,
    val serverSeq: Long,
)

@Serializable
data class CreateShareRequest(
    val clipId: String,
    val ttlSeconds: Long,
    val oneTime: Boolean,
)

@Serializable
data class CreateShareResponse(
    val token: String,
    val url: String,
    val expiresAt: Long,
)

@Serializable
data class ApiErrorEnvelope(
    val error: ApiErrorBody,
)

@Serializable
data class ApiErrorBody(
    val code: String,
    val message: String,
)
