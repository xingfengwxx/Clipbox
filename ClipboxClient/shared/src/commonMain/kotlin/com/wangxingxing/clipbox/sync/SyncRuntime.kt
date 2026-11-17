/**
 * author : 王星星
 * date : 2026-11-17 13:30
 * email : 1099420259@qq.com
 * description : 共享同步运行时装配；每次进程只构建一个数据库、一个 ApiService 与一把同步锁，供 SyncEngine / OutboxWorker / SyncSocket 共用。注意：服务器地址在构建期固定为 defaultServerUrl，M2 不支持运行期改址，改址需要重建整个运行时
 */
package com.wangxingxing.clipbox.sync

import com.wangxingxing.clipbox.db.ClipboxDatabase
import com.wangxingxing.clipbox.network.ApiService
import com.wangxingxing.clipbox.network.KtorSyncSocketTransport
import com.wangxingxing.clipbox.network.SyncSocket
import com.wangxingxing.clipbox.network.createApiHttpClient
import com.wangxingxing.clipbox.network.createSyncWebSocketClient
import com.wangxingxing.clipbox.repository.ClipLookup
import com.wangxingxing.clipbox.repository.ClipRepository
import com.wangxingxing.clipbox.repository.ShareRepository
import com.wangxingxing.clipbox.repository.SyncNowRunner
import com.wangxingxing.clipbox.repository.SyncRepository
import com.wangxingxing.clipbox.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex

class SyncRuntime internal constructor(
    val clipRepository: ClipRepository,
    val shareRepository: ShareRepository,
    val syncRepository: SyncRepository,
    val outboxWorker: OutboxWorker,
    val syncCoordinator: SyncCoordinator,
    val syncStatusHolder: SyncStatusHolder,
)

/**
 * 平台工厂负责构建 Room（Android 需要 Context），其余对象在此统一装配。
 * 由平台工厂在进程级单例中调用一次；scope 必须是长生命周期协程作用域。
 */
internal fun buildSyncRuntime(
    database: ClipboxDatabase,
    scope: CoroutineScope,
    settingsRepository: SettingsRepository,
    defaultServerUrl: String,
): SyncRuntime {
    val syncMutex = Mutex()
    val statusHolder = SyncStatusHolder(database, scope)
    // 令牌每次请求时读取，重新配对后无需重建客户端
    val apiService = ApiService(
        createApiHttpClient(
            serverUrl = defaultServerUrl,
            tokenProvider = { settingsRepository.current().deviceToken },
        )
    )
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
    // WS 客户端随进程存活，没有关闭入口；长连接断开由 SyncSocket 负责
    val socket = SyncSocket(
        transport = KtorSyncSocketTransport(createSyncWebSocketClient(defaultServerUrl)),
        gateway = syncEngine,
        settingsRepository = settingsRepository,
        defaultServerUrl = defaultServerUrl,
        statusReporter = statusHolder,
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
    return SyncRuntime(
        clipRepository = clipRepository,
        shareRepository = shareRepository,
        syncRepository = syncRepository,
        outboxWorker = outboxWorker,
        syncCoordinator = coordinator,
        syncStatusHolder = statusHolder,
    )
}
