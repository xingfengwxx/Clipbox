/**
 * author : 王星星
 * date : 2026-11-16 15:07
 * email : 1099420259@qq.com
 * description : Android 端 DataStore 工厂；文件位于应用私有 filesDir，不申请任何存储权限
 */
package com.wangxingxing.clipbox.settings

import android.content.Context
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

fun createSettingsRepository(
    context: Context,
    scope: CoroutineScope,
    diagnosticLoggingDefault: Boolean,
): SettingsRepository {
    val file = File(context.filesDir, SETTINGS_FILE_NAME)
    val repository = SettingsRepository(
        dataStore = createPreferencesDataStore(producePath = { file.absolutePath }, scope = scope),
        diagnosticLoggingDefault = diagnosticLoggingDefault,
    )
    scope.launch { repository.initialize() }
    return repository
}
