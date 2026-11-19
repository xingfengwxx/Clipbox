/**
 * author : 王星星
 * date : 2026-11-17 18:40
 * email : 1099420259@qq.com
 * description : 应用容器：持有进程级环境（设置、主题、日志、配对入口）与可重建的会话（数据库、同步运行时）。
 *               注意：配对成功、服务器地址变化、解绑都必须关闭旧会话再新建；旧会话先取消协程，再关闭网络与数据库，避免残留连接与协程
 */
package com.wangxingxing.clipbox.app

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.wangxingxing.clipbox.clipboard.ClipboardController
import com.wangxingxing.clipbox.db.ClipboxDatabase
import com.wangxingxing.clipbox.logging.AppLogger
import com.wangxingxing.clipbox.logging.LogEvent
import com.wangxingxing.clipbox.logging.LogSink
import com.wangxingxing.clipbox.platform.ClipboardService
import com.wangxingxing.clipbox.repository.ClipRepository
import com.wangxingxing.clipbox.repository.PairingCodeRepository
import com.wangxingxing.clipbox.repository.PairingRepository
import com.wangxingxing.clipbox.repository.ShareRepository
import com.wangxingxing.clipbox.repository.SyncRepository
import com.wangxingxing.clipbox.settings.AppLanguage
import com.wangxingxing.clipbox.settings.AppSettings
import com.wangxingxing.clipbox.settings.SettingsRepository
import com.wangxingxing.clipbox.settings.ThemeMode
import com.wangxingxing.clipbox.sync.OutboxWorker
import com.wangxingxing.clipbox.sync.SyncCoordinator
import com.wangxingxing.clipbox.sync.SyncRuntime
import com.wangxingxing.clipbox.sync.SyncStatusHolder
import com.wangxingxing.clipbox.sync.buildSyncRuntime
import com.wangxingxing.clipbox.update.UpdateApi
import com.wangxingxing.clipbox.update.UpdateController
import com.wangxingxing.clipbox.update.UpdateInstaller
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile

/** 平台差异的唯一注入点；实现与进程同生命周期，不在容器关闭时销毁 */
interface PlatformServices {
    val appScope: CoroutineScope
    val clipboardService: ClipboardService
    val logSink: LogSink
    val defaultServerUrl: String
    val defaultDeviceName: String
    val isDesktop: Boolean
    /** iOS 只检查更新、不提供安装器；弹窗据此展示只读说明 */
    val isIos: Boolean get() = false
    val isDebugBuild: Boolean
    val versionName: String
    val buildType: String

    fun createDatabase(): ClipboxDatabase

    fun createSettingsRepository(): SettingsRepository

    /** 平台更新安装器；未实现的平台返回 null，更新弹窗按只读展示 */
    fun createUpdateInstaller(): UpdateInstaller? = null
}

sealed interface AppStartupState {
    data object Loading : AppStartupState
    data object Ready : AppStartupState
    data class Failed(val reason: String?) : AppStartupState
}

/** 一次配对周期内的运行时；解绑或改址后整体替换，旧实例不再被解析 */
class AppSession internal constructor(
    val database: ClipboxDatabase,
    val runtime: SyncRuntime,
    val paired: Boolean,
    internal val scope: CoroutineScope,
) {
    val clipRepository: ClipRepository get() = runtime.clipRepository
    val shareRepository: ShareRepository get() = runtime.shareRepository
    val syncRepository: SyncRepository get() = runtime.syncRepository
    val outboxWorker: OutboxWorker get() = runtime.outboxWorker
    val syncCoordinator: SyncCoordinator get() = runtime.syncCoordinator
    val syncStatusHolder: SyncStatusHolder get() = runtime.syncStatusHolder
    val clipboardService: ClipboardService get() = runtime.clipboardService
    val clipboardController: ClipboardController get() = runtime.clipboardController
    val pairingCodeRepository: PairingCodeRepository = PairingCodeRepository(runtime.apiService)

    internal suspend fun shutdown() {
        // 先等会话内所有协程退出，再关网络与数据库，避免关闭后仍有请求或查询在途
        scope.coroutineContext[Job]?.cancelAndJoin()
        runtime.close()
        database.close()
    }
}

