/**
 * author : 王星星
 * date : 2026-11-16 15:07
 * email : 1099420259@qq.com
 * description : iOS 端 DataStore 工厂；文件写入 Application Support 目录，由系统备份策略管理
 */
package com.wangxingxing.clipbox.settings

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
fun createSettingsRepository(
    scope: CoroutineScope,
    diagnosticLoggingDefault: Boolean,
): SettingsRepository {
    val basePath = NSSearchPathForDirectoriesInDomains(
        NSApplicationSupportDirectory,
        NSUserDomainMask,
        true,
    ).firstOrNull() as? String ?: error("Application Support directory is unavailable")

    val directory = "$basePath/Theca"
    val fileManager = NSFileManager.defaultManager
    if (!fileManager.fileExistsAtPath(directory)) {
        fileManager.createDirectoryAtPath(
            path = directory,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
    }

    val filePath = "$directory/$SETTINGS_FILE_NAME"
    val repository = SettingsRepository(
        dataStore = createPreferencesDataStore(producePath = { filePath }, scope = scope),
        diagnosticLoggingDefault = diagnosticLoggingDefault,
    )
    scope.launch { repository.initialize() }
    return repository
}
