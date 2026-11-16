/**
 * author : 王星星
 * date : 2026-11-16 11:17
 * email : 1099420259@qq.com
 * description : Android 宿主 Activity，只负责装载共享根 Composable；分享入口与明文网络配置在后续平台卡补充
 */
package com.wangxingxing.clipbox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            App()
        }
    }
}
