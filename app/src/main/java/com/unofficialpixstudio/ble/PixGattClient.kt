package com.unofficialpixstudio.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import android.util.Log
import com.unofficialpixstudio.hasBluetoothPermissions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

data class GattCharacteristicInfo(val uuid: UUID, val properties: Int) {
    val isReadable: Boolean get() = properties and BluetoothGattCharacteristic.PROPERTY_READ != 0

    /** Human-readable property flags, e.g. ["READ", "WRITE", "NOTIFY"]. */
    val propertyNames: List<String>
        get() = PROPERTY_NAMES.filter { (flag, _) -> properties and flag != 0 }.map { it.second }

    private companion object {
        val PROPERTY_NAMES = listOf(
            BluetoothGattCharacteristic.PROPERTY_BROADCAST to "BROADCAST",
            BluetoothGattCharacteristic.PROPERTY_READ to "READ",
            BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE to "WRITE NO RESPONSE",
            BluetoothGattCharacteristic.PROPERTY_WRITE to "WRITE",
            BluetoothGattCharacteristic.PROPERTY_NOTIFY to "NOTIFY",
            BluetoothGattCharacteristic.PROPERTY_INDICATE to "INDICATE",
            BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE to "SIGNED WRITE",
            BluetoothGattCharacteristic.PROPERTY_EXTENDED_PROPS to "EXTENDED",
        )
    }
}

data class GattServiceInfo(val uuid: UUID, val characteristics: List<GattCharacteristicInfo>)

sealed interface GattState {
    data object Disconnected : GattState
    /** Link is being established (includes MTU negotiation). */
    data object Connecting : GattState
    data object DiscoveringServices : GattState
    data class Ready(val services: List<GattServiceInfo>, val mtu: Int) : GattState
    data class Failed(val message: String) : GattState
}

/** Latest known value of a characteristic, from a read or a notification. */
sealed interface CharacteristicReading {
    /** A read has been requested and hasn't finished yet. */
    data object Pending : CharacteristicReading
    class Value(val bytes: ByteArray) : CharacteristicReading
    class Error(val message: String) : CharacteristicReading
}

class NotificationEntry(val characteristic: UUID, val bytes: ByteArray, val timeMillis: Long)

class GattException(message: String) : Exception(message)

/**
 * GATT client for a single PIX backpack connection. Connects, negotiates a larger MTU and
 * discovers services; progress is published on [state].
 *
 * Android allows only one GATT operation in flight at a time and silently drops the rest, so
 * reads and descriptor writes go through [runOp], which runs them one by one and suspends until
 * the matching callback arrives.
 *
 * GATT callbacks arrive on a binder thread; everything they touch is a StateFlow, volatile, or
 * a CompletableDeferred, all safe to update from there.
 */
// connect() checks BLUETOOTH_CONNECT before any GATT call is made.
@SuppressLint("MissingPermission")
class PixGattClient(context: Context) {

    private val context = context.applicationContext
    private val adapter = this.context.getSystemService(BluetoothManager::class.java)?.adapter

    private val _state = MutableStateFlow<GattState>(GattState.Disconnected)
    val state: StateFlow<GattState> = _state.asStateFlow()

    private val _values = MutableStateFlow<Map<UUID, CharacteristicReading>>(emptyMap())
    /** Latest reading per characteristic UUID. */
    val values: StateFlow<Map<UUID, CharacteristicReading>> = _values.asStateFlow()

    private val _subscriptions = MutableStateFlow<Map<UUID, String>>(emptyMap())
    /** Notification subscription result per characteristic: "subscribed" or "failed: …". */
    val subscriptions: StateFlow<Map<UUID, String>> = _subscriptions.asStateFlow()

    private val _notifications = MutableStateFlow<List<NotificationEntry>>(emptyList())
    /** Most recent notifications/indications, newest first. */
    val notifications: StateFlow<List<NotificationEntry>> = _notifications.asStateFlow()

    @Volatile
    private var gatt: BluetoothGatt? = null

    @Volatile
    private var mtu = DEFAULT_MTU

    private class PendingOp(val characteristic: UUID) {
        val result = CompletableDeferred<ByteArray>()
    }

    private val opMutex = Mutex()

    @Volatile
    private var pendingOp: PendingOp? = null

    fun connect(address: String) {
        disconnect()

        if (!context.hasBluetoothPermissions()) {
            _state.value = GattState.Failed("Bluetooth permission not granted")
            return
        }
        val device = adapter?.takeIf { it.isEnabled }?.getRemoteDevice(address)
        if (device == null) {
            _state.value = GattState.Failed("Bluetooth is off")
            return
        }

        Log.d(TAG, "Connecting to $address")
        mtu = DEFAULT_MTU
        _values.value = emptyMap()
        _subscriptions.value = emptyMap()
        _notifications.value = emptyList()
        _state.value = GattState.Connecting
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        if (gatt == null) _state.value = GattState.Failed("Couldn't start the connection")
    }

