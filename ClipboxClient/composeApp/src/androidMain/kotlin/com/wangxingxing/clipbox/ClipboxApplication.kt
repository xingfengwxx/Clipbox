/**
 * author : 王星星
 * date : 2026-11-17 18:55
 * email : 1099420259@qq.com
 * description : Android 进程入口：启动 Koin 并异步初始化应用容器，用 Activity 计数驱动同步前后台切换。
 *               注意：不在这里做业务判断；创建 Activity 前同步预载设置，保证首帧主题正确；前后台只影响 WS 重连，Outbox 队列由会话自行保留
 */
package com.wangxingxing.clipbox

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.wangxingxing.clipbox.app.AppContainer
import com.wangxingxing.clipbox.di.androidPlatformModule
import com.wangxingxing.clipbox.di.appModule
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin

class ClipboxApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val koin = startKoin { modules(androidPlatformModule(this@ClipboxApplication), appModule) }.koin
        val container = koin.get<AppContainer>()
        // 设置读取在进程启动时完成，Activity 首帧就能拿到主题；其余会话初始化仍走异步
        runBlocking { container.preloadSettings() }
        container.startAsync()
        registerActivityLifecycleCallbacks(ContainerLifecycle(container))
    }

    // 只统计已 start 的 Activity：切换应用、锁屏与对话框都会走到 onStop，避免把后台误判为前台
    private class ContainerLifecycle(private val container: AppContainer) :
        ActivityLifecycleCallbacks {

        private var startedCount = 0

        override fun onActivityStarted(activity: Activity) {
            if (startedCount == 0) container.onForeground()
            startedCount++
        }

        override fun onActivityStopped(activity: Activity) {
            startedCount--
            if (startedCount == 0) container.onBackground()
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }
}
