/**
 * author : 王星星
 * date : 2026-11-16 11:17
 * email : 1099420259@qq.com
 * description : 三端共享的根 Composable，当前只承载骨架占位内容；主题、导航与页面由后续 UI 任务接入，占位文案必须来自 composeResources
 */
package com.wangxingxing.clipbox

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.wangxingxing.clipbox.resources.Res
import com.wangxingxing.clipbox.resources.app_placeholder
import org.jetbrains.compose.resources.stringResource

@Composable
fun App() {
    MaterialTheme {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(text = stringResource(Res.string.app_placeholder))
        }
    }
}
