package com.wangxingxing.clipbox.server

import com.wangxingxing.clipbox.server.config.AppConfig
import com.wangxingxing.clipbox.server.config.ConfigException
import com.wangxingxing.clipbox.server.plugins.configureSerialization
import com.wangxingxing.clipbox.server.routes.healthRoutes
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import kotlin.system.exitProcess

fun main() {
    val config = try {
        AppConfig.fromEnv(System.getenv())
    } catch (e: ConfigException) {
        System.err.println("Clipbox 服务端启动失败：${e.message}")
        exitProcess(1)
    }

    embeddedServer(Netty, host = "0.0.0.0", port = config.port) {
        module(config)
    }.start(wait = true)
}

fun Application.module(config: AppConfig) {
    configureSerialization()
    routing {
        healthRoutes()
    }
}
