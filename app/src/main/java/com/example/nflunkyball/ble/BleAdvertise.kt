package com.example.nflunkyball.ble

import android.annotation.SuppressLint
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay

/** Advertises [payload] for [durationMs], then stops. Assumes BLUETOOTH_ADVERTISE is granted. */
@SuppressLint("MissingPermission")
suspend fun BluetoothLeAdvertiser.burst(payload: ByteArray, durationMs: Long) {
    val settings = AdvertiseSettings.Builder()
        .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
        .setConnectable(false)
        .build()
    val data = AdvertiseData.Builder()
        .setIncludeDeviceName(false)
        .setIncludeTxPowerLevel(false)
        .addManufacturerData(BleConstants.MANUFACTURER_ID, payload)
        .build()

    val started = CompletableDeferred<Unit>()
    val callback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            started.complete(Unit)
        }

        override fun onStartFailure(errorCode: Int) {
            started.complete(Unit) // give up on this chunk/burst rather than blocking the cycle
        }
    }

    startAdvertising(settings, data, callback)
    started.await()
    delay(durationMs)
    stopAdvertising(callback)
}
