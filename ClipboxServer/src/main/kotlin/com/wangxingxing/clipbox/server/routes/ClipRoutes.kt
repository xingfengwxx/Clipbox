/**
 * author : 王星星
 * date : 2026-11-16 15:21
 * email : 1099420259@qq.com
 * description : 剪贴记录读接口：GET /api/v1/clips 的 since/limit 参数校验与响应序列化。
 *               注意：响应字段名必须与 api.md §3.1 完全一致；墓碑只能出现 id/serverSeq/deleted 三个字段，
 *                     因此用 JsonObject 逐字段构造，而不是依赖全局 JSON 配置省略 null。
 */
package com.wangxingxing.clipbox.server.routes

import com.wangxingxing.clipbox.server.plugins.DEVICE_AUTH_PROVIDER
import com.wangxingxing.clipbox.server.service.ClipService
import com.wangxingxing.clipbox.server.service.ClipSyncPage
import com.wangxingxing.clipbox.server.service.SyncItem
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val DEFAULT_LIMIT = 200
private const val MIN_LIMIT = 1
private const val MAX_LIMIT = 500

fun Route.clipRoutes(clipService: ClipService) {
    authenticate(DEVICE_AUTH_PROVIDER) {
        get("/api/v1/clips") {
            val parameters = call.request.queryParameters
            val sinceParam = parameters["since"]
            val limitParam = parameters["limit"]
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

            if (since == null || limit == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ApiErrorResponse(
                        ApiErrorBody(
                            code = "VALIDATION_ERROR",
                            message = "since 必须是非负整数，limit 必须是 $MIN_LIMIT-$MAX_LIMIT 的整数",
                        ),
                    ),
                )
                return@get
            }

            call.respond(clipService.sync(since = since, limit = limit).toJsonObject())
        }
    }
}

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
