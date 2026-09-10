package com.example.nflunkyball.persistence

import android.content.Context

/** Plain (non-secret) on-device app preferences. Currently just the BLE live-sync toggle, which
 *  defaults to off: peer-to-peer BLE broadcasting/scanning is an optional path, not the primary
 *  one (that's moving to server-backed broadcasting), so it should never turn itself on. */
class AppSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    fun isBleEnabled(): Boolean = prefs.getBoolean(KEY_BLE_ENABLED, false)

    fun setBleEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BLE_ENABLED, enabled).apply()
    }

    private companion object {
        const val KEY_BLE_ENABLED = "ble_enabled"
    }
}
