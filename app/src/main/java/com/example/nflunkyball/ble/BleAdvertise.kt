package com.example.nflunkyball.ble

import android.annotation.SuppressLint
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay

private const val TAG = "BleAdvertise"

private fun advertiseFailureReason(errorCode: Int): String = when (errorCode) {
    AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED -> "ALREADY_STARTED"
    AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE -> "DATA_TOO_LARGE"
    AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "FEATURE_UNSUPPORTED"
    AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR -> "INTERNAL_ERROR"
    AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "TOO_MANY_ADVERTISERS"
    else -> "UNKNOWN($errorCode)"
}

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

    val started = CompletableDeferred<Boolean>()
    val callback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            started.complete(true)
        }

        override fun onStartFailure(errorCode: Int) {
            Log.w(TAG, "Advertising failed to start (payload ${payload.size}B): ${advertiseFailureReason(errorCode)}")
            started.complete(false) // give up on this chunk/burst rather than blocking the cycle
        }
    }

    startAdvertising(settings, data, callback)
    val didStart = started.await()
    delay(durationMs)
    if (didStart) stopAdvertising(callback)
}
