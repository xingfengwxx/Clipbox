/**
 * author : 王星星
 * date : 2026-11-17 10:29
 * email : 1099420259@qq.com
 * description : 单实例内存限流：固定窗口计数、IP/设备分项额度、WebSocket 连接尝试准入，超限统一 429 或 4429。
 *               注意：IP 只取 TCP 连接来源（origin.remoteHost），绝不读取 X-Forwarded-For；
 *                     限流状态不持久化、重启清零，符合 api.md §8 的单实例约定。
 */
package com.wangxingxing.clipbox.server.plugins

import com.wangxingxing.clipbox.server.ws.ConnectionAttemptGate
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.plugins.origin
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** 限流额度；测试可注入更宽松或更严格的值，避免影响并发用例。 */
data class RateLimitSettings(
    val pairPerMinute: Int = 5,
    val adminPerMinute: Int = 10,
    val devicePerMinute: Int = 120,
    val sharePagePerMinute: Int = 60,
    val healthPerMinute: Int = 120,
    val webSocketAttemptsPerMinute: Int = 10,
    val windowMillis: Long = 60_000,
)

sealed interface RateLimitDecision {
    data object Allowed : RateLimitDecision

    data class Denied(val retryAfterSeconds: Long) : RateLimitDecision
}

/**
 * 固定窗口限流器。窗口内计数达到上限后拒绝，并给出距离窗口结束的秒数（向上取整，至少 1 秒）。
 * 并发下允许极端情况下的轻微少计，但不允许超发：每个 key 的窗口对象在 compute 内原子创建。
 */
class FixedWindowRateLimiter(
    private val limit: Int,
    private val windowMillis: Long,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private class Window(val startedAt: Long) {
        val count = AtomicInteger(0)
    }

    private val windows = ConcurrentHashMap<String, Window>()

    fun acquire(key: String): RateLimitDecision {
        val current = now()
        val window = windows.compute(key) { _, existing ->
            if (existing == null || current - existing.startedAt >= windowMillis) Window(current) else existing
        }!!
        val used = window.count.incrementAndGet()
        if (used <= limit) return RateLimitDecision.Allowed

        val remainingMillis = window.startedAt + windowMillis - current
        val retryAfterSeconds = ((remainingMillis + 999) / 1000).coerceAtLeast(1)
        pruneIfNeeded(current)
        return RateLimitDecision.Denied(retryAfterSeconds)
    }

    // 只为防止大量一次性 IP 让 map 无限增长；冷 key 在窗口两倍时长后清理。
    private fun pruneIfNeeded(current: Long) {
        if (windows.size < PRUNE_THRESHOLD) return
        windows.entries.removeIf { current - it.value.startedAt >= windowMillis * 2 }
    }

    private companion object {
        const val PRUNE_THRESHOLD = 1024
    }
}

/** 六类额度各用一个独立实例，key 之间互不影响。 */
class RateLimiters(
    settings: RateLimitSettings = RateLimitSettings(),
    now: () -> Long = System::currentTimeMillis,
) {
    val pair = FixedWindowRateLimiter(settings.pairPerMinute, settings.windowMillis, now)
    val admin = FixedWindowRateLimiter(settings.adminPerMinute, settings.windowMillis, now)
    val device = FixedWindowRateLimiter(settings.devicePerMinute, settings.windowMillis, now)
    val sharePage = FixedWindowRateLimiter(settings.sharePagePerMinute, settings.windowMillis, now)
    val health = FixedWindowRateLimiter(settings.healthPerMinute, settings.windowMillis, now)
    val webSocketAttempts =
        FixedWindowRateLimiter(settings.webSocketAttemptsPerMinute, settings.windowMillis, now)
}

/** IP 维度的公开接口限流；设备维度在认证插件内计数，WebSocket 维度由连接准入 gate 计数。 */
fun Application.configureRateLimits(limiters: RateLimiters) {
    intercept(ApplicationCallPipeline.Call) {
        val limiter = limiterFor(call.request.httpMethod, call.request.path(), limiters)
        if (limiter == null) {
            proceed()
            return@intercept
        }

        when (val decision = limiter.acquire(call.request.origin.remoteHost)) {
            RateLimitDecision.Allowed -> proceed()
            is RateLimitDecision.Denied -> call.respondRateLimited(decision.retryAfterSeconds)
        }
    }
}

/** WebSocket 连接建立前的按设备计数；拒绝时复用 SyncRoutes 的 4429 关闭分支。 */
class RateLimitedConnectionAttemptGate(
    private val limiter: FixedWindowRateLimiter,
) : ConnectionAttemptGate {
    override suspend fun allow(deviceId: String): Boolean =
        limiter.acquire(deviceId) is RateLimitDecision.Allowed
}

private fun limiterFor(
    method: HttpMethod,
    path: String,
    limiters: RateLimiters,
): FixedWindowRateLimiter? = when {
    method == HttpMethod.Post && path == "/api/v1/pair" -> limiters.pair
    method == HttpMethod.Post && path == "/api/v1/admin/pairing-codes" -> limiters.admin
    path == "/healthz" -> limiters.health
    path.startsWith("/s/") && path.length > 3 -> limiters.sharePage
    else -> null
}
