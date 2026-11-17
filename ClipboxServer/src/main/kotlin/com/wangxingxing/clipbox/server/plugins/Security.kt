/**
 * author : 王星星
 * date : 2026-11-16 15:21
 * email : 1099420259@qq.com
 * description : 设备 Bearer 认证与全局安全响应头：解析 Authorization、SHA-256 查设备令牌哈希、按设备限流、暴露 DevicePrincipal。
 *               注意：认证失败只返回统一的 401 JSON，不区分令牌缺失、格式错误与不存在；设备超限返回 429 且不注入 principal；
 *                     令牌与哈希不得进入日志；未使用 Ktor bearer provider 的默认 challenge，因为其响应体固定为空，无法满足 api.md §1.5。
 */
package com.wangxingxing.clipbox.server.plugins

import com.wangxingxing.clipbox.server.db.DatabaseFactory
import com.wangxingxing.clipbox.server.db.DeviceRepository
import com.wangxingxing.clipbox.server.db.DeviceRow
import com.wangxingxing.clipbox.server.util.Hashing
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.http.auth.parseAuthorizationHeader
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.AuthenticationContext
import io.ktor.server.auth.AuthenticationFailedCause
import io.ktor.server.auth.AuthenticationProvider
import io.ktor.server.request.header
import io.ktor.server.response.header

const val DEVICE_AUTH_PROVIDER = "device"

/** 认证成功后挂到调用上下文上的设备身份，写接口据此写入 sourceDeviceId。 */
data class DevicePrincipal(
    val deviceId: String,
    val name: String,
)

/**
 * 安装设备认证插件。时钟可注入，便于测试 last_seen_at 的 5 分钟节流。
 * [deviceRateLimiter] 按设备 ID 限制 120 次/分钟；超限回 429，不注入 principal。
 */
fun Application.configureDeviceAuth(
    databaseFactory: DatabaseFactory,
    now: () -> Long = System::currentTimeMillis,
    deviceRateLimiter: FixedWindowRateLimiter? = null,
) {
    val provider = DeviceAuthenticationProvider(databaseFactory, now, deviceRateLimiter)
    install(Authentication) {
        register(provider)
    }
}

/** 全局安全响应头；分享页的 CSP 由分享页自行追加，这里不写 HSTS（学习版为明文 HTTP）。 */
fun Application.configureSecurityHeaders() {
    intercept(ApplicationCallPipeline.Call) {
        call.response.header("X-Content-Type-Options", "nosniff")
        call.response.header("Referrer-Policy", "no-referrer")
        call.response.header(HttpHeaders.CacheControl, "no-store")
        proceed()
    }
}

class DeviceAuthenticationProvider(
    private val databaseFactory: DatabaseFactory,
    private val now: () -> Long,
    private val deviceRateLimiter: FixedWindowRateLimiter? = null,
) : AuthenticationProvider(Config(DEVICE_AUTH_PROVIDER)) {

    private class Config(name: String) : AuthenticationProvider.Config(name)

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        val call = context.call
        val token = call.bearerToken()
        val device = token?.let { value ->
            databaseFactory.readTransaction {
                DeviceRepository.findByTokenHash(Hashing.sha256Hex(value))
            }
        }

        if (device == null) {
            val cause =
                if (token == null) AuthenticationFailedCause.NoCredentials
                else AuthenticationFailedCause.InvalidCredentials
            context.challenge(FAILURE_KEY, cause) { challenge, challengedCall ->
                challengedCall.respondUnauthorized()
                challenge.complete()
            }
            return
        }

        // 设备限额先于 last_seen_at 写入：被限流的请求不应产生任何数据库写入。
        val decision = deviceRateLimiter?.acquire(device.id)
        if (decision is RateLimitDecision.Denied) {
            context.challenge(FAILURE_KEY, AuthenticationFailedCause.InvalidCredentials) { challenge, challengedCall ->
                challengedCall.respondRateLimited(decision.retryAfterSeconds)
                challenge.complete()
            }
            return
        }

        touchLastSeenIfStale(device)
        context.principal(DevicePrincipal(deviceId = device.id, name = device.name))
    }

    // 每个请求都写 last_seen_at 会与同步写事务争抢单写者锁，因此按 5 分钟窗口节流。
    private suspend fun touchLastSeenIfStale(device: DeviceRow) {
        val current = now()
        val lastSeen = device.lastSeenAt
        if (lastSeen == null || current - lastSeen >= LAST_SEEN_INTERVAL_MS) {
            databaseFactory.writeTransaction {
                DeviceRepository.touchLastSeen(device.id, current)
            }
        }
    }

    private suspend fun ApplicationCall.respondUnauthorized() {
        respondError(HttpStatusCode.Unauthorized, "UNAUTHORIZED", "设备令牌缺失或无效")
    }

    private fun ApplicationCall.bearerToken(): String? {
        val header = request.header("Authorization") ?: return null
        // 格式非法的头按“无有效凭证”处理，不向客户端区分具体原因。
        val parsed = runCatching { parseAuthorizationHeader(header) }.getOrNull()
        val single = parsed as? HttpAuthHeader.Single ?: return null
        if (!single.authScheme.equals("Bearer", ignoreCase = true)) return null
        return single.blob.takeIf { it.isNotBlank() }
    }

    companion object {
        private const val LAST_SEEN_INTERVAL_MS = 5 * 60 * 1000L
        private val FAILURE_KEY = Any()
    }
}
