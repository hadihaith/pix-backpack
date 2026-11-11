package com.unofficialpixstudio.ble

import android.app.Application
import android.content.Context
import android.util.AtomicFile
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.unofficialpixstudio.hasBluetoothPermissions
import com.unofficialpixstudio.image.PixelArt
import com.unofficialpixstudio.image.SentPicture
import com.unofficialpixstudio.image.SentPictureCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.util.UUID

data class DisplaySize(val width: Int, val height: Int)

data class DeviceInfo(val firmware: String?, val hardware: String?)

/**
 * Owns the backpack connection so it survives rotation and screen changes, and is shared by
 * every screen that talks to the backpack.
 *
 * The selected backpack is remembered across app restarts: if one was connected when the app
 * was closed, it reconnects on launch. Unexpected disconnects/failures are retried a few times.
 */
class BackpackViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val client = PixGattClient(application)
    private val backpack = PixBackpack(client)

    val connectionState: StateFlow<GattState> = client.state
    val values: StateFlow<Map<UUID, CharacteristicReading>> = client.values
    val subscriptions: StateFlow<Map<UUID, String>> = client.subscriptions
    val notifications: StateFlow<List<NotificationEntry>> = client.notifications

    /** Display size from 0103/0104, falling back to the documented 16×20 until read. */
    val displaySize: StateFlow<DisplaySize> = client.values
        .map { values ->
            DisplaySize(
                width = values.byteValue(PixUuids.WIDTH)?.takeIf { it > 0 } ?: 16,
                height = values.byteValue(PixUuids.HEIGHT)?.takeIf { it > 0 } ?: 20,
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DisplaySize(16, 20))

    /**
     * Current brightness on the app's 0–100 % scale (100 % is the safe maximum, see
     * [PixBrightness.userPercentToRaw]), or null until read.
     */
    val brightnessPercent: StateFlow<Int?> = client.values
        .map { values -> values.byteValue(PixUuids.BRIGHTNESS)?.let(PixBrightness::rawToUserPercent) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Firmware and hardware revision strings from the Device Information service. */
    val deviceInfo: StateFlow<DeviceInfo> = client.values
        .map { values -> DeviceInfo(values.text(FIRMWARE_UUID), values.text(HARDWARE_UUID)) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DeviceInfo(null, null))

    private val lastSentFile = AtomicFile(File(application.filesDir, LAST_SENT_FILE))
    private val _lastSent = MutableStateFlow<SentPicture?>(null)

    private val _device = MutableStateFlow<BleDevice?>(null)
    /** The backpack the app is working with, or null to show the scanner. */
    val device: StateFlow<BleDevice?> = _device.asStateFlow()

    /**
     * The last picture or animation this phone sent to the current backpack, saved to storage
     * so it's still shown after the app restarts. The backpack can't report its screen, so this may be stale
     * if it was power-cycled or something else sent a picture since.
     */
    val nowShowing: StateFlow<SentPicture?> = combine(_lastSent, _device) { picture, device ->
        picture?.takeIf { device != null && it.address == device.address }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _readingAll = MutableStateFlow(false)
    val readingAll: StateFlow<Boolean> = _readingAll.asStateFlow()

    private val _busy = MutableStateFlow(false)
    /** True while a write action (brightness, upload) is running. */
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _brightnessLimit = MutableStateFlow(100)
    /**
     * Highest brightness (0–100 % scale) the slider allows for what's currently on the
     * display: 100 %, or lower for bright, white-heavy images (see [PixBrightness.maxUserPercentFor]).
     */
    val brightnessLimit: StateFlow<Int> = _brightnessLimit.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    /** Progress or result of the last write action. */
    val message: StateFlow<String?> = _message.asStateFlow()

    /** False after the user taps Disconnect, so we don't fight them by reconnecting. */
    private var autoReconnect = true
    private var retries = 0

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val saved = runCatching { lastSentFile.openRead().use(SentPictureCodec::read) }.getOrNull()
            // Don't overwrite a picture sent while this was loading.
            _lastSent.compareAndSet(null, saved)
        }

        // Read everything once each time the connection becomes ready.
        viewModelScope.launch {
            client.state.map { it is GattState.Ready }.distinctUntilChanged().collect { ready ->
                if (ready) {
                    retries = 0
                    readAll()
                }
            }
        }
        // Retry failed or dropped connections. collectLatest cancels a pending retry if the
        // state changes meanwhile (e.g. the user taps Connect).
        viewModelScope.launch {
            client.state.collectLatest { state ->
                val device = _device.value ?: return@collectLatest
                val dropped = state is GattState.Failed || state is GattState.Disconnected
                if (dropped && autoReconnect && retries < MAX_RETRIES) {
                    delay(RETRY_DELAY_MS)
                    retries++
                    client.connect(device.address)
                }
            }
        }

        // Reconnect to the last backpack, unless permissions were revoked meanwhile; then go
        // back to the scanner, which asks for them.
        val saved = loadSavedDevice()
        if (saved != null && application.hasBluetoothPermissions()) {
            _device.value = saved
            connect()
        } else if (saved != null) {
            forgetDevice()
        }
    }

    /** Picks [device] from the scan, remembers it and connects. */
    fun selectDevice(device: BleDevice) {
        prefs.edit {
            putString(KEY_ADDRESS, device.address)
            putString(KEY_NAME, device.name)
        }
        _device.value = device
        connect()
    }

    fun connect() {
        val device = _device.value ?: return
        autoReconnect = true
        retries = 0
        _message.value = null
        client.connect(device.address)
    }

    fun disconnect() {
        autoReconnect = false
        client.disconnect()
    }

    /** Disconnects and forgets the backpack, returning to the scanner. */
    fun forgetDevice() {
        disconnect()
        prefs.edit { clear() }
        _device.value = null
    }

    fun readAll() {
        if (_readingAll.value) return
        viewModelScope.launch {
            _readingAll.value = true
            try {
                client.readAllAndSubscribe()
            } finally {
                _readingAll.value = false
            }
        }
    }

    fun read(characteristic: UUID) {
        viewModelScope.launch { runCatching { client.read(characteristic) } }
    }

    fun setBrightness(percent: Int) {
        val target = percent.coerceIn(0, _brightnessLimit.value)
        runAction("Setting brightness to $target%…") {
            backpack.setBrightness(target)
            null
        }
    }

    fun sendTestImage(width: Int, height: Int, playOnce: Boolean) =
        send(
            label = "test image",
            frames = listOf(pixTestPattern(width, height)),
            width = width,
            height = height,
            frameMs = STILL_FRAME_MS,
            direction = if (playOnce) PixDirection.NORMAL_STOP else PixDirection.NORMAL,
        )

    /** Sends a still picture (any colours; reduced to the 256-colour palette here). */
    fun sendImage(pixels: IntArray, width: Int, height: Int) =
        send("image", listOf(PixelArt.quantize(pixels)), width, height, STILL_FRAME_MS, PixDirection.NORMAL_STOP)

    /**
     * Sends [frames] to play in a loop, each shown for [frameMs] milliseconds. Together the
     * frames must use no more than 256 colours.
     */
    fun sendAnimation(frames: List<IntArray>, width: Int, height: Int, frameMs: Int) =
        send("animation", frames, width, height, frameMs, PixDirection.NORMAL)

    /**
     * Uploads [frames]. If the current brightness is above what the brightest frame can show
     * without flicker, lowers it first, and caps the slider for as long as it's displayed.
     * Messages use the app's 0–100 % scale and don't explain the cap; users don't need to
     * know it.
     */
    private fun send(
        label: String,
        frames: List<IntArray>,
        width: Int,
        height: Int,
        frameMs: Int,
        direction: Int,
    ) = runAction("Sending $label…") {
        val animation = PixAnimation(
            width = width,
            height = height,
            frames = frames,
            frameDurationsMs = List(frames.size) { frameMs },
            direction = direction,
        )
        val limit = frames.minOf(PixBrightness::maxUserPercentFor)
        val current = currentBrightnessPercent()
        var note = ""
        if (current == null || current > limit) {
            _message.value = "Setting brightness to $limit% for this $label…"
            backpack.setBrightness(limit)
            if (current != null) note = " Brightness lowered to $limit% for this $label."
        }
        _brightnessLimit.value = limit

        backpack.showAnimation(animation) { sent, total ->
            _message.value = "Sending $label… $sent/$total"
        }
        _device.value?.let { device ->
            rememberSent(SentPicture(device.address, width, height, frames, frameMs, System.currentTimeMillis()))
        }
        "Sent the $label.$note"
    }

    private fun currentBrightnessPercent(): Int? = brightnessPercent.value

    /** Keeps [picture] as the last one sent and saves it, replacing the file atomically. */
    private fun rememberSent(picture: SentPicture) {
        _lastSent.value = picture
        viewModelScope.launch(Dispatchers.IO) {
            val stream = lastSentFile.startWrite()
            try {
                SentPictureCodec.write(picture, stream)
                lastSentFile.finishWrite(stream)
            } catch (e: IOException) {
                lastSentFile.failWrite(stream)
            }
        }
    }

    private fun Map<UUID, CharacteristicReading>.byteValue(uuid: UUID): Int? =
        (this[uuid] as? CharacteristicReading.Value)?.bytes?.firstOrNull()?.toInt()?.and(0xFF)

    private fun Map<UUID, CharacteristicReading>.text(uuid: UUID): String? =
        (this[uuid] as? CharacteristicReading.Value)?.bytes
            ?.toString(Charsets.UTF_8)?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * Runs one write action at a time. [action] returns the final message (or null to clear
     * it); failures are reported as messages rather than thrown.
     */
    private fun runAction(startMessage: String, action: suspend () -> String?) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _message.value = startMessage
            _message.value = try {
                action()
            } catch (e: GattException) {
                "Failed: ${e.message}"
            } catch (e: IllegalArgumentException) {
                "Failed: ${e.message}"
            } finally {
                _busy.value = false
            }
        }
    }

    private fun loadSavedDevice(): BleDevice? {
        val address = prefs.getString(KEY_ADDRESS, null) ?: return null
        return BleDevice(address = address, name = prefs.getString(KEY_NAME, null), rssi = 0)
    }

    override fun onCleared() {
        client.disconnect()
    }

    private companion object {
        const val PREFS_NAME = "backpack"
        const val KEY_ADDRESS = "last_address"
        const val KEY_NAME = "last_name"
        const val MAX_RETRIES = 3
        const val RETRY_DELAY_MS = 2_000L
        const val STILL_FRAME_MS = 1000
        const val LAST_SENT_FILE = "last_sent_picture.bin"
        val FIRMWARE_UUID: UUID = UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb")
        val HARDWARE_UUID: UUID = UUID.fromString("00002a27-0000-1000-8000-00805f9b34fb")
    }
}
