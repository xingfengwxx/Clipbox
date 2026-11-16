/**
 * author : 王星星
 * date : 2026-11-16 16:13
 * email : 1099420259@qq.com
 * description : 已认证 WebSocket 连接注册表：单设备单连接，广播使用 trySend 非阻塞发送。
 *               注意：注册、注销与广播快照共用同一把锁，锁内不做序列化与网络发送；注销必须按实例身份比较，
 *                     否则旧连接关闭时会误删同设备的新连接。
 */
package com.wangxingxing.clipbox.server.ws

import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.Frame
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 一个已通过首帧认证的同步连接。 */
class SyncClient(
    val deviceId: String,
    val session: DefaultWebSocketServerSession,
)

class ConnectionRegistry {

    private val mutex = Mutex()
    private val clients = LinkedHashMap<String, SyncClient>()

    /** 注册新连接并返回被替换的旧连接（如有）；调用方负责按 4001 关闭旧连接。 */
    suspend fun register(client: SyncClient): SyncClient? = mutex.withLock {
        clients.put(client.deviceId, client)
    }

    /** 只有当前登记的就是该实例时才移除，防止旧连接关闭时误删同设备的新连接。 */
    suspend fun unregister(client: SyncClient) {
        mutex.withLock {
            if (clients[client.deviceId] === client) {
                clients.remove(client.deviceId)
            }
        }
    }

    suspend fun activeCount(): Int = mutex.withLock { clients.size }

    /**
     * 向所有已认证连接发送同一帧。trySend 不阻塞调用方，慢连接不会拖住写请求；
     * 通道已关闭说明连接失效，移除即可，丢失的事件由客户端 REST 补拉兜底。
     */
    suspend fun broadcast(text: String) {
        val snapshot = mutex.withLock { clients.values.toList() }
        val stale = snapshot.filter { client ->
            client.session.outgoing.trySend(Frame.Text(text)).isFailure
        }
        if (stale.isEmpty()) return
        mutex.withLock {
            stale.forEach { client ->
                if (clients[client.deviceId] === client) {
                    clients.remove(client.deviceId)
                }
            }
        }
    }
}
