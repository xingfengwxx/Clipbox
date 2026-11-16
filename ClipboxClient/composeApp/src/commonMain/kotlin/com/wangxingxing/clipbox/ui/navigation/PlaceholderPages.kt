/**
 * author : 王星星
 * date : 2026-11-16 16:29
 * email : 1099420259@qq.com
 * description : 三个一级页面的占位内容；只验证导航与主题，不承载任何数据，后续卡片会逐页替换
 */
package com.wangxingxing.clipbox.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.wangxingxing.clipbox.resources.Res
import com.wangxingxing.clipbox.resources.placeholder_under_construction
import com.wangxingxing.clipbox.ui.theme.ClipboxTokens
import org.jetbrains.compose.resources.stringResource

@Composable
fun PlaceholderPage(destination: TopLevelDestination) {
    val colors = ClipboxTokens.colors
    val spacing = ClipboxTokens.spacing
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(spacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(destination.label),
            style = MaterialTheme.typography.titleLarge,
            color = colors.ink,
        )
        Spacer(Modifier.height(spacing.s))
        Text(
            text = stringResource(Res.string.placeholder_under_construction),
            style = MaterialTheme.typography.bodySmall,
            color = colors.ink3,
        )
    }
}