    fun disconnect() {
        val current = gatt ?: return
        gatt = null
        failPendingOp("Disconnected")
        // close() unregisters the callback, so no STATE_DISCONNECTED will follow; set it here.
        runCatching {
            current.disconnect()
            current.close()
        }
        _state.value = GattState.Disconnected
    }

    /** Reads a characteristic and records the result in [values]. */
    suspend fun read(characteristic: UUID): ByteArray {
        _values.update { it + (characteristic to CharacteristicReading.Pending) }
        val result = runCatching {
            runOp(characteristic) { g, c -> g.readCharacteristic(c) }
        }
        val reading = result.fold(
            onSuccess = { CharacteristicReading.Value(it) },
            onFailure = { CharacteristicReading.Error(it.message ?: "Read failed") },
        )
        _values.update { it + (characteristic to reading) }
        return result.getOrThrow()
    }

    /**
     * Writes [value] to a characteristic and suspends until the write is acknowledged.
     * Each write must fit in one ATT packet (MTU - 3 bytes); this never splits data.
     */
    suspend fun write(characteristic: UUID, value: ByteArray, withResponse: Boolean = true) {
        val maxPayload = mtu - ATT_HEADER_SIZE
        if (value.size > maxPayload) {
            throw GattException("${value.size}-byte write exceeds the $maxPayload-byte limit for MTU $mtu")
        }
        val writeType = if (withResponse) {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        }
        Log.d(TAG, "WRITE $characteristic: ${value.toHex()}")
        runOp(characteristic) { g, c -> g.writeCharacteristicCompat(c, value, writeType) }
    }

    /** Subscribes to notifications (or indications, if that's all it supports). */
    suspend fun enableNotifications(characteristic: UUID) {
        val result = runCatching { subscribe(characteristic) }
        val status = result.fold({ "subscribed" }, { "failed: ${it.message}" })
        _subscriptions.update { it + (characteristic to status) }
        Log.d(TAG, "Notifications for $characteristic: $status")
        result.getOrThrow()
    }

    private suspend fun subscribe(characteristic: UUID) {
        runOp(characteristic) { g, c ->
            val cccd = c.getDescriptor(CCCD_UUID) ?: return@runOp false
            val supportsNotify = c.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
            val value = if (supportsNotify) {
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            } else {
                BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            }
            g.setCharacteristicNotification(c, true) && g.writeDescriptorCompat(cccd, value)
        }
    }

    /**
     * Subscribes to every notifying characteristic in the PIX service, then reads every readable
     * characteristic. Subscribing comes first so a slow or stuck read can't stop notifications
     * from arriving. Individual failures are recorded in [values] / logged, not thrown.
     */
    suspend fun readAllAndSubscribe() {
        val g = gatt ?: return
        val notifyFlags =
            BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE
        val pixNotifying = g.getService(PIX_SERVICE_UUID)?.characteristics.orEmpty()
            .filter { it.properties and notifyFlags != 0 }
        for (c in pixNotifying) {
            runCatching { enableNotifications(c.uuid) } // Result is recorded in [subscriptions].
        }

        val readable = g.services.flatMap { it.characteristics }
            .filter { it.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0 }
        for (c in readable) {
            currentCoroutineContext().ensureActive() // runCatching would otherwise swallow cancellation.
            runCatching { read(c.uuid) }
        }
    }

    private suspend fun runOp(
        characteristic: UUID,
        start: (BluetoothGatt, BluetoothGattCharacteristic) -> Boolean,
    ): ByteArray = opMutex.withLock {
        val g = gatt ?: throw GattException("Not connected")
        val c = g.services.firstNotNullOfOrNull { it.getCharacteristic(characteristic) }
            ?: throw GattException("Characteristic not found")
        val op = PendingOp(characteristic)
        pendingOp = op
        try {
            if (!start(g, c)) throw GattException("Request rejected")
            withTimeoutOrNull(OP_TIMEOUT_MS) { op.result.await() }
                ?: throw GattException("Timed out")
        } finally {
            pendingOp = null
        }
    }

    private fun completePendingOp(characteristic: UUID, status: Int, value: ByteArray) {
        // A late callback from a timed-out op must not complete a newer op on another UUID.
        val op = pendingOp?.takeIf { it.characteristic == characteristic } ?: return
        if (status == BluetoothGatt.GATT_SUCCESS) {
            op.result.complete(value)
        } else {
            op.result.completeExceptionally(GattException(gattStatusMessage(status)))
        }
    }

    private fun gattStatusMessage(status: Int): String = when (status) {
        BluetoothGatt.GATT_READ_NOT_PERMITTED -> "read not permitted (status $status)"
        BluetoothGatt.GATT_WRITE_NOT_PERMITTED -> "write not permitted (status $status)"
        // These mean the backpack wants the phone to pair/bond first.
        BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION,
        BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION,
        BluetoothGatt.GATT_INSUFFICIENT_AUTHORIZATION -> "needs pairing (status $status)"
        else -> "GATT status $status"
    }

    private fun failPendingOp(message: String) {
        pendingOp?.result?.completeExceptionally(GattException(message))
    }

