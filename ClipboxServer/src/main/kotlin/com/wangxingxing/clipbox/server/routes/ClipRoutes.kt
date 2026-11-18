/**
 * author : 王星星
 * date : 2026-11-16 15:21
 * email : 1099420259@qq.com
 * description : 剪贴记录接口：GET 增量查询与 POST/PATCH/DELETE 写入的请求校验、认证身份注入与响应序列化。
 *               注意：响应字段名必须与 api.md §3.1–§3.4 完全一致；墓碑只能出现 id/serverSeq/deleted 三个字段，
 *                     因此用 JsonObject 逐字段构造；sourceDeviceId 只取认证结果，忽略请求体中的同名字段。
 */
package com.wangxingxing.clipbox.server.routes

import com.wangxingxing.clipbox.server.db.ClipRow
import com.wangxingxing.clipbox.server.plugins.DEVICE_AUTH_PROVIDER
import com.wangxingxing.clipbox.server.plugins.DevicePrincipal
import com.wangxingxing.clipbox.server.plugins.respondError
import com.wangxingxing.clipbox.server.service.ClipService
import com.wangxingxing.clipbox.server.service.ClipSyncPage
import com.wangxingxing.clipbox.server.service.ClipWriteFailure
import com.wangxingxing.clipbox.server.service.SyncItem
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.plugins.PayloadTooLargeException
import io.ktor.server.plugins.UnsupportedMediaTypeException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val DEFAULT_LIMIT = 200
private const val MIN_LIMIT = 1
private const val MAX_LIMIT = 500

@Serializable
private data class CreatedClipResponse(
    val id: String,
    val serverSeq: Long,
    val createdAt: Long,
    val pinned: Boolean,
    val deleted: Boolean,
)

@Serializable
private data class UpdatedClipResponse(
    val id: String,
    val pinned: Boolean,
    val serverSeq: Long,
    val deleted: Boolean,
)

@Serializable
private data class DeletedClipResponse(
    val id: String,
    val deleted: Boolean,
    val serverSeq: Long,
)

fun Route.clipRoutes(clipService: ClipService) {
    authenticate(DEVICE_AUTH_PROVIDER) {
        get("/api/v1/clips") {
            val parameters = call.request.queryParameters
            val sinceParam = parameters["since"]
            val limitParam = parameters["limit"]
            val fullParam = parameters["full"]
            val since = if (sinceParam == null) {
                0L
            } else {
                sinceParam.toLongOrNull()?.takeIf { value -> value >= 0 }
            }
            val limit = if (limitParam == null) {
                DEFAULT_LIMIT
            } else {
                limitParam.toIntOrNull()?.takeIf { value -> value in MIN_LIMIT..MAX_LIMIT }
            }
            // 只接受字面量 true/false，避免 1/TRUE 等写法被静默转换为布尔值
            val full = when (fullParam) {
                null -> false
                "true" -> true
                "false" -> false
                else -> null
            }

            if (since == null || limit == null || full == null) {
                call.respondError(
                    HttpStatusCode.BadRequest,
                    "VALIDATION_ERROR",
                    "since 必须是非负整数，limit 必须是 $MIN_LIMIT-$MAX_LIMIT 的整数，full 只能是 true 或 false",
                )
                return@get
            }

            call.respond(clipService.sync(since = since, limit = limit, full = full).toJsonObject())
        }

        post("/api/v1/clips") {
            val principal = call.principal<DevicePrincipal>()!!
            val request = call.receiveJsonOrNull<JsonObject>()
            val id = request?.stringField("id")
            val content = request?.stringField("content")
            val createdAt = request?.longField("createdAt")
            if (id == null || content == null || createdAt == null) {
                call.respondClipError(
                    HttpStatusCode.BadRequest,
                    "VALIDATION_ERROR",
                    "请求体必须包含字符串 id、字符串 content 与整数 createdAt",
                )
                return@post
            }

            try {
                val outcome = clipService.create(
                    id = id,
                    content = content,
                    createdAt = createdAt,
                    sourceDeviceId = principal.deviceId,
                )
                val status = if (outcome.created) HttpStatusCode.Created else HttpStatusCode.OK
                call.respond(status, outcome.clip.toCreatedResponse())
            } catch (e: ClipWriteFailure) {
                call.respondClipError(e.status, e.code, e.message)
            }
        }

        patch("/api/v1/clips/{id}") {
            val id = call.parameters["id"]!!
            val pinned = call.receiveJsonOrNull<JsonObject>()?.booleanField("pinned")
            if (pinned == null) {
                call.respondClipError(HttpStatusCode.BadRequest, "VALIDATION_ERROR", "pinned 必须是布尔值")
                return@patch
            }

            try {
                call.respond(clipService.setPinned(id, pinned).toUpdatedResponse())
            } catch (e: ClipWriteFailure) {
                call.respondClipError(e.status, e.code, e.message)
            }
        }

        delete("/api/v1/clips/{id}") {
            val id = call.parameters["id"]!!
            try {
                call.respond(clipService.delete(id).toDeletedResponse())
            } catch (e: ClipWriteFailure) {
                call.respondClipError(e.status, e.code, e.message)
            }
        }
    }
}

private suspend inline fun <reified T : Any> ApplicationCall.receiveJsonOrNull(): T? =
    try {
        receive<T>()
    } catch (e: CancellationException) {
        throw e
    } catch (e: PayloadTooLargeException) {
        // 分块请求超限在这里才暴露，必须原样抛出交给统一错误映射，不能降级成参数错误。
        throw e
    } catch (e: UnsupportedMediaTypeException) {
        throw e
    } catch (e: Exception) {
        // 缺字段与类型不符统一按参数错误处理。
        null
    }

private suspend fun ApplicationCall.respondClipError(
    status: HttpStatusCode,
    code: String,
    message: String,
) {
    respondError(status, code, message)
}

// 契约要求严格类型：带引号的 "true" / "1" 不当作布尔或数字，避免字符串被静默转成合法值。
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

private fun ClipRow.toCreatedResponse() = CreatedClipResponse(
    id = id,
    serverSeq = serverSeq,
    createdAt = createdAt,
    pinned = pinned,
    deleted = deletedAt != null,
)

private fun ClipRow.toUpdatedResponse() = UpdatedClipResponse(
    id = id,
    pinned = pinned,
    serverSeq = serverSeq,
    deleted = deletedAt != null,
)

private fun ClipRow.toDeletedResponse() = DeletedClipResponse(
    id = id,
    deleted = deletedAt != null,
    serverSeq = serverSeq,
)

private fun ClipSyncPage.toJsonObject() = buildJsonObject {
    put(
        "items",
        buildJsonArray {
            items.forEach { item ->
                add(
                    when (item) {
                        is SyncItem.Active -> buildJsonObject {
                            put("id", item.id)
                            put("content", item.content)
                            put("sourceDeviceId", item.sourceDeviceId?.let(::JsonPrimitive) ?: JsonNull)
                            put("sourceDeviceName", item.sourceDeviceName)
                            put("pinned", item.pinned)
                            put("createdAt", item.createdAt)
                            put("serverSeq", item.serverSeq)
                            put("deleted", false)
                        }

                        is SyncItem.Tombstone -> buildJsonObject {
                            put("id", item.id)
                            put("serverSeq", item.serverSeq)
                            put("deleted", true)
                        }
                    },
                )
            }
        },
    )
    put("nextSince", nextSince)
    put("hasMore", hasMore)
    put("resyncRequired", resyncRequired)
    put("minAvailableSeq", minAvailableSeq)
    put("serverMaxSeq", serverMaxSeq)
}
