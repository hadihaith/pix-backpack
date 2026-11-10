package com.unofficialpixstudio.ble

/** PIX-specific commands on top of a connected [PixGattClient]. */
class PixBackpack(private val client: PixGattClient) {

    private val encoder = PixCommandEncoder()

    /**
     * Writes brightness to 0106, capped at [PixBrightness.MAX_SAFE_PERCENT], and reads it back
     * so [PixGattClient.values] updates.
     */
    suspend fun setBrightness(percent: Int) {
        val safe = percent.coerceIn(0, PixBrightness.MAX_SAFE_PERCENT)
        client.write(PixUuids.BRIGHTNESS, byteArrayOf(PixBrightness.percentToRaw(safe).toByte()))
        client.read(PixUuids.BRIGHTNESS)
    }

    /**
     * Uploads and plays [animation] from RAM (not saved to flash; it's gone after a power
     * cycle). Stops at the first failed write. [onProgress] gets (packetsSent, totalPackets).
     */
    suspend fun showAnimation(
        animation: PixAnimation,
        onProgress: (sent: Int, total: Int) -> Unit = { _, _ -> },
    ) {
        val packets = encoder.uploadAnimation(animation)
        packets.forEachIndexed { i, packet ->
            client.write(PixUuids.RPC, packet)
            onProgress(i + 1, packets.size)
        }
    }
}
