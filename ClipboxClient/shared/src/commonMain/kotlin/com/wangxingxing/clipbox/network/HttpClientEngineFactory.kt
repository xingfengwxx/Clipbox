/**
 * author : 王星星
 * date : 2026-11-16 15:31
 * email : 1099420259@qq.com
 * description : 各平台 Ktor 引擎入口的共享声明；Android 用 OkHttp、Desktop 用 CIO、iOS 用 Darwin
 */
package com.wangxingxing.clipbox.network

import io.ktor.client.engine.HttpClientEngine

internal expect fun defaultHttpClientEngine(): HttpClientEngine
