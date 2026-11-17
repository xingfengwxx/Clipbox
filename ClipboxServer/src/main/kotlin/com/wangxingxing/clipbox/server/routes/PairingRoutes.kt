/**
 * author : 王星星
 * date : 2026-11-16 14:56
 * email : 1099420259@qq.com
 * description : 配对相关路由：管理端生成配对码与客户端用码换取设备令牌，错误体统一走 plugins.respondError。
 *               注意：配对码与令牌不写任何日志，也不进入 URL；请求体解析必须原样放行 413/415，
 *                     否则请求体限制会被这里的兜底 catch 吞成 400。
 */
package com.wangxingxing.clipbox.server.routes

import com.wangxingxing.clipbox.server.plugins.respondError
import com.wangxingxing.clipbox.server.service.AdminService
import com.wangxingxing.clipbox.server.service.PairingFailure
import com.wangxingxing.clipbox.server.service.PairingService
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.PayloadTooLargeException
import io.ktor.server.plugins.UnsupportedMediaTypeException
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

private const val ADMIN_TOKEN_HEADER = "X-Admin-Token"

@Serializable
data class PairRequest(
    val code: String = "",
    val deviceName: String = "",
)

@Serializable
data class PairResponse(
    val deviceId: String,
    val token: String,
    val deviceName: String,
)

@Serializable
data class AdminPairingCodeResponse(
    val code: String,
    val expiresAt: Long,
)

fun Route.pairingRoutes(adminService: AdminService, pairingService: PairingService) {
    post("/api/v1/admin/pairing-codes") {
        if (!adminService.verifyToken(call.request.header(ADMIN_TOKEN_HEADER))) {
            call.respondError(HttpStatusCode.Unauthorized, "UNAUTHORIZED", "管理令牌缺失或不匹配")
            return@post
        }
        val pairingCode = adminService.createPairingCode()
        call.respond(
            HttpStatusCode.Created,
            AdminPairingCodeResponse(code = pairingCode.code, expiresAt = pairingCode.expiresAt),
        )
    }

    post("/api/v1/pair") {
        val request = try {
            call.receive<PairRequest>()
        } catch (e: CancellationException) {
            throw e
        } catch (e: PayloadTooLargeException) {
            throw e
        } catch (e: UnsupportedMediaTypeException) {
            throw e
        } catch (e: Exception) {
            // 解析失败（缺字段、非 JSON）按参数错误处理；415/413 已在上方原样抛出，交给统一错误映射。
            call.respondError(HttpStatusCode.BadRequest, "VALIDATION_ERROR", "请求体缺少 code 或 deviceName")
            return@post
        }
        try {
            val device = pairingService.pair(request.code, request.deviceName)
            call.respond(
                HttpStatusCode.Created,
                PairResponse(
                    deviceId = device.deviceId,
                    token = device.token,
                    deviceName = device.deviceName,
                ),
            )
        } catch (e: PairingFailure) {
            call.respondError(e.status, e.code, e.message)
        }
    }
}
