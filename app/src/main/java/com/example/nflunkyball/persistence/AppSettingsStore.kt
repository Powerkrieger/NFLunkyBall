package com.example.nflunkyball.persistence

import android.content.Context

/** Plain (non-secret) on-device app preferences. Currently just the live-sync transport toggle:
 *  server-backed sync is the primary path (works over the internet, no proximity needed) and
 *  defaults on ([useBleSync] false); peer-to-peer BLE is the fallback for venues with no
 *  internet, opted into explicitly. */
class AppSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    fun useBleSync(): Boolean = prefs.getBoolean(KEY_USE_BLE_SYNC, false)

    fun setUseBleSync(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_USE_BLE_SYNC, enabled).apply()
    }

    private companion object {
        const val KEY_USE_BLE_SYNC = "use_ble_sync"
    }
}
