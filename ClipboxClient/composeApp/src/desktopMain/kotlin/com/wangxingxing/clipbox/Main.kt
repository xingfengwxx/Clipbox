/**
 * author : 王星星
 * date : 2026-11-16 11:17
 * email : 1099420259@qq.com
 * description : Desktop 入口，创建 1000×700 主窗口并限制最小 640×480；加载品牌窗口图标与固定标题；窗口内只装载共享根 Composable
 */
package com.wangxingxing.clipbox

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.wangxingxing.clipbox.resources.Res
import com.wangxingxing.clipbox.resources.window_icon
import com.wangxingxing.clipbox.resources.window_title
import java.awt.Dimension
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

fun main() = application {
    // brand-spec 规定 Desktop 窗口标题固定为 Theca，三语资源同值
    val title = stringResource(Res.string.window_title)
    Window(
        onCloseRequest = ::exitApplication,
        title = title,
        icon = painterResource(Res.drawable.window_icon),
        state = rememberWindowState(size = DpSize(1000.dp, 700.dp))
    ) {
        // 最小尺寸只能通过 AWT 窗口设置，Compose 的 rememberWindowState 不支持下限
        LaunchedEffect(Unit) {
            window.minimumSize = Dimension(640, 480)
        }
        App()
    }
}
