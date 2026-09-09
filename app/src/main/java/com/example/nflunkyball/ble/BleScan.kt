package com.example.nflunkyball.ble

import android.annotation.SuppressLint
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private const val TAG = "BleScan"

/**
 * Continuously scans for our Manufacturer Specific Data, emitting the raw payload bytes of
 * every matching advertisement seen. Assumes BLUETOOTH_SCAN (or ACCESS_FINE_LOCATION pre-API
 * 31) is granted. Cancel the collecting coroutine to stop scanning.
 */
@SuppressLint("MissingPermission")
fun BluetoothLeScanner.manufacturerDataFlow(): Flow<ByteArray> = callbackFlow {
    var receivedCount = 0
    val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val bytes = result.scanRecord?.getManufacturerSpecificData(BleConstants.MANUFACTURER_ID)
                ?: return
            receivedCount++
            if (receivedCount == 1 || receivedCount % 50 == 0) {
                Log.d(TAG, "Received matching advertisement #$receivedCount (${bytes.size}B)")
            }
            trySend(bytes)
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "BLE scan failed to start, error code $errorCode")
            close(IllegalStateException("BLE scan failed, error code $errorCode"))
        }
    }

    val settings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()
    startScan(null, settings, callback)

    awaitClose { stopScan(callback) }
}
