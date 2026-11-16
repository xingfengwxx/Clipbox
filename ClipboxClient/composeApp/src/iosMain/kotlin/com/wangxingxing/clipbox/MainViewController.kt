/**
 * author : 王星星
 * date : 2026-11-16 11:17
 * email : 1099420259@qq.com
 * description : iOS 入口，把共享根 Composable 包装成 UIViewController，供 SwiftUI 壳承载
 */
package com.wangxingxing.clipbox

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController = ComposeUIViewController { App() }
