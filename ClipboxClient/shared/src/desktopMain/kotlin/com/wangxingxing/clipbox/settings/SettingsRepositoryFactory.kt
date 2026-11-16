/**
 * author : 王星星
 * date : 2026-11-16 15:07
 * email : 1099420259@qq.com
 * description : Desktop 端 DataStore 工厂；按系统约定选择用户数据目录，避免写入安装目录或工作目录
 */
package com.wangxingxing.clipbox.settings

import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

fun createSettingsRepository(
    scope: CoroutineScope,
    diagnosticLoggingDefault: Boolean,
): SettingsRepository {
    val directory = desktopSettingsDirectory()
    directory.mkdirs()
    val file = File(directory, SETTINGS_FILE_NAME)
    val repository = SettingsRepository(
        dataStore = createPreferencesDataStore(producePath = { file.absolutePath }, scope = scope),
        diagnosticLoggingDefault = diagnosticLoggingDefault,
    )
    scope.launch { repository.initialize() }
    return repository
}

private fun desktopSettingsDirectory(): File {
    val home = System.getProperty("user.home")
    val osName = System.getProperty("os.name").orEmpty().lowercase()
    return when {
        osName.contains("mac") -> File(home, "Library/Application Support/Theca")
        osName.contains("win") -> File(System.getenv("APPDATA") ?: home, "Theca")
        else -> File(System.getenv("XDG_DATA_HOME") ?: File(home, ".local/share").path, "theca")
    }
}
