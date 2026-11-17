/**
 * author : 王星星
 * date : 2026-11-16 10:33
 * email : 1099420259@qq.com
 * description : 服务端入口与模块装配：读取并校验环境配置，按访问日志、安全头、限流、请求体限制、序列化、错误映射、认证、
 *               WebSocket、路由的顺序安装管线，最后启动 Ktor Netty。
 *               注意：配置校验、数据库打开与迁移必须先于服务器启动完成，任一项失败立即退出，不得进入半启动状态；
 *                     启动日志只含版本、监听地址、数据目录与迁移版本，不得输出管理令牌、设备令牌或正文。
 */
package com.wangxingxing.clipbox.server

import com.wangxingxing.clipbox.server.config.AppConfig
import com.wangxingxing.clipbox.server.config.ConfigException
import com.wangxingxing.clipbox.server.db.DatabaseFactory
import com.wangxingxing.clipbox.server.job.RetentionJob
import com.wangxingxing.clipbox.server.plugins.RateLimitSettings
import com.wangxingxing.clipbox.server.plugins.RateLimitedConnectionAttemptGate
import com.wangxingxing.clipbox.server.plugins.RateLimiters
import com.wangxingxing.clipbox.server.plugins.configureDeviceAuth
import com.wangxingxing.clipbox.server.plugins.configureErrorMapping
import com.wangxingxing.clipbox.server.plugins.configureRateLimits
import com.wangxingxing.clipbox.server.plugins.configureRequestLimits
import com.wangxingxing.clipbox.server.plugins.configureRequestLogging
import com.wangxingxing.clipbox.server.plugins.configureSecurityHeaders
import com.wangxingxing.clipbox.server.plugins.configureSerialization
import com.wangxingxing.clipbox.server.plugins.notFoundRoute
import com.wangxingxing.clipbox.server.routes.SERVER_VERSION
import com.wangxingxing.clipbox.server.routes.clipRoutes
import com.wangxingxing.clipbox.server.routes.healthRoutes
import com.wangxingxing.clipbox.server.routes.pairingRoutes
import com.wangxingxing.clipbox.server.routes.shareRoutes
import com.wangxingxing.clipbox.server.service.AdminService
import com.wangxingxing.clipbox.server.service.Broadcaster
import com.wangxingxing.clipbox.server.service.ClipService
import com.wangxingxing.clipbox.server.service.PairingService
import com.wangxingxing.clipbox.server.service.ShareService
import com.wangxingxing.clipbox.server.ws.ConnectionRegistry
import com.wangxingxing.clipbox.server.ws.WebSocketSettings
import com.wangxingxing.clipbox.server.ws.configureSyncWebSockets
import com.wangxingxing.clipbox.server.ws.syncRoutes
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import kotlin.system.exitProcess
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("com.wangxingxing.clipbox.server.Application")

fun main() {
    // 校验失败直接退出，避免服务以部分配置启动后产生难以排查的运行时错误。
    val config = try {
        AppConfig.fromEnv(System.getenv())
    } catch (e: ConfigException) {
        System.err.println("Clipbox 服务端启动失败：${e.message}")
        exitProcess(1)
    }

    try {
        embeddedServer(Netty, host = "0.0.0.0", port = config.port) {
            module(config, startRetentionJob = true)
        }.start(wait = true)
    } catch (e: Exception) {
        val rootCause = generateSequence<Throwable>(e) { it.cause }.last()
        System.err.println("Clipbox 服务端启动失败：${rootCause.message ?: e.message}")
        exitProcess(1)
    }
}

/**
 * 装配应用模块。数据库工厂、WebSocket 参数、连接注册表与限流额度可由测试注入；未注入时按配置打开数据库。
 * 迁移失败必须关闭连接池后继续抛出，避免残留连接与半初始化状态。
 * 管线顺序有依赖：日志最外层保证每请求都有记录；错误映射必须早于认证与路由才能兜住其异常；
 * 请求体流限制必须早于 ContentNegotiation 才能先看到原始字节。
 * 保留清理默认不启动：测试夹具依赖固定时间与旧墓碑，后台任务会按真实时钟破坏这些数据；
 * 生产入口 [main] 显式传 true，启动时立即清理一次并每 24 小时重复。
 */
fun Application.module(
    config: AppConfig,
    databaseFactory: DatabaseFactory? = null,
    webSocketSettings: WebSocketSettings = WebSocketSettings(),
    connectionRegistry: ConnectionRegistry = ConnectionRegistry(),
    rateLimitSettings: RateLimitSettings = RateLimitSettings(),
    startRetentionJob: Boolean = false,
) {
    val factory = databaseFactory ?: DatabaseFactory.open(config.dataDir)
    val schemaVersion = try {
        factory.migrate()
    } catch (e: Exception) {
        factory.close()
        throw e
    }
    monitor.subscribe(ApplicationStopped) { factory.close() }

    val adminService = AdminService(factory, config.adminToken)
    val pairingService = PairingService(factory)
    // 启动日志只含版本、监听地址、数据目录与迁移版本；ADMIN_TOKEN、设备令牌与正文一律不出现。
    logger.info(
        "Clipbox 服务端 {} 启动：listen=http://0.0.0.0:{} dataDir={} schemaVersion={}",
        SERVER_VERSION,
        config.port,
        config.dataDir.toAbsolutePath(),
        schemaVersion,
    )
    // 启动即补发自助配对码，保证容器日志里始终可用；生成失败直接终止启动，不进入无码可用的状态。
    runBlocking { adminService.ensureStartupCode() }?.let { code ->
        logger.info("[PAIRING-CODE] 启动自动生成配对码：{}（10 分钟内有效，一次性使用）", code)
    }

    val limiters = RateLimiters(rateLimitSettings)
    // 路由与保留清理共用同一个广播器，确保双方看到同一份连接注册表。
    val broadcaster = Broadcaster(connectionRegistry)

    configureRequestLogging()
    configureSecurityHeaders()
    configureRateLimits(limiters)
    configureRequestLimits()
    configureSerialization()
    configureErrorMapping()
    configureDeviceAuth(factory, deviceRateLimiter = limiters.device)
    configureSyncWebSockets(webSocketSettings)
    routing {
        healthRoutes(factory::isHealthy)
        pairingRoutes(adminService, pairingService)
        clipRoutes(ClipService(factory, broadcaster))
        shareRoutes(ShareService(factory, config.publicBaseUrl))
        syncRoutes(
            factory,
            connectionRegistry,
            webSocketSettings,
            RateLimitedConnectionAttemptGate(limiters.webSocketAttempts),
        )
        notFoundRoute()
    }

    if (startRetentionJob) {
        RetentionJob(factory, broadcaster).start(this)
    }
}