class AppContainer(private val services: PlatformServices) {

    val appLogger = AppLogger(services.logSink, isDebugBuild = services.isDebugBuild)
    val settingsRepository: SettingsRepository = services.createSettingsRepository()
    val pairingRepository = PairingRepository(settingsRepository)

    // 更新能力与配对无关：只访问官方发行服务器，会话重建不替换本控制器
    val updateController = UpdateController(
        scope = services.appScope,
        currentVersion = services.versionName,
        installer = services.createUpdateInstaller(),
        createApi = { UpdateApi(services.defaultServerUrl) },
        autoCheckEnabled = { settingsRepository.current().autoUpdateCheck },
    )

    val defaultServerUrl: String get() = services.defaultServerUrl
    val defaultDeviceName: String get() = services.defaultDeviceName
    val isDesktop: Boolean get() = services.isDesktop
    val isIos: Boolean get() = services.isIos
    val isDebugBuild: Boolean get() = services.isDebugBuild
    val versionName: String get() = services.versionName
    val buildType: String get() = services.buildType

    private val _startupState = MutableStateFlow<AppStartupState>(AppStartupState.Loading)
    val startupState: StateFlow<AppStartupState> = _startupState.asStateFlow()

    // 会话版本号；UI 根节点用它重建导航与 ViewModel，避免旧页面持有已关闭的数据库
    private val _generation = MutableStateFlow(0)
    val generation: StateFlow<Int> = _generation.asStateFlow()

    // 主题与语言独立于会话：切换只改根组合，不触发导航或数据库重建
    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _language = MutableStateFlow(AppLanguage.ZH_HANS)
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    private val startLock = Mutex()
    private val sessionLock = Mutex()
    private val preloadLock = Mutex()

    @Volatile
    private var settingsPreloaded = false

    @Volatile
    private var settingsSnapshotJob: Job? = null

    @Volatile
    private var session: AppSession? = null

    @Volatile
    private var settingsSnapshot: AppSettings? = null

    @Volatile
    private var startAtPairing = false

    fun currentSession(): AppSession? = session

    fun requireSession(): AppSession = session ?: error("AppContainer session is not ready")

    /** 配对页预填地址：优先使用已保存的服务器地址，未配置时回落到构建期默认值 */
    fun initialServerUrl(): String = settingsSnapshot?.serverUrl ?: services.defaultServerUrl

    /** 只读取一次：解绑后的重建需要让导航直接落在配对页 */
    fun consumeStartAtPairing(): Boolean {
        val value = startAtPairing
        startAtPairing = false
        return value
    }

    /**
     * 首次组合前预载设置：主题与语言必须第一帧就按用户选择渲染，否则固定深浅色启动会先闪一帧系统外观。
     * 平台入口在创建界面前同步调用；与 start() 共用默认值写入，重复调用无副作用。
     */
    suspend fun preloadSettings() {
        preloadLock.withLock {
            if (settingsPreloaded) return
            settingsRepository.initialize()
            val settings = settingsRepository.current()
            settingsSnapshot = settings
            _themeMode.value = settings.themeMode
            _language.value = settings.appLanguage
            settingsPreloaded = true
        }
    }

    fun startAsync() {
        services.appScope.launch { start() }
    }

