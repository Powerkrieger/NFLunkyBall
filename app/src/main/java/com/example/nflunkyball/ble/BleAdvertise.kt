package com.example.nflunkyball.ble

import android.annotation.SuppressLint
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.AdvertisingSet
import android.bluetooth.le.AdvertisingSetCallback
import android.bluetooth.le.AdvertisingSetParameters
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
    try {
        started.await()
        delay(durationMs)
    } finally {
        // Unconditional and in `finally`: if this coroutine gets cancelled (e.g. collectLatest
        // tearing down this chunk's burst to broadcast a newer tournament version) while still
        // inside await()/delay() above, a plain `if (didStart) stopAdvertising(...)` placed
        // after them would never run — cancellation throws right there and skips it. That
        // leaves the legacy advertisement running forever, silently consuming one of the
        // device's limited concurrent-advertisement slots. Do this every time regardless of
        // whether start actually succeeded — stopping an advertisement that never started is a
        // harmless no-op.
        stopAdvertising(callback)
    }
}

/**
 * Same contract as [burst] but over a Bluetooth 5 extended (non-legacy) advertising set, which
 * accepts payloads far past the legacy 31-byte ceiling. Caller is responsible for checking
 * [BleCapability.supportsExtendedAdvertising] first — this assumes the hardware/OS combo
 * actually supports it.
 */
@SuppressLint("MissingPermission")
suspend fun BluetoothLeAdvertiser.burstExtended(payload: ByteArray, durationMs: Long) {
    val parameters = AdvertisingSetParameters.Builder()
        .setLegacyMode(false)
        .setConnectable(false)
        .setInterval(AdvertisingSetParameters.INTERVAL_LOW)
        .build()
    val data = AdvertiseData.Builder()
        .setIncludeDeviceName(false)
        .setIncludeTxPowerLevel(false)
        .addManufacturerData(BleConstants.MANUFACTURER_ID, payload)
        .build()

    val started = CompletableDeferred<AdvertisingSet?>()
    val callback = object : AdvertisingSetCallback() {
        override fun onAdvertisingSetStarted(advertisingSet: AdvertisingSet?, txPower: Int, status: Int) {
            if (status == AdvertisingSetCallback.ADVERTISE_SUCCESS) {
                started.complete(advertisingSet)
            } else {
                Log.w(TAG, "Extended advertising failed to start (payload ${payload.size}B): status=$status")
                started.complete(null)
            }
        }
    }

    startAdvertisingSet(parameters, data, null, null, null, callback)
    try {
        started.await()
        delay(durationMs)
    } finally {
        // See the matching comment in burst() above — same leak, same fix.
        stopAdvertisingSet(callback)
    }
}
