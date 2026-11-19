/**
 * author : 王星星
 * date : 2026-11-16 15:07
 * email : 1099420259@qq.com
 * description : 设置唯一读写入口；键名与默认值严格对齐架构文档 §6.2，令牌只存本机 DataStore，设置变更通过 Flow 推送
 */
package com.wangxingxing.clipbox.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val KEY_SERVER_URL = stringPreferencesKey("serverUrl")
private val KEY_DEVICE_TOKEN = stringPreferencesKey("deviceToken")
private val KEY_DEVICE_ID = stringPreferencesKey("deviceId")
private val KEY_DEVICE_NAME = stringPreferencesKey("deviceName")
private val KEY_LAST_SERVER_URL = stringPreferencesKey("lastServerUrl")
private val KEY_AUTO_PASTE = booleanPreferencesKey("autoPaste")
private val KEY_AUTO_CLIPBOARD_SEND = booleanPreferencesKey("autoClipboardSend")
private val KEY_AUTO_UPDATE_CHECK = booleanPreferencesKey("autoUpdateCheck")
private val KEY_THEME_MODE = stringPreferencesKey("themeMode")
private val KEY_APP_LANGUAGE = stringPreferencesKey("appLanguage")
private val KEY_DIAGNOSTIC_LOGGING = booleanPreferencesKey("diagnosticLoggingEnabled")

class SettingsRepository internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val diagnosticLoggingDefault: Boolean,
) {

    val settings: Flow<AppSettings> = dataStore.data.map { it.toAppSettings() }

    /** 读取一次当前设置，供网络层等非 UI 调用方使用 */
    suspend fun current(): AppSettings = settings.first()

    /**
     * 首次启动写入固定默认值；已存在用户选择时不覆盖。
     * 诊断日志默认值由构建类型决定，必须通过构造参数传入，不能读取系统语言或运行时猜测。
     */
    suspend fun initialize() {
        dataStore.edit { prefs ->
            if (prefs[KEY_APP_LANGUAGE] == null) {
                prefs[KEY_APP_LANGUAGE] = DEFAULT_APP_LANGUAGE.tag
            }
            if (prefs[KEY_DIAGNOSTIC_LOGGING] == null) {
                prefs[KEY_DIAGNOSTIC_LOGGING] = diagnosticLoggingDefault
            }
        }
    }

    /** 校验失败时不落盘；成功时保存规范化后的地址，不设置 lastServerUrl */
    suspend fun setServerUrl(raw: String): ServerUrlValidation {
        return when (val validation = ServerUrlValidator.validate(raw)) {
            is ServerUrlValidation.Invalid -> validation
            is ServerUrlValidation.Valid -> {
                dataStore.edit { it[KEY_SERVER_URL] = validation.url }
                validation
            }
        }
    }

    /** 配对成功后一次性写入设备信息，保证令牌与设备身份同时可见 */
    suspend fun setPairingInfo(
        deviceToken: String,
        deviceId: String,
        deviceName: String,
        lastServerUrl: String,
    ) {
        dataStore.edit { prefs ->
            prefs[KEY_DEVICE_TOKEN] = deviceToken
            prefs[KEY_DEVICE_ID] = deviceId
            prefs[KEY_DEVICE_NAME] = deviceName
            prefs[KEY_LAST_SERVER_URL] = lastServerUrl
        }
    }

    /** 只清除配对信息，主题、语言、开关与 serverUrl 保留 */
    suspend fun clearPairingInfo() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_DEVICE_TOKEN)
            prefs.remove(KEY_DEVICE_ID)
            prefs.remove(KEY_LAST_SERVER_URL)
        }
    }

    suspend fun setAutoPaste(enabled: Boolean) {
        dataStore.edit { it[KEY_AUTO_PASTE] = enabled }
    }

    suspend fun setAutoClipboardSend(enabled: Boolean) {
        dataStore.edit { it[KEY_AUTO_CLIPBOARD_SEND] = enabled }
    }

    suspend fun setAutoUpdateCheck(enabled: Boolean) {
        dataStore.edit { it[KEY_AUTO_UPDATE_CHECK] = enabled }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[KEY_THEME_MODE] = mode.value }
    }

    suspend fun setAppLanguage(language: AppLanguage) {
        dataStore.edit { it[KEY_APP_LANGUAGE] = language.tag }
    }

    suspend fun setDiagnosticLoggingEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_DIAGNOSTIC_LOGGING] = enabled }
    }

    private fun Preferences.toAppSettings(): AppSettings = AppSettings(
        serverUrl = this[KEY_SERVER_URL],
        deviceToken = this[KEY_DEVICE_TOKEN],
        deviceId = this[KEY_DEVICE_ID],
        deviceName = this[KEY_DEVICE_NAME],
        lastServerUrl = this[KEY_LAST_SERVER_URL],
        autoPaste = this[KEY_AUTO_PASTE] ?: DEFAULT_AUTO_PASTE,
        autoClipboardSend = this[KEY_AUTO_CLIPBOARD_SEND] ?: DEFAULT_AUTO_CLIPBOARD_SEND,
        autoUpdateCheck = this[KEY_AUTO_UPDATE_CHECK] ?: DEFAULT_AUTO_UPDATE_CHECK,
        themeMode = ThemeMode.fromValue(this[KEY_THEME_MODE]),
        appLanguage = AppLanguage.fromValue(this[KEY_APP_LANGUAGE]),
        diagnosticLoggingEnabled = this[KEY_DIAGNOSTIC_LOGGING] ?: diagnosticLoggingDefault,
    )
}
