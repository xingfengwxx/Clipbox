/**
 * author : 王星星
 * date : 2026-11-16 10:33
 * email : 1099420259@qq.com
 * description : 健康检查路由，供容器与 uptime 探测使用；数据库 SELECT 1 失败时返回 503。
 *               注意：不认证、不返回配置、路径或错误详情；响应结构固定为 status/version/time。
 */
package com.wangxingxing.clipbox.server.routes

import io.ktor.http.HttpStatusCode
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

fun Route.healthRoutes(isDatabaseHealthy: suspend () -> Boolean) {
    // 只反映进程存活与数据库可达；契约不允许返回配置、路径或错误详情。
    get("/healthz") {
        val healthy = isDatabaseHealthy()
        call.respond(
            if (healthy) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable,
            HealthResponse(
                status = if (healthy) "ok" else "error",
                version = SERVER_VERSION,
                time = System.currentTimeMillis(),
            ),
        )
    }
}
