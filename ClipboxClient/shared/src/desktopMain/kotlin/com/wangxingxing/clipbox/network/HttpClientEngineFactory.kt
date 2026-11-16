/**
 * author : 王星星
 * date : 2026-11-16 15:31
 * email : 1099420259@qq.com
 * description : Desktop 平台 Ktor 引擎，使用 CIO 覆盖 Windows / macOS / Linux
 */
package com.wangxingxing.clipbox.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO

internal actual fun defaultHttpClientEngine(): HttpClientEngine = CIO.create()
