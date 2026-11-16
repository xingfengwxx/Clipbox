/**
 * author : 王星星
 * date : 2026-11-16 16:13
 * email : 1099420259@qq.com
 * description : WebSocket 同步通道的运行时参数、关闭码与连接尝试准入。
 *               注意：认证超时 5 秒与心跳 30/15 秒来自 api.md §1.6、§4.1；连接尝试计数逻辑留给 T-S-07，本卡只保留 4429 分支。
 */
package com.wangxingxing.clipbox.server.ws

/** WebSocket 通道参数；测试可注入更短的认证超时，生产使用默认值。 */
data class WebSocketSettings(
    val authTimeoutMillis: Long = 5_000,
    val pingPeriodMillis: Long = 30_000,
    val pongTimeoutMillis: Long = 15_000,
    val maxConnections: Int = 100,
)

/** 关闭码语义见 api.md §4.4，客户端据此决定是否自动重连。 */
object SyncCloseCodes {
    const val REPLACED = 4001
    const val UNAUTHORIZED = 4401
    const val AUTH_TIMEOUT = 4408
    const val RATE_LIMITED = 4429
    const val INTERNAL_ERROR = 1011
}

/** 连接尝试准入（10 次/分钟/设备由 T-S-07 接入），本卡默认全部放行。 */
fun interface ConnectionAttemptGate {
    suspend fun allow(deviceId: String): Boolean

    companion object {
        val ALLOW_ALL = ConnectionAttemptGate { true }
    }
}
