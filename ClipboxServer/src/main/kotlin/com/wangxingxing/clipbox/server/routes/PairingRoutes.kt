/**
 * author : 王星星
 * date : 2026-11-16 14:56
 * email : 1099420259@qq.com
 * description : 配对相关路由：空库初始化、用设备码换取令牌、已配对设备生成新码，错误体统一走 plugins.respondError。
 *               注意：配对码与令牌不写任何日志，也不进入 URL；请求体解析必须原样放行 413/415，
 *                     否则请求体限制会被这里的兜底 catch 吞成 400。
 */
package com.wangxingxing.clipbox.server.routes

import com.wangxingxing.clipbox.server.plugins.DEVICE_AUTH_PROVIDER
import com.wangxingxing.clipbox.server.plugins.DevicePrincipal
import com.wangxingxing.clipbox.server.plugins.respondError
import com.wangxingxing.clipbox.server.service.DevicePairingCodeService
import com.wangxingxing.clipbox.server.service.PairingFailure
import com.wangxingxing.clipbox.server.service.PairingService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.plugins.PayloadTooLargeException
import io.ktor.server.plugins.UnsupportedMediaTypeException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

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
data class BootstrapRequest(
    val deviceName: String = "",
)

@Serializable
data class PairingCodeResponse(
    val code: String,
    val expiresAt: Long,
)

fun Route.pairingRoutes(
    pairingService: PairingService,
    pairingCodeService: DevicePairingCodeService,
) {
    post("/api/v1/bootstrap") {
        val request = call.receivePairingBody<BootstrapRequest>("请求体缺少 deviceName") ?: return@post
        try {
            val device = pairingService.initialize(request.deviceName)
            call.respond(HttpStatusCode.Created, device.toResponse())
        } catch (e: PairingFailure) {
            call.respondError(e.status, e.code, e.message)
        }
    }

    post("/api/v1/pair") {
        val request = call.receivePairingBody<PairRequest>("请求体缺少 code 或 deviceName") ?: return@post
        try {
            val device = pairingService.pair(request.code, request.deviceName)
            call.respond(HttpStatusCode.Created, device.toResponse())
        } catch (e: PairingFailure) {
            call.respondError(e.status, e.code, e.message)
        }
    }

    authenticate(DEVICE_AUTH_PROVIDER) {
        post("/api/v1/devices/me/pairing-code") {
            val principal = call.principal<DevicePrincipal>()
            if (principal == null) {
                call.respondError(HttpStatusCode.Unauthorized, "UNAUTHORIZED", "设备令牌缺失或无效")
                return@post
            }
            val issued = pairingCodeService.create(principal.deviceId)
            call.respond(
                HttpStatusCode.Created,
                PairingCodeResponse(code = issued.code, expiresAt = issued.expiresAt),
            )
        }
    }
}

/**
 * 解析配对请求体；解析失败回统一 400。
 * 413/415 必须继续抛出，交给全局错误映射保持状态码。
 */
private suspend inline fun <reified T : Any> ApplicationCall.receivePairingBody(invalidMessage: String): T? =
    try {
        receive<T>()
    } catch (e: CancellationException) {
        throw e
    } catch (e: PayloadTooLargeException) {
        throw e
    } catch (e: UnsupportedMediaTypeException) {
        throw e
    } catch (e: Exception) {
        respondError(HttpStatusCode.BadRequest, "VALIDATION_ERROR", invalidMessage)
        null
    }

private fun com.wangxingxing.clipbox.server.service.IssuedDevice.toResponse() = PairResponse(
    deviceId = deviceId,
    token = token,
    deviceName = deviceName,
)
