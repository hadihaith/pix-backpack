package com.unofficialpixstudio.ble

/** PIX-specific commands on top of a connected [PixGattClient]. */
class PixBackpack(private val client: PixGattClient) {

    private val encoder = PixCommandEncoder()

    /**
     * Writes brightness to 0106 and reads it back so [PixGattClient.values] updates.
     * [userPercent] is on the app's 0–100 % scale, whose 100 % is the safe maximum
     * ([PixBrightness.userPercentToRaw]), so it can't go above the cap.
     */
    suspend fun setBrightness(userPercent: Int) {
        client.write(PixUuids.BRIGHTNESS, byteArrayOf(PixBrightness.userPercentToRaw(userPercent).toByte()))
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
