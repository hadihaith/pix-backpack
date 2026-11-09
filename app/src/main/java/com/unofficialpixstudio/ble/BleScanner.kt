package com.unofficialpixstudio.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import com.unofficialpixstudio.hasBluetoothPermissions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** A BLE device seen during a scan. [address] is unique per device and used as its key. */
data class BleDevice(
    val address: String,
    val name: String?,
    val rssi: Int,
)

class BleScanException(message: String) : Exception(message)

class BleScanner(private val context: Context) {

    private val adapter: BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    val isBluetoothSupported: Boolean get() = adapter != null
    val isBluetoothEnabled: Boolean get() = adapter?.isEnabled == true

    /**
     * Scans for BLE devices and emits each advertisement as a [BleDevice] until the collector
     * is cancelled, at which point the scan is stopped. Fails with [BleScanException] if the
     * scan can't start or the system reports a scan failure.
     */
    @SuppressLint("MissingPermission") // Checked explicitly below.
    fun scan(): Flow<BleDevice> = callbackFlow {
        if (!context.hasBluetoothPermissions()) {
            throw BleScanException("Bluetooth permissions not granted")
        }
        val scanner = adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner
            ?: throw BleScanException("Bluetooth is off")

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                trySend(result.toBleDevice())
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { trySend(it.toBleDevice()) }
            }

            override fun onScanFailed(errorCode: Int) {
                close(BleScanException("Scan failed: ${scanErrorName(errorCode)}"))
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner.startScan(null, settings, callback)
        // stopScan can throw if Bluetooth was turned off mid-scan; nothing to clean up then.
        awaitClose { runCatching { scanner.stopScan(callback) } }
    }

    @SuppressLint("MissingPermission") // BLUETOOTH_CONNECT is requested alongside BLUETOOTH_SCAN.
    private fun ScanResult.toBleDevice(): BleDevice {
        val name = scanRecord?.deviceName
            ?: try {
                device.name
            } catch (_: SecurityException) {
                null
            }
        return BleDevice(address = device.address, name = name, rssi = rssi)
    }

    private fun scanErrorName(code: Int): String = when (code) {
        ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "already started"
        ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "app registration failed"
        ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "internal error"
        ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "BLE scanning not supported"
        5 -> "out of hardware resources"
        6 -> "scanning too frequently"
        else -> "error $code"
    }
}
