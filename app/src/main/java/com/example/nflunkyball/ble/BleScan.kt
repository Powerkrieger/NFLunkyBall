package com.example.nflunkyball.ble

import android.annotation.SuppressLint
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Continuously scans for our Manufacturer Specific Data, emitting the raw payload bytes of
 * every matching advertisement seen. Assumes BLUETOOTH_SCAN (or ACCESS_FINE_LOCATION pre-API
 * 31) is granted. Cancel the collecting coroutine to stop scanning.
 */
@SuppressLint("MissingPermission")
fun BluetoothLeScanner.manufacturerDataFlow(): Flow<ByteArray> = callbackFlow {
    val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val bytes = result.scanRecord?.getManufacturerSpecificData(BleConstants.MANUFACTURER_ID)
                ?: return
            trySend(bytes)
        }

        override fun onScanFailed(errorCode: Int) {
            close(IllegalStateException("BLE scan failed, error code $errorCode"))
        }
    }

    val settings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()
    startScan(null, settings, callback)

    awaitClose { stopScan(callback) }
}
