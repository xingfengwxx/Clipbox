/**
 * author : 王星星
 * date : 2026-11-16 15:31
 * email : 1099420259@qq.com
 * description : iOS 平台 Ktor 引擎，使用 Darwin 接入 NSURLSession
 */
package com.wangxingxing.clipbox.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin

internal actual fun defaultHttpClientEngine(): HttpClientEngine = Darwin.create()