    suspend fun start() {
        startLock.withLock {
            if (_startupState.value is AppStartupState.Ready) return
            _startupState.value = AppStartupState.Loading
            try {
                preloadSettings()
                // 诊断开关只观察一次，跨会话保持；会话重建不重复收集
                appLogger.observe(settingsRepository, services.appScope)
                // 快照跟随 DataStore：未配对改址后配对页预填、外观摘要都读取最新值
                if (settingsSnapshotJob == null) {
                    settingsSnapshotJob = services.appScope.launch {
                        settingsRepository.settings.collect {
                            settingsSnapshot = it
                            _themeMode.value = it.themeMode
                            _language.value = it.appLanguage
                        }
                    }
                }
                val settings = settingsRepository.current()
                settingsSnapshot = settings
                _themeMode.value = settings.themeMode
                _language.value = settings.appLanguage
                session = buildSession(settings)
                _startupState.value = AppStartupState.Ready
                // 首帧之后再触发自动检查；协程内部静默失败，不阻塞启动
                updateController.onAppStart()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                appLogger.log(
                    LogEvent.Business(
                        operation = "startup",
                        errorCode = t::class.simpleName,
                    ),
                )
                _startupState.value = AppStartupState.Failed(t.message)
            }
        }
    }

    /** 配对成功或未配对时修改服务器地址后调用：保留本地数据，仅替换网络与数据库会话 */
    suspend fun refreshSession() {
        rebuildSession(clearLocalData = false, returnToPairing = false)
    }

    /** 解绑：停止同步 → 清空本机令牌、历史、Outbox 与游标 → 关闭旧会话 → 重建 → 导航回配对页 */
    suspend fun unpair() {
        rebuildSession(clearLocalData = true, returnToPairing = true)
    }

    private suspend fun rebuildSession(clearLocalData: Boolean, returnToPairing: Boolean) {
        sessionLock.withLock {
            val previous = session
            session = null
            previous?.runtime?.stopSync()
            if (clearLocalData) {
                // 同步已先停止，此刻不会有并发写入；三张表清空放在同一写事务里，避免半清状态被重建后的会话读到
                previous?.let { old ->
                    old.database.useWriterConnection { transactor ->
                        transactor.immediateTransaction {
                            old.database.clipDao().deleteAll()
                            old.database.outboxDao().deleteAll()
                            old.database.metaDao().clearAll()
                        }
                    }
                }
                settingsRepository.clearPairingInfo()
            }
            previous?.shutdown()

            val settings = settingsRepository.current()
            settingsSnapshot = settings
            _themeMode.value = settings.themeMode
            _language.value = settings.appLanguage
            startAtPairing = returnToPairing && settings.deviceToken.isNullOrEmpty()
            session = buildSession(settings)
            _generation.value += 1
        }
    }

    private fun buildSession(settings: AppSettings): AppSession {
        val sessionJob = SupervisorJob(services.appScope.coroutineContext[Job])
        val scope = CoroutineScope(services.appScope.coroutineContext + sessionJob)
        val database = services.createDatabase()
        return try {
            val runtime = buildSyncRuntime(
                database = database,
                scope = scope,
                settingsRepository = settingsRepository,
                defaultServerUrl = settings.serverUrl ?: services.defaultServerUrl,
                clipboardService = services.clipboardService,
                isDesktop = services.isDesktop,
                appLogger = appLogger,
            )
            // 未配对只启动本地功能：不建 WS、不跑 Outbox，配对成功后由重建的新会话启动
            val paired = !settings.deviceToken.isNullOrEmpty()
            if (paired) runtime.syncCoordinator.start()
            AppSession(
                database = database,
                runtime = runtime,
                paired = paired,
                scope = scope,
            )
        } catch (t: Throwable) {
            sessionJob.cancel()
            database.close()
            throw t
        }
    }

    fun onForeground() {
        session?.takeIf { it.paired }?.syncCoordinator?.onForeground()
    }

    fun onBackground() {
        session?.takeIf { it.paired }?.syncCoordinator?.onBackground()
    }

    suspend fun close() {
        val current = session
        session = null
        current?.shutdown()
        // 退出应用才取消更新下载并清理未完成文件；关闭弹窗不经过这里
        updateController.close()
    }
}
