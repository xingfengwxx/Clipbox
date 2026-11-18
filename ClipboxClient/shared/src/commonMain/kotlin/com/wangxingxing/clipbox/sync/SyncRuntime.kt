/**
 * author : 王星星
 * date : 2026-11-17 13:30
 * email : 1099420259@qq.com
 * description : 单个会话的同步运行时装配；数据库、ApiService、WS 客户端与一把同步锁只在本会话内共享。注意：服务器地址由调用方按当前设置传入，改址、配对与解绑都必须关闭旧运行时并新建
 */
package com.wangxingxing.clipbox.sync

import com.wangxingxing.clipbox.clipboard.ClipboardController
import com.wangxingxing.clipbox.db.ClipboxDatabase
import com.wangxingxing.clipbox.logging.AppLogger
import com.wangxingxing.clipbox.network.ApiService
import com.wangxingxing.clipbox.network.KtorSyncSocketTransport
import com.wangxingxing.clipbox.network.SyncSocket
import com.wangxingxing.clipbox.network.createApiHttpClient
import com.wangxingxing.clipbox.network.createSyncWebSocketClient
import com.wangxingxing.clipbox.platform.ClipboardService
import com.wangxingxing.clipbox.repository.ClipLookup
import com.wangxingxing.clipbox.repository.ClipRepository
import com.wangxingxing.clipbox.repository.ShareRepository
import com.wangxingxing.clipbox.repository.SyncNowRunner
import com.wangxingxing.clipbox.repository.SyncRepository
import com.wangxingxing.clipbox.settings.SettingsRepository
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex

class SyncRuntime internal constructor(
    val clipRepository: ClipRepository,
    val shareRepository: ShareRepository,
    val syncRepository: SyncRepository,
    val outboxWorker: OutboxWorker,
    val syncCoordinator: SyncCoordinator,
    val syncStatusHolder: SyncStatusHolder,
    val clipboardService: ClipboardService,
    val clipboardController: ClipboardController,
    val apiService: ApiService,
    private val apiClient: HttpClient,
    private val socketClient: HttpClient,
) {

    /** 解绑前先停 WS 与剪贴板监听；Outbox 与令牌观察随会话协程取消一起结束 */
    fun stopSync() {
        syncCoordinator.stop()
        clipboardController.stop()
    }

    /** 关闭网络客户端；调用方必须先取消会话协程，避免仍有请求在途 */
    fun close() {
        stopSync()
        apiClient.close()
        socketClient.close()
    }
}

/**
 * 平台工厂负责构建 Room（Android 需要 Context），其余对象在此统一装配。
 * scope 是单个会话的作用域；会话关闭时由调用方取消它。
 */
internal fun buildSyncRuntime(
    database: ClipboxDatabase,
    scope: CoroutineScope,
    settingsRepository: SettingsRepository,
    defaultServerUrl: String,
    clipboardService: ClipboardService,
    isDesktop: Boolean,
    appLogger: AppLogger,
): SyncRuntime {
    val syncMutex = Mutex()
    val statusHolder = SyncStatusHolder(database, scope)
    // 令牌每次请求时读取，重新配对后无需重建客户端
    val apiClient = createApiHttpClient(
        serverUrl = defaultServerUrl,
        tokenProvider = { settingsRepository.current().deviceToken },
    )
    val apiService = ApiService(apiClient, logger = appLogger)
    val syncEngine = SyncEngine(
        database = database,
        apiService = apiService,
        settingsRepository = settingsRepository,
        syncMutex = syncMutex,
        statusReporter = statusHolder,
    )
    val outboxWorker = OutboxWorker(
        database = database,
        apiService = apiService,
        settingsRepository = settingsRepository,
        syncMutex = syncMutex,
    )
    val clipRepository = ClipRepository(database, settingsRepository)
    val socketClient = createSyncWebSocketClient(defaultServerUrl)
    val socket = SyncSocket(
        transport = KtorSyncSocketTransport(socketClient),
        gateway = syncEngine,
        settingsRepository = settingsRepository,
        defaultServerUrl = defaultServerUrl,
        statusReporter = statusHolder,
        logger = appLogger,
    )
    val shareRepository = ShareRepository(
        apiService = apiService,
        clips = ClipLookup { clipRepository.findClip(it) },
    )
    val coordinator = SyncCoordinator(
        scope = scope,
        settingsRepository = settingsRepository,
        syncEngine = syncEngine,
        outboxWorker = outboxWorker,
        socket = socket,
        statusReporter = statusHolder,
    )
    val syncRepository = SyncRepository(
        metaDao = database.metaDao(),
        runner = SyncNowRunner { coordinator.syncNow() },
    )
    val clipboardController = ClipboardController(
        scope = scope,
        settingsRepository = settingsRepository,
        clipRepository = clipRepository,
        clipboardService = clipboardService,
        realtimeCreated = socket.realtimeCreated,
        isDesktop = isDesktop,
        wakeOutbox = outboxWorker::requestReplay,
    )
    clipboardController.start()
    return SyncRuntime(
        clipRepository = clipRepository,
        shareRepository = shareRepository,
        syncRepository = syncRepository,
        outboxWorker = outboxWorker,
        syncCoordinator = coordinator,
        syncStatusHolder = statusHolder,
        clipboardService = clipboardService,
        clipboardController = clipboardController,
        apiService = apiService,
        apiClient = apiClient,
        socketClient = socketClient,
    )
}
