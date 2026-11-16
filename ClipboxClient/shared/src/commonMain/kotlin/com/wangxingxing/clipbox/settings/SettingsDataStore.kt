/**
 * author : 王星星
 * date : 2026-11-16 15:07
 * email : 1099420259@qq.com
 * description : 各平台共用 Preferences DataStore 创建逻辑，文件路径由平台源集提供并位于应用沙箱内
 */
package com.wangxingxing.clipbox.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import okio.Path.Companion.toPath

internal const val SETTINGS_FILE_NAME = "clipbox_settings.preferences_pb"

/**
 * 同一文件在进程内只允许存在一个 DataStore 实例，否则并发写入会互相破坏；
 * 实例化与生命周期由后续 Koin 装配统一保证单例。
 */
internal fun createPreferencesDataStore(
    producePath: () -> String,
    scope: CoroutineScope,
): DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath(
    scope = scope,
    produceFile = { producePath().toPath() },
)
