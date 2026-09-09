package com.example.nflunkyball.ble

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object BlePermissions {

    /** Runtime permissions this app needs, on top of the always-granted manifest-only ones. */
    val required: Array<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
            add(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            // Required by the OS for BLE scanning pre-API 31; advertising needed no runtime
            // prompt on these versions (covered by the manifest-only BLUETOOTH/BLUETOOTH_ADMIN).
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        add(Manifest.permission.CAMERA)
    }.toTypedArray()

    fun hasAll(context: Context): Boolean =
        required.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
}
