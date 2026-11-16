/**
 * author : 王星星
 * date : 2026-11-16 10:33
 * email : 1099420259@qq.com
 * description : JSON 内容协商配置，统一服务端进出的 JSON 序列化口径。
 */
package com.wangxingxing.clipbox.server.plugins

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import kotlinx.serialization.json.Json

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        // 接口契约要求双方忽略未知字段，保证 v1 追加新字段时旧端不会解析失败。
        json(Json { ignoreUnknownKeys = true })
    }
}
