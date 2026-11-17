/**
 * author : 王星星
 * date : 2026-11-17 19:00
 * email : 1099420259@qq.com
 * description : iOS 进程级装配入口：首次访问时启动 Koin 并取得应用容器。注意：Compose 根节点与宿主生命周期桥必须复用同一实例，否则会出现两条 WS 连接与两个数据库
 */
package com.wangxingxing.clipbox

import com.wangxingxing.clipbox.app.AppContainer
import com.wangxingxing.clipbox.di.appModule
import com.wangxingxing.clipbox.di.iosPlatformModule
import org.koin.core.context.startKoin

internal object IosAppBootstrap {
    val container: AppContainer by lazy {
        startKoin { modules(iosPlatformModule(), appModule) }.koin.get()
    }
}