    private val callback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (g != gatt) return // Late callback from a connection we already dropped.

            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Connection error: $status")
                closeWith(g, GattState.Failed(connectionErrorMessage(status)))
                return
            }
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "Connected, requesting MTU $DESIRED_MTU")
                    // Bigger packets for later data transfer; discovery starts in onMtuChanged.
                    if (!g.requestMtu(DESIRED_MTU)) startServiceDiscovery(g)
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Disconnected")
                    closeWith(g, GattState.Disconnected)
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            if (g != gatt) return
            Log.d(TAG, "MTU changed to $mtu (status $status)")
            if (status == BluetoothGatt.GATT_SUCCESS) this@PixGattClient.mtu = mtu

            when (val current = _state.value) {
                GattState.Connecting -> startServiceDiscovery(g)
                // The backpack may renegotiate later; keep the reported MTU current.
                is GattState.Ready -> _state.value = current.copy(mtu = this@PixGattClient.mtu)
                else -> Unit
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (g != gatt) return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Service discovery failed: $status")
                _state.value = GattState.Failed("Service discovery failed (status $status)")
                return
            }

            val services = g.services.map { service ->
                GattServiceInfo(
                    uuid = service.uuid,
                    characteristics = service.characteristics.map {
                        GattCharacteristicInfo(it.uuid, it.properties)
                    },
                )
            }
            logServices(services)
            _state.value = GattState.Ready(services, mtu)
        }

        // Android 13+ passes the value directly; this overload isn't called below API 33.
        override fun onCharacteristicRead(
            g: BluetoothGatt,
            c: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            if (g != gatt) return
            Log.d(TAG, "READ ${c.uuid} status=$status: ${value.toHex()}")
            completePendingOp(c.uuid, status, value)
        }

        @Deprecated("Used below API 33")
        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            onCharacteristicRead(g, c, c.value ?: ByteArray(0), status)
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            if (g != gatt) return
            if (status != BluetoothGatt.GATT_SUCCESS) Log.e(TAG, "WRITE ${c.uuid} failed: $status")
            completePendingOp(c.uuid, status, ByteArray(0))
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            if (g != gatt) return
            completePendingOp(d.characteristic.uuid, status, ByteArray(0))
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            c: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            if (g != gatt) return
            Log.d(TAG, "NOTIFY ${c.uuid}: ${value.toHex()}")
            _values.update { it + (c.uuid to CharacteristicReading.Value(value)) }
            val entry = NotificationEntry(c.uuid, value, System.currentTimeMillis())
            _notifications.update { (listOf(entry) + it).take(MAX_NOTIFICATIONS) }
        }

        @Deprecated("Used below API 33")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            onCharacteristicChanged(g, c, c.value ?: ByteArray(0))
        }
    }

    @Suppress("DEPRECATION")
    private fun BluetoothGatt.writeCharacteristicCompat(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        writeType: Int,
    ): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            writeCharacteristic(characteristic, value, writeType) == BluetoothStatusCodes.SUCCESS
        } else {
            characteristic.writeType = writeType
            characteristic.value = value
            writeCharacteristic(characteristic)
        }

    @Suppress("DEPRECATION")
    private fun BluetoothGatt.writeDescriptorCompat(
        descriptor: BluetoothGattDescriptor,
        value: ByteArray,
    ): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS
        } else {
            descriptor.value = value
            writeDescriptor(descriptor)
        }

    private fun startServiceDiscovery(g: BluetoothGatt) {
        _state.value = GattState.DiscoveringServices
        if (!g.discoverServices()) {
            _state.value = GattState.Failed("Couldn't start service discovery")
        }
    }

    private fun closeWith(g: BluetoothGatt, newState: GattState) {
        if (g == gatt) gatt = null
        failPendingOp("Disconnected")
        g.close()
        _state.value = newState
    }

    private fun logServices(services: List<GattServiceInfo>) {
        Log.d(TAG, "========== GATT SERVICES ==========")
        for (service in services) {
            Log.d(TAG, "SERVICE: ${service.uuid}")
            for (c in service.characteristics) {
                Log.d(TAG, "  CHARACTERISTIC: ${c.uuid} ${c.propertyNames}")
            }
        }
        Log.d(TAG, "========== DISCOVERY COMPLETE ==========")
    }

    private fun connectionErrorMessage(status: Int): String = when (status) {
        8 -> "Connection timed out. Is the backpack still on and nearby?"
        19 -> "The backpack closed the connection"
        22 -> "The phone closed the connection"
        62 -> "Couldn't establish the connection. Try again."
        // 133 is Android's catch-all GATT_ERROR; it's often transient, especially on Samsung.
        133 -> "Connection failed (error 133). Try again."
        else -> "Connection failed (status $status)"
    }

    companion object {
        private const val TAG = "PixBackpackBLE"
        private const val DEFAULT_MTU = 23
        private const val DESIRED_MTU = 517
        private const val ATT_HEADER_SIZE = 3
        private const val OP_TIMEOUT_MS = 5_000L
        private const val MAX_NOTIFICATIONS = 50
        private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}

fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it) }
