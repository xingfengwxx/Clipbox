/**
 * author : 王星星
 * date : 2026-11-17 18:50
 * email : 1099420259@qq.com
 * description : Koin 装配：进程级单例提供设置、日志、配对与历史视口；网络、数据库与仓库按当前会话工厂解析。
 *               注意：会话在配对、改址、解绑时整体重建，页面必须通过 koinViewModel / koinInject 获取依赖，不能缓存旧会话实例
 */
package com.wangxingxing.clipbox.di

import com.wangxingxing.clipbox.app.AppContainer
import com.wangxingxing.clipbox.app.AppSession
import com.wangxingxing.clipbox.clipboard.ClipboardController
import com.wangxingxing.clipbox.logging.AppLogger
import com.wangxingxing.clipbox.platform.ClipboardService
import com.wangxingxing.clipbox.repository.ClipRepository
import com.wangxingxing.clipbox.repository.ShareRepository
import com.wangxingxing.clipbox.repository.SyncRepository
import com.wangxingxing.clipbox.settings.SettingsRepository
import com.wangxingxing.clipbox.sync.OutboxWorker
import com.wangxingxing.clipbox.sync.SyncCoordinator
import com.wangxingxing.clipbox.sync.SyncStatusHolder
import com.wangxingxing.clipbox.ui.history.HistoryViewportState
import com.wangxingxing.clipbox.ui.history.HistoryViewModel
import com.wangxingxing.clipbox.ui.pairing.PairingViewModel
import com.wangxingxing.clipbox.ui.settings.SettingsViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule: Module = module {
    single { AppContainer(get()) }
    single<AppLogger> { get<AppContainer>().appLogger }
    single<SettingsRepository> { get<AppContainer>().settingsRepository }
    single { get<AppContainer>().pairingRepository }
    single { HistoryViewportState() }

    factory<AppSession> { get<AppContainer>().requireSession() }
    factory { get<AppSession>().clipRepository }
    factory { get<AppSession>().shareRepository }
    factory { get<AppSession>().syncRepository }
    factory { get<AppSession>().outboxWorker }
    factory { get<AppSession>().syncCoordinator }
    factory { get<AppSession>().syncStatusHolder }
    factory { get<AppSession>().clipboardService }
    factory { get<AppSession>().clipboardController }

    viewModel { HistoryViewModel(get(), get(), get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get(), get(), get()) }
    viewModel {
        val container = get<AppContainer>()
        PairingViewModel(
            repository = container.pairingRepository,
            initialServerUrl = container.initialServerUrl(),
            defaultDeviceName = container.defaultDeviceName,
        )
    }
}
