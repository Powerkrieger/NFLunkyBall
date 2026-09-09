package com.example.nflunkyball.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context

object BleCapability {

    fun bluetoothAdapter(context: Context): BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    /** False both when there's no Bluetooth hardware at all and when it's just turned off. */
    fun isBluetoothEnabled(context: Context): Boolean =
        bluetoothAdapter(context)?.isEnabled == true

    /** Scanning (viewing scores) works on effectively every device with Bluetooth enabled. */
    fun canScan(context: Context): Boolean =
        bluetoothAdapter(context)?.isEnabled == true

    /** Hosting a tournament or sending an emoji requires BLE peripheral/advertising support. */
    fun canAdvertise(context: Context): Boolean {
        val adapter = bluetoothAdapter(context) ?: return false
        return adapter.isEnabled && adapter.isMultipleAdvertisementSupported
    }

    /**
     * Bluetooth 5 extended advertising (baseline since BT 5.0 chipsets, ~2016+) lets each
     * chunk carry far more than the legacy 31-byte cap — collapsing what would otherwise be
     * dozens of legacy chunks into a handful. Only the advertiser (host) needs this; scanning
     * already reports extended results transparently through the same ScanCallback API, so a
     * receiver on older hardware still falls back to whatever legacy chunks a non-capable host
     * sends, and a capable host's extended chunks are just bigger versions of the exact same
     * wire format (no protocol change on the receive side).
     */
    fun supportsExtendedAdvertising(context: Context): Boolean {
        val adapter = bluetoothAdapter(context) ?: return false
        return adapter.isEnabled &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
            adapter.isLeExtendedAdvertisingSupported
    }

    /** Real per-device ceiling for one extended-advertising chunk's payload, already reduced by [BleConstants.HEADER_SIZE_BYTES] and capped at [BleConstants.MAX_EXTENDED_CHUNK_PAYLOAD_BYTES] for headroom. */
    fun extendedChunkPayloadBytes(context: Context): Int {
        val adapter = bluetoothAdapter(context) ?: return BleConstants.MAX_CHUNK_PAYLOAD_BYTES
        val deviceMax = adapter.leMaximumAdvertisingDataLength - BleConstants.HEADER_SIZE_BYTES
        return deviceMax.coerceAtMost(BleConstants.MAX_EXTENDED_CHUNK_PAYLOAD_BYTES)
    }
}
