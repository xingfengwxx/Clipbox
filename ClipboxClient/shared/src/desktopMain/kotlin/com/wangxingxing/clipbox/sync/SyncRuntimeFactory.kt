/**
 * author : 王星星
 * date : 2026-11-17 13:32
 * email : 1099420259@qq.com
 * description : Desktop 同步运行时工厂；构建进程唯一的 Room 数据库后交给共享装配，保证记录仓库与同步内核使用同一个数据库实例
 */
package com.wangxingxing.clipbox.sync

import com.wangxingxing.clipbox.db.buildClipboxDatabase
import com.wangxingxing.clipbox.db.createClipboxDatabaseBuilder
import com.wangxingxing.clipbox.platform.DesktopClipboardService
import com.wangxingxing.clipbox.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope

fun createSyncRuntime(
    scope: CoroutineScope,
    settingsRepository: SettingsRepository,
    defaultServerUrl: String,
): SyncRuntime = buildSyncRuntime(
    database = buildClipboxDatabase(createClipboxDatabaseBuilder()),
    scope = scope,
    settingsRepository = settingsRepository,
    defaultServerUrl = defaultServerUrl,
    clipboardService = DesktopClipboardService(),
    isDesktop = true,
)
