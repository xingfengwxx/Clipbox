/**
 * author : 王星星
 * date : 2026-11-16 15:07
 * email : 1099420259@qq.com
 * description : 设置领域模型与默认值；诊断日志默认值依赖构建类型，由 SettingsRepository 构造参数传入，不在此硬编码
 */
package com.wangxingxing.clipbox.settings

enum class ThemeMode(val value: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    ;

    companion object {
        fun fromValue(value: String?): ThemeMode =
            entries.firstOrNull { it.value == value } ?: SYSTEM
    }
}

enum class AppLanguage(val tag: String) {
    ZH_HANS("zh-Hans"),
    ZH_TW("zh-TW"),
    EN("en"),
    ;

    companion object {
        fun fromValue(value: String?): AppLanguage =
            entries.firstOrNull { it.tag == value } ?: ZH_HANS
    }
}

data class AppSettings(
    val serverUrl: String? = null,
    val deviceToken: String? = null,
    val deviceId: String? = null,
    val deviceName: String? = null,
    val lastServerUrl: String? = null,
    val autoPaste: Boolean = DEFAULT_AUTO_PASTE,
    val autoClipboardSend: Boolean = DEFAULT_AUTO_CLIPBOARD_SEND,
    val themeMode: ThemeMode = DEFAULT_THEME_MODE,
    val appLanguage: AppLanguage = DEFAULT_APP_LANGUAGE,
    val diagnosticLoggingEnabled: Boolean,
)

const val DEFAULT_AUTO_PASTE = true
const val DEFAULT_AUTO_CLIPBOARD_SEND = false
val DEFAULT_THEME_MODE = ThemeMode.SYSTEM
val DEFAULT_APP_LANGUAGE = AppLanguage.ZH_HANS
