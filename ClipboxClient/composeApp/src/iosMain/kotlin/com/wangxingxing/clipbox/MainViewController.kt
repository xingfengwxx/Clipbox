/**
 * author : 王星星
 * date : 2026-11-16 11:17
 * email : 1099420259@qq.com
 * description : iOS 入口，把共享根 Composable 包装成 UIViewController，供 SwiftUI 壳承载；分享草稿由生命周期桥提供，发送成功后回调清除。
 *               注意：创建界面前同步预载设置以确定首帧主题，容器其余启动放在 Compose 副作用里，避免阻塞主线程
 */
package com.wangxingxing.clipbox

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.coroutines.runBlocking
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    runBlocking { IosAppBootstrap.container.preloadSettings() }
    return ComposeUIViewController {
        LaunchedEffect(Unit) {
            IosAppBootstrap.container.start()
        }
        val language by IosAppBootstrap.container.language.collectAsState()
        // 扩展运行在独立进程，主 App 的语言变化必须写入 App Group 才能让扩展文案同步切换
        LaunchedEffect(language) {
            IosAppLanguageStore.write(language.tag)
        }
        val shareDraft by IosAppLifecycle.incomingShareDraft.collectAsState()
        App(
            incomingShareDraft = shareDraft?.text,
            onShareDraftConsumed = IosAppLifecycle::consumeShareDraft,
        )
    }
}
