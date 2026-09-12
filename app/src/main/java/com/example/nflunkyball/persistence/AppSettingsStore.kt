package com.example.nflunkyball.persistence

import android.content.Context

/** Plain (non-secret) on-device app preferences: the live-sync transport toggle and the UI
 *  language. The transport toggle:
 *  server-backed sync is the primary path (works over the internet, no proximity needed) and
 *  defaults on ([useBleSync] false); peer-to-peer BLE is the fallback for venues with no
 *  internet, opted into explicitly. Interface so JVM tests can substitute an in-memory one. */
/** UI language. [SYSTEM] follows the device; a device language the app isn't translated into
 *  falls back to English (the default `values/` resources) as usual on Android. */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    ENGLISH("en"),
    GERMAN("de");

    companion object {
        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag == tag } ?: SYSTEM
    }
}

interface AppSettings {
    fun useBleSync(): Boolean
    fun setUseBleSync(enabled: Boolean)
    fun language(): AppLanguage
    fun setLanguage(language: AppLanguage)
}

/** [AppSettings] on SharedPreferences. */
class AppSettingsStore(context: Context) : AppSettings {
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    override fun useBleSync(): Boolean = prefs.getBoolean(KEY_USE_BLE_SYNC, false)

    override fun setUseBleSync(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_USE_BLE_SYNC, enabled).apply()
    }

    override fun language(): AppLanguage = AppLanguage.fromTag(prefs.getString(KEY_LANGUAGE, null))

    override fun setLanguage(language: AppLanguage) {
        prefs.edit().putString(KEY_LANGUAGE, language.tag).apply()
    }

    private companion object {
        const val KEY_USE_BLE_SYNC = "use_ble_sync"
        const val KEY_LANGUAGE = "language"
    }
}
