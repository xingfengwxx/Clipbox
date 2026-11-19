/**
 * author : 王星星
 * date : 2026-11-19 11:04
 * email : 1099420259@qq.com
 * description : 公开更新描述路由：GET /api/v1/app/latest，无需认证，清单非法时统一 503。
 *               注意：不读取 Authorization、不接触配对会话；响应由 ContentNegotiation 序列化，null 平台字段不会输出。
 */
package com.wangxingxing.clipbox.server.routes

import com.wangxingxing.clipbox.server.plugins.respondError
import com.wangxingxing.clipbox.server.service.UpdateManifestService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun Route.updateRoutes(manifestService: UpdateManifestService) {
    get("/api/v1/app/latest") {
        // 清单读取是阻塞 IO，放到 IO 调度器，避免占用事件循环线程。
        val descriptor = withContext(Dispatchers.IO) { manifestService.latest() }
        if (descriptor == null) {
            call.respondError(
                HttpStatusCode.ServiceUnavailable,
                "SERVICE_UNAVAILABLE",
                "update manifest unavailable",
            )
        } else {
            call.respond(descriptor)
        }
    }
}
