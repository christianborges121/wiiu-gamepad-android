package com.cemupad.network

import com.cemupad.util.Logger
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Data class representing a discovered Cemu instance on the local network.
 */
data class DiscoveredServer(
    val ip: String,
    val hostname: String,
    val dsuPort: Int,
    val videoPort: Int,
    val audioPort: Int
)

/**
 * UDP Discovery Client for locating Cemu instances running on the local network (port 26763).
 * Broadcasts "CEMUPAD_DISCOVER" and listens for "CEMUPAD_HERE:<hostname>:<dsu_port>:<video_port>:<audio_port>".
 */
class DiscoveryClient(
    private val onServerDiscovered: (DiscoveredServer) -> Unit
) {
    companion object {
        private const val TAG = "DiscoveryClient"
        const val DISCOVERY_PORT = 26763
        private const val DISCOVER_MAGIC = "CEMUPAD_DISCOVER"
        private const val RESPONSE_PREFIX = "CEMUPAD_HERE:"
        private const val BROADCAST_INTERVAL_MS = 2000L
        private const val SOCKET_TIMEOUT_MS = 1000
    }

    private val isRunning = AtomicBoolean(false)
    private var workerThread: Thread? = null

    fun start() {
        if (isRunning.getAndSet(true)) return
        workerThread = Thread({
            runDiscoveryLoop()
        }, "CemuPad-Discovery").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        isRunning.set(false)
        workerThread?.interrupt()
        workerThread = null
    }

    private fun runDiscoveryLoop() {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket().apply {
                broadcast = true
                soTimeout = SOCKET_TIMEOUT_MS
            }

            val requestData = DISCOVER_MAGIC.toByteArray(Charsets.UTF_8)
            val recvBuffer = ByteArray(512)
            val recvPacket = DatagramPacket(recvBuffer, recvBuffer.size)
            var lastBroadcastTime = 0L

            while (isRunning.get()) {
                val now = System.currentTimeMillis()
                if (now - lastBroadcastTime >= BROADCAST_INTERVAL_MS) {
                    lastBroadcastTime = now
                    sendBroadcastPings(socket, requestData)
                }

                try {
                    recvPacket.length = recvBuffer.size
                    socket.receive(recvPacket)
                    val text = String(recvPacket.data, 0, recvPacket.length, Charsets.UTF_8).trim()
                    if (text.startsWith(RESPONSE_PREFIX)) {
                        val senderIp = recvPacket.address.hostAddress ?: ""
                        parseResponse(senderIp, text)?.let { server ->
                            Logger.i(TAG, "Discovered Cemu at ${server.ip} (${server.hostname})")
                            onServerDiscovered(server)
                        }
                    }
                } catch (e: SocketTimeoutException) {
                    // Normal timeout while waiting for responses
                }
            }
        } catch (e: InterruptedException) {
            // Stopping
        } catch (e: Exception) {
            Logger.w(TAG, "Discovery client error: ${e.message}")
        } finally {
            socket?.close()
        }
    }

    private fun sendBroadcastPings(socket: DatagramSocket, data: ByteArray) {
        // Send to standard 255.255.255.255 broadcast
        try {
            val globalBroadcast = DatagramPacket(
                data, data.size,
                InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT
            )
            socket.send(globalBroadcast)
        } catch (e: Exception) {
            Logger.v(TAG, "Failed global broadcast ping: ${e.message}")
        }

        // Also broadcast on each active network interface's subnet broadcast address
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return
            for (iface in interfaces) {
                if (iface.isLoopback || !iface.isUp) continue
                for (addr in iface.interfaceAddresses) {
                    val bcast = addr.broadcast ?: continue
                    try {
                        val pkt = DatagramPacket(data, data.size, bcast, DISCOVERY_PORT)
                        socket.send(pkt)
                    } catch (ignored: Exception) {
                    }
                }
            }
        } catch (e: Exception) {
            Logger.v(TAG, "Failed subnet broadcast ping: ${e.message}")
        }
    }

    private fun parseResponse(senderIp: String, text: String): DiscoveredServer? {
        val parts = text.substring(RESPONSE_PREFIX.length).split(":")
        if (parts.size < 4) return null
        val hostname = parts[0]
        val dsuPort = parts[1].toIntOrNull() ?: 26760
        val videoPort = parts[2].toIntOrNull() ?: 26761
        val audioPort = parts[3].toIntOrNull() ?: 26762
        return DiscoveredServer(senderIp, hostname, dsuPort, videoPort, audioPort)
    }
}
