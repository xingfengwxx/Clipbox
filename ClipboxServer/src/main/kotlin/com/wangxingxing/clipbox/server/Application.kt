/**
 * author : 王星星
 * date : 2026-11-16 10:33
 * email : 1099420259@qq.com
 * description : 服务端入口：读取并校验环境配置，装配序列化与路由后启动 Ktor Netty。
 *               注意：配置校验必须先于服务器启动完成，任一项非法立即退出，不得进入半启动状态。
 */
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
    // 校验失败直接退出，避免服务以部分配置启动后产生难以排查的运行时错误。
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

/**
 * 装配应用模块。配置由入口或测试显式传入，装配路径不读取环境变量，便于用固定配置运行集成测试。
 */
fun Application.module(config: AppConfig) {
    configureSerialization()
    routing {
        healthRoutes()
    }
}
