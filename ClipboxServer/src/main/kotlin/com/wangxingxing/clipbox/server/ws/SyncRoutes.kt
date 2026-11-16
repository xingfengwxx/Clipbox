/**
 * author : 王星星
 * date : 2026-11-16 16:13
 * email : 1099420259@qq.com
 * description : WebSocket 同步路由 /api/v1/sync：首帧认证、单设备单连接替换、auth.ok 与心跳配置。
 *               注意：认证帧原文与令牌不得进入日志；认证前不注册连接，因此不会收到业务事件；
 *                     数据库访问只走 DatabaseFactory 事务方法，关闭回调中不做阻塞操作。
 */
package com.wangxingxing.clipbox.server.ws

import com.wangxingxing.clipbox.server.db.DatabaseFactory
import com.wangxingxing.clipbox.server.db.DeviceRepository
import com.wangxingxing.clipbox.server.db.MetaRepository
import com.wangxingxing.clipbox.server.util.Hashing
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.routing.Route
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 安装 WebSocket 插件；心跳由插件统一发送 ping，超时未回 pong 即断开。 */
fun Application.configureSyncWebSockets(settings: WebSocketSettings) {
    install(WebSockets) {
        pingPeriodMillis = settings.pingPeriodMillis
        timeoutMillis = settings.pongTimeoutMillis
    }
}

fun Route.syncRoutes(
    databaseFactory: DatabaseFactory,
    registry: ConnectionRegistry,
    settings: WebSocketSettings = WebSocketSettings(),
    attemptGate: ConnectionAttemptGate = ConnectionAttemptGate.ALLOW_ALL,
) {
    webSocket("/api/v1/sync") {
        val token = awaitAuthToken(settings.authTimeoutMillis) ?: return@webSocket

        // 只按令牌哈希查设备；令牌明文用完即弃，不进入日志或注册表。
        val device = databaseFactory.readTransaction {
            DeviceRepository.findByTokenHash(Hashing.sha256Hex(token))
        }
        if (device == null) {
            reject(SyncCloseCodes.UNAUTHORIZED, "UNAUTHORIZED", "invalid token")
            return@webSocket
        }
        if (!attemptGate.allow(device.id)) {
            reject(SyncCloseCodes.RATE_LIMITED, "RATE_LIMITED", "too many connection attempts")
            return@webSocket
        }
        if (registry.activeCount() >= settings.maxConnections) {
            close(CloseReason(SyncCloseCodes.INTERNAL_ERROR.toShort(), "too many connections"))
            return@webSocket
        }

        val client = SyncClient(device.id, this)
        // 先注册再关闭旧连接，保证同一设备在注册表中始终只有一条记录。
        registry.register(client)?.let { previous ->
            previous.session.close(CloseReason(SyncCloseCodes.REPLACED.toShort(), "replaced"))
        }

        val serverSeq = databaseFactory.readTransaction {
            MetaRepository.getLong(MetaRepository.SERVER_SEQ_KEY) ?: 0L
        }
        send(
            Frame.Text(
                buildJsonObject {
                    put("type", "auth.ok")
                    put("deviceId", device.id)
                    put("serverSeq", serverSeq)
                }.toString(),
            ),
        )

        try {
            // 认证后不再处理上行消息；持续消费以感知客户端断开，注销统一放在 finally。
            for (ignored in incoming) {
            }
        } finally {
            registry.unregister(client)
        }
    }
}

/** 在认证窗口内读取并解析首帧；超时关闭 4408，客户端提前断开则静默返回。 */
private suspend fun DefaultWebSocketServerSession.awaitAuthToken(timeoutMillis: Long): String? {
    val frame = try {
        withTimeout(timeoutMillis) { incoming.receive() }
    } catch (e: TimeoutCancellationException) {
        close(CloseReason(SyncCloseCodes.AUTH_TIMEOUT.toShort(), "auth timeout"))
        return null
    } catch (e: ClosedReceiveChannelException) {
        return null
    }

    val token = (frame as? Frame.Text)?.readText()?.let(::parseAuthToken)
    if (token == null) {
        reject(SyncCloseCodes.UNAUTHORIZED, "UNAUTHORIZED", "invalid auth frame")
    }
    return token
}

private fun parseAuthToken(text: String): String? {
    val json = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return null
    val type = (json["type"] as? JsonPrimitive)?.takeIf { it.isString }?.content
    if (type != "auth") return null
    return (json["token"] as? JsonPrimitive)
        ?.takeIf { it.isString }
        ?.content
        ?.takeIf { it.isNotBlank() }
}

private suspend fun DefaultWebSocketServerSession.reject(code: Int, errorCode: String, message: String) {
    send(
        Frame.Text(
            buildJsonObject {
                put("type", "error")
                put(
                    "error",
                    buildJsonObject {
                        put("code", errorCode)
                        put("message", message)
                    },
                )
            }.toString(),
        ),
    )
    close(CloseReason(code.toShort(), message))
}
