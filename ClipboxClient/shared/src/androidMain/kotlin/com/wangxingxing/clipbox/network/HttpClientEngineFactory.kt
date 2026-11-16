/**
 * author : 王星星
 * date : 2026-11-16 15:31
 * email : 1099420259@qq.com
 * description : Android 平台 Ktor 引擎，使用 OkHttp 复用系统网络栈
 */
package com.wangxingxing.clipbox.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp

internal actual fun defaultHttpClientEngine(): HttpClientEngine = OkHttp.create()
