package com.unofficialpixstudio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** Runtime permissions needed for BLE scanning and connecting on this device's API level. */
val bluetoothPermissions: Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
    } else {
        // BLUETOOTH / BLUETOOTH_ADMIN are install-time; scanning needs location at runtime.
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

fun Context.hasBluetoothPermissions(): Boolean =
    bluetoothPermissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }
