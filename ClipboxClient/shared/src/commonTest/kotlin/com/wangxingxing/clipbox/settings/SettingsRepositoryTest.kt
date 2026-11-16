/**
 * author : 王星星
 * date : 2026-11-16 15:07
 * email : 1099420259@qq.com
 * description : 设置仓库测试；用内存 DataStore 假实现验证默认值、首次写入、地址落盘、清空配对与 Flow 可观察性
 */
package com.wangxingxing.clipbox.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsRepositoryTest {

    private val appLanguageKey = stringPreferencesKey("appLanguage")
    private val themeModeKey = stringPreferencesKey("themeMode")
    private val diagnosticKey = booleanPreferencesKey("diagnosticLoggingEnabled")

    @Test
    fun emptyStoreUsesDocumentedDefaults() = runTest {
        val repository = SettingsRepository(FakePreferencesDataStore(), diagnosticLoggingDefault = false)

        val settings = repository.current()

        assertNull(settings.serverUrl)
        assertNull(settings.deviceToken)
        assertNull(settings.deviceId)
        assertNull(settings.deviceName)
        assertNull(settings.lastServerUrl)
        assertTrue(settings.autoPaste)
        assertFalse(settings.autoClipboardSend)
        assertEquals(ThemeMode.SYSTEM, settings.themeMode)
        assertEquals(AppLanguage.ZH_HANS, settings.appLanguage)
        assertFalse(settings.diagnosticLoggingEnabled)
    }

    @Test
    fun diagnosticDefaultFollowsConstructorFlag() = runTest {
        val debugRepository = SettingsRepository(FakePreferencesDataStore(), diagnosticLoggingDefault = true)
        assertTrue(debugRepository.current().diagnosticLoggingEnabled)
    }

    @Test
    fun initializeWritesLanguageAndDiagnosticDefaultOnlyOnce() = runTest {
        val store = FakePreferencesDataStore()
        val repository = SettingsRepository(store, diagnosticLoggingDefault = true)

        repository.initialize()

        val firstRun = store.current()
        assertEquals("zh-Hans", firstRun[appLanguageKey])
        assertEquals(true, firstRun[diagnosticKey])

        repository.setAppLanguage(AppLanguage.EN)
        repository.setDiagnosticLoggingEnabled(false)
        repository.initialize()

        val afterUserChoice = store.current()
        assertEquals("en", afterUserChoice[appLanguageKey])
        assertEquals(false, afterUserChoice[diagnosticKey])
    }

    @Test
    fun validServerUrlIsNormalizedAndObserved() = runTest {
        val repository = SettingsRepository(FakePreferencesDataStore(), diagnosticLoggingDefault = false)

        val result = repository.setServerUrl("http://203.0.113.10:8080/")

        val valid = result as ServerUrlValidation.Valid
        assertEquals("http://203.0.113.10:8080", valid.url)
        assertTrue(valid.insecure)
        assertEquals("http://203.0.113.10:8080", repository.current().serverUrl)
    }

    @Test
    fun invalidServerUrlDoesNotOverwriteStoredValue() = runTest {
        val repository = SettingsRepository(FakePreferencesDataStore(), diagnosticLoggingDefault = false)
        repository.setServerUrl("https://example.com")

        val result = repository.setServerUrl("203.0.113.10:8080")

        val invalid = result as ServerUrlValidation.Invalid
        assertEquals(ServerUrlError.MISSING_SCHEME, invalid.error)
        assertEquals("https://example.com", repository.current().serverUrl)
    }

    @Test
    fun clearPairingInfoKeepsAppearanceAndSwitches() = runTest {
        val repository = SettingsRepository(FakePreferencesDataStore(), diagnosticLoggingDefault = false)
        repository.setServerUrl("http://203.0.113.10:8080")
        repository.setPairingInfo(
            deviceToken = "device-token",
            deviceId = "device-id",
            deviceName = "Android · Pixel",
            lastServerUrl = "http://203.0.113.10:8080",
        )
        repository.setThemeMode(ThemeMode.DARK)
        repository.setAppLanguage(AppLanguage.ZH_TW)
        repository.setAutoPaste(false)
        repository.setAutoClipboardSend(true)
        repository.setDiagnosticLoggingEnabled(true)

        repository.clearPairingInfo()

        val settings = repository.current()
        assertNull(settings.deviceToken)
        assertNull(settings.deviceId)
        assertNull(settings.lastServerUrl)
        assertEquals("http://203.0.113.10:8080", settings.serverUrl)
        assertEquals(ThemeMode.DARK, settings.themeMode)
        assertEquals(AppLanguage.ZH_TW, settings.appLanguage)
        assertFalse(settings.autoPaste)
        assertTrue(settings.autoClipboardSend)
        assertTrue(settings.diagnosticLoggingEnabled)
    }

    @Test
    fun unknownStoredEnumValuesFallBackToDefaults() = runTest {
        val store = FakePreferencesDataStore()
        store.updateData { prefs ->
            prefs.toMutablePreferences().apply {
                this[themeModeKey] = "neon"
                this[appLanguageKey] = "fr"
            }
        }
        val repository = SettingsRepository(store, diagnosticLoggingDefault = false)

        val settings = repository.current()

        assertEquals(ThemeMode.SYSTEM, settings.themeMode)
        assertEquals(AppLanguage.ZH_HANS, settings.appLanguage)
    }
}

// 其他共享层测试复用该内存实现，避免为 Repository 测试重复搭建 DataStore
internal class FakePreferencesDataStore(
    initial: Preferences = emptyPreferences(),
) : DataStore<Preferences> {

    private val state = MutableStateFlow(initial)
    private val mutex = Mutex()

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        mutex.withLock {
            val updated = transform(state.value)
            state.value = updated
            updated
        }

    fun current(): Preferences = state.value
}
