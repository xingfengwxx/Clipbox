/**
 * author : 王星星
 * date : 2026-11-16 11:17
 * email : 1099420259@qq.com
 * description : 三端共享根 Composable：观察应用容器启动状态与会话版本，就绪后按版本重建导航；未配对由历史空态进入配对页，解绑后直接落在配对页
 */
package com.wangxingxing.clipbox

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.navigation.compose.rememberNavController
import com.wangxingxing.clipbox.app.AppContainer
import com.wangxingxing.clipbox.app.AppStartupState
import com.wangxingxing.clipbox.resources.Res
import com.wangxingxing.clipbox.resources.startup_failed_body
import com.wangxingxing.clipbox.resources.startup_failed_title
import com.wangxingxing.clipbox.resources.startup_loading
import com.wangxingxing.clipbox.resources.startup_retry
import com.wangxingxing.clipbox.ui.i18n.ProvideAppLanguage
import com.wangxingxing.clipbox.ui.components.UpdateDialog
import com.wangxingxing.clipbox.ui.navigation.ClipboxNavigationHost
import com.wangxingxing.clipbox.ui.navigation.PAIRING_ROUTE
import com.wangxingxing.clipbox.ui.navigation.TopLevelDestination
import com.wangxingxing.clipbox.ui.theme.ClipboxTheme
import com.wangxingxing.clipbox.ui.theme.ClipboxTokens
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun App(
    incomingShareDraft: String? = null,
    onShareDraftConsumed: () -> Unit = {},
) {
    val container = koinInject<AppContainer>()
    val startupState by container.startupState.collectAsState()
    val generation by container.generation.collectAsState()
    val themeMode by container.themeMode.collectAsState()
    val language by container.language.collectAsState()

    ClipboxTheme(themeMode = themeMode) {
        ProvideAppLanguage(language) {
            when (startupState) {
                AppStartupState.Loading -> StartupLoading()
                is AppStartupState.Failed -> StartupFailure(onRetry = container::startAsync)
                AppStartupState.Ready -> {
                    // 会话版本变化意味着数据库与 ViewModel 已整体替换，导航必须重建，旧页面不能继续持有旧会话
                    val startAtPairing = remember(generation) { container.consumeStartAtPairing() }
                    // 根组合只收集弹层可见性；下载进度在弹窗内部订阅，避免每帧重组导航
                    val dialogVisible by container.updateController.dialogVisible.collectAsState()
                    Box(Modifier.fillMaxSize()) {
                        key(generation) {
                            ClipboxNavigationHost(
                                navController = rememberNavController(),
                                startDestination = if (startAtPairing) {
                                    PAIRING_ROUTE
                                } else {
                                    TopLevelDestination.History.route
                                },
                                incomingShareDraft = incomingShareDraft,
                                onShareDraftConsumed = onShareDraftConsumed,
                            )
                        }
                        if (dialogVisible) {
                            UpdateDialog(
                                controller = container.updateController,
                                currentVersion = container.versionName,
                                isIos = container.isIos,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StartupLoading() {
    val colors = ClipboxTokens.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ClipboxTokens.spacing.m),
        ) {
            CircularProgressIndicator(color = colors.accent)
            Text(
                text = stringResource(Res.string.startup_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.ink2,
            )
        }
    }
}

@Composable
private fun StartupFailure(onRetry: () -> Unit) {
    val colors = ClipboxTokens.colors
    val spacing = ClipboxTokens.spacing
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(spacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.m),
        ) {
            Text(
                text = stringResource(Res.string.startup_failed_title),
                style = MaterialTheme.typography.titleMedium,
                color = colors.ink,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(Res.string.startup_failed_body),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.ink2,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.onAccent,
                ),
            ) {
                Text(text = stringResource(Res.string.startup_retry))
            }
        }
    }
}
