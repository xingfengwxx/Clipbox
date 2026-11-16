/**
 * author : 王星星
 * date : 2026-11-16 16:13
 * email : 1099420259@qq.com
 * description : 同步事件广播：把剪贴变更构造为 api.md §4.2 的 clip.created/updated/deleted 帧，并交给连接注册表发送。
 *               注意：只在写事务提交后由 ClipService 调用；序列化与发送失败只记异常类型，不得抛出影响写响应，
 *                     也不得把正文、令牌写入日志。
 */
package com.wangxingxing.clipbox.server.service

import com.wangxingxing.clipbox.server.db.ClipRow
import com.wangxingxing.clipbox.server.ws.ConnectionRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory

class Broadcaster(private val registry: ConnectionRegistry) {

    suspend fun clipCreated(clip: ClipRow, sourceDeviceName: String) {
        publish(EVENT_CREATED, clip.serverSeq, activeData(clip, sourceDeviceName))
    }

    suspend fun clipUpdated(clip: ClipRow, sourceDeviceName: String) {
        publish(EVENT_UPDATED, clip.serverSeq, activeData(clip, sourceDeviceName))
    }

    suspend fun clipDeleted(id: String, serverSeq: Long) {
        publish(
            EVENT_DELETED,
            serverSeq,
            buildJsonObject {
                put("id", id)
                put("serverSeq", serverSeq)
                put("deleted", true)
            },
        )
    }

    private suspend fun publish(type: String, seq: Long, data: JsonObject) {
        try {
            val line = buildJsonObject {
                put("type", type)
                put("seq", seq)
                put("data", data)
            }.toString()
            registry.broadcast(line)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 广播是实时通知而非同步依据；失败由客户端 REST 补拉兜底，写响应必须照常返回。
            logger.warn("WebSocket 广播失败：{}", e.javaClass.simpleName)
        }
    }

    private fun activeData(clip: ClipRow, sourceDeviceName: String) = buildJsonObject {
        put("id", clip.id)
        put("content", clip.content)
        put("sourceDeviceId", clip.sourceDeviceId)
        put("sourceDeviceName", sourceDeviceName)
        put("pinned", clip.pinned)
        put("createdAt", clip.createdAt)
        put("serverSeq", clip.serverSeq)
        put("deleted", false)
    }

    companion object {
        private const val EVENT_CREATED = "clip.created"
        private const val EVENT_UPDATED = "clip.updated"
        private const val EVENT_DELETED = "clip.deleted"
        private val logger = LoggerFactory.getLogger(Broadcaster::class.java)
    }
}
