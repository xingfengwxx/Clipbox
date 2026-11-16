/**
 * author : 王星星
 * date : 2026-11-16 10:33
 * email : 1099420259@qq.com
 * description : 健康检查路由，供容器与 uptime 探测使用。
 *               注意：不认证、不返回配置或路径；后续接入数据库探测时保持同一响应结构。
 */
package com.wangxingxing.clipbox.server.routes

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable

private const val SERVER_VERSION = "1.0.0"

@Serializable
data class HealthResponse(
    val status: String,
    val version: String,
    val time: Long,
)

fun Route.healthRoutes() {
    // 只反映进程存活；契约不允许返回配置、路径或错误详情。
    get("/healthz") {
        call.respond(
            HealthResponse(
                status = "ok",
                version = SERVER_VERSION,
                time = System.currentTimeMillis(),
            ),
        )
    }
}
