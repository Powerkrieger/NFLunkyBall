package com.example.nflunkyball.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context

object BleCapability {

    fun bluetoothAdapter(context: Context): BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    /** Scanning (viewing scores) works on effectively every device with Bluetooth enabled. */
    fun canScan(context: Context): Boolean =
        bluetoothAdapter(context)?.isEnabled == true

    /** Hosting a tournament or sending an emoji requires BLE peripheral/advertising support. */
    fun canAdvertise(context: Context): Boolean {
        val adapter = bluetoothAdapter(context) ?: return false
        return adapter.isEnabled && adapter.isMultipleAdvertisementSupported
    }
}
