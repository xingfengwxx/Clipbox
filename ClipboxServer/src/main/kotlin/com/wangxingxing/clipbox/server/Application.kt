/**
 * author : 王星星
 * date : 2026-11-16 10:33
 * email : 1099420259@qq.com
 * description : 服务端入口：读取并校验环境配置，装配数据库、序列化与路由后启动 Ktor Netty。
 *               注意：配置校验、数据库打开与迁移必须先于服务器启动完成，任一项失败立即退出，不得进入半启动状态。
 */
package com.wangxingxing.clipbox.server

import com.wangxingxing.clipbox.server.config.AppConfig
import com.wangxingxing.clipbox.server.config.ConfigException
import com.wangxingxing.clipbox.server.db.DatabaseFactory
import com.wangxingxing.clipbox.server.plugins.configureDeviceAuth
import com.wangxingxing.clipbox.server.plugins.configureSerialization
import com.wangxingxing.clipbox.server.routes.clipRoutes
import com.wangxingxing.clipbox.server.routes.healthRoutes
import com.wangxingxing.clipbox.server.routes.pairingRoutes
import com.wangxingxing.clipbox.server.service.AdminService
import com.wangxingxing.clipbox.server.service.Broadcaster
import com.wangxingxing.clipbox.server.service.ClipService
import com.wangxingxing.clipbox.server.service.PairingService
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
            module(config)
        }.start(wait = true)
    } catch (e: Exception) {
        val rootCause = generateSequence<Throwable>(e) { it.cause }.last()
        System.err.println("Clipbox 服务端启动失败：${rootCause.message ?: e.message}")
        exitProcess(1)
    }
}

/**
 * 装配应用模块。数据库工厂、WebSocket 参数与连接注册表可由测试注入；未注入时按配置打开数据库。
 * 迁移失败必须关闭连接池后继续抛出，避免残留连接与半初始化状态。
 */
fun Application.module(
    config: AppConfig,
    databaseFactory: DatabaseFactory? = null,
    webSocketSettings: WebSocketSettings = WebSocketSettings(),
    connectionRegistry: ConnectionRegistry = ConnectionRegistry(),
) {
    val factory = databaseFactory ?: DatabaseFactory.open(config.dataDir)
    try {
        factory.migrate()
    } catch (e: Exception) {
        factory.close()
        throw e
    }
    monitor.subscribe(ApplicationStopped) { factory.close() }

    val adminService = AdminService(factory, config.adminToken)
    val pairingService = PairingService(factory)
    // 启动即补发自助配对码，保证容器日志里始终可用；生成失败直接终止启动，不进入无码可用的状态。
    runBlocking { adminService.ensureStartupCode() }?.let { code ->
        logger.info("[PAIRING-CODE] 启动自动生成配对码：{}（10 分钟内有效，一次性使用）", code)
    }

    configureSerialization()
    configureDeviceAuth(factory)
    configureSyncWebSockets(webSocketSettings)
    routing {
        healthRoutes(factory::isHealthy)
        pairingRoutes(adminService, pairingService)
        clipRoutes(ClipService(factory, Broadcaster(connectionRegistry)))
        syncRoutes(factory, connectionRegistry, webSocketSettings)
    }
}
