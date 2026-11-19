/**
 * author : 王星星
 * date : 2026-11-16 11:17
 * email : 1099420259@qq.com
 * description : Desktop 入口：启动 Koin 与应用容器，创建 1000×700 主窗口并限制最小 640×480；窗口关闭时先关闭会话再退出进程。
 *               注意：创建界面前同步预载设置，保证首帧主题正确；close 带 2 秒上限，避免 WS 或数据库收尾异常拖住退出
 */
package com.wangxingxing.clipbox

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.wangxingxing.clipbox.app.AppContainer
import com.wangxingxing.clipbox.di.appModule
import com.wangxingxing.clipbox.di.desktopPlatformModule
import com.wangxingxing.clipbox.resources.Res
import com.wangxingxing.clipbox.resources.window_icon
import com.wangxingxing.clipbox.resources.window_title
import com.wangxingxing.clipbox.ui.i18n.ProvideAppLanguage
import java.awt.Dimension
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.core.context.startKoin

fun main() {
    val koin = startKoin { modules(desktopPlatformModule(), appModule) }.koin
    val container = koin.get<AppContainer>()
    // 设置只读几个键，阻塞时间可忽略；先定主题再开窗，固定深浅色启动时窗口不会先闪一帧系统外观
    runBlocking { container.preloadSettings() }

    application {
        // 窗口标题跟随应用内语言（简中/繁中「字匣」、英文 Theca），需先套用资源环境再取值，否则会按系统 Locale 解析
        val language by container.language.collectAsState()
        ProvideAppLanguage(language) {
            val title = stringResource(Res.string.window_title)
            Window(
                onCloseRequest = {
                    runBlocking { withTimeoutOrNull(2_000) { container.close() } }
                    exitApplication()
                },
                title = title,
                icon = painterResource(Res.drawable.window_icon),
                state = rememberWindowState(size = DpSize(1000.dp, 700.dp))
            ) {
                LaunchedEffect(Unit) {
                    container.start()
                }
                // 最小尺寸只能通过 AWT 窗口设置，Compose 的 rememberWindowState 不支持下限
                LaunchedEffect(Unit) {
                    window.minimumSize = Dimension(640, 480)
                }
                App()
            }
        }
    }
}
