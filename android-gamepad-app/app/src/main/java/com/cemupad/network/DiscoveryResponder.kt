package com.cemupad.network

import android.os.Build
import com.cemupad.util.Logger
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * UDP Discovery Responder answering Cemu PC probes on port 26763.
 *
 * - Listens on UDP [DISCOVERY_PORT] for "CEMU_DISCOVER" probes broadcast by
 *   Cemu's pairing dialog (and "CEMUPAD_DISCOVER" from other tools/phones).
 * - Answers each probe with a unicast
 *   "CEMUPAD_HERE:<device>:<dsu_port>:<video_port>:<audio_port>" datagram so
 *   the PC can list this phone for 1-click pairing.
 * - Silent on the network unless probed; safe to keep running while streaming.
 */
class DiscoveryResponder(
    private val deviceName: String = defaultDeviceName(),
    private val onProbeReceived: ((senderIp: String) -> Unit)? = null
) {
    companion object {
        private const val TAG = "DiscoveryResponder"
        const val DISCOVERY_PORT = 26763
        const val DSU_PORT = 26760
        const val VIDEO_PORT = 26761
        const val AUDIO_PORT = 26762
        private const val PROBE_PC = "CEMU_DISCOVER"
        private const val PROBE_PHONE = "CEMUPAD_DISCOVER"
        private const val HERE_PREFIX = "CEMUPAD_HERE:"
        private const val SOCKET_TIMEOUT_MS = 1000

        fun defaultDeviceName(): String = sanitizeDeviceName(Build.MODEL ?: "CemuPad")

        fun sanitizeDeviceName(raw: String): String {
            val cleaned = raw.replace(":", "-")
                .replace("\n", " ")
                .replace("\r", " ")
                .trim()
            return cleaned.ifEmpty { "CemuPad" }.take(64)
        }

        fun isProbePacket(text: String): Boolean {
            val payload = text.trim()
            return payload.startsWith(PROBE_PC) || payload.startsWith(PROBE_PHONE)
        }

        fun buildHereResponse(deviceName: String): String {
            return "$HERE_PREFIX${sanitizeDeviceName(deviceName)}:$DSU_PORT:$VIDEO_PORT:$AUDIO_PORT"
        }
    }

    private val isRunning = AtomicBoolean(false)
    private var workerThread: Thread? = null

    fun start() {
        if (isRunning.getAndSet(true)) return
        workerThread = Thread({
            runResponderLoop()
        }, "CemuPad-DiscoveryResponder").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        isRunning.set(false)
        workerThread?.interrupt()
        workerThread = null
    }

    private fun runResponderLoop() {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket(DISCOVERY_PORT).apply {
                broadcast = true
                soTimeout = SOCKET_TIMEOUT_MS
            }
            Logger.i(TAG, "Responding to discovery probes on UDP $DISCOVERY_PORT")

            val recvBuffer = ByteArray(512)
            val recvPacket = DatagramPacket(recvBuffer, recvBuffer.size)
            val responseData = buildHereResponse(deviceName).toByteArray(Charsets.UTF_8)

            while (isRunning.get()) {
                try {
                    recvPacket.length = recvBuffer.size
                    socket.receive(recvPacket)
                    val text = String(recvPacket.data, 0, recvPacket.length, Charsets.UTF_8)
                    if (!isProbePacket(text)) continue

                    val senderIp = recvPacket.address?.hostAddress ?: ""
                    // Reply to the sender's port (ephemeral listeners) AND to the
                    // well-known discovery port: PC probes originate from a
                    // transient socket, so a sender-port-only reply would land
                    // where nobody listens and the PC dialog would stay empty.
                    val replyToSender = DatagramPacket(
                        responseData, responseData.size,
                        recvPacket.address, recvPacket.port
                    )
                    socket.send(replyToSender)
                    if (recvPacket.port != DISCOVERY_PORT) {
                        val replyToDiscoveryPort = DatagramPacket(
                            responseData, responseData.size,
                            recvPacket.address, DISCOVERY_PORT
                        )
                        socket.send(replyToDiscoveryPort)
                    }
                    Logger.i(TAG, "Answered discovery probe from $senderIp")
                    if (senderIp.isNotEmpty()) {
                        try {
                            onProbeReceived?.invoke(senderIp)
                        } catch (_: Exception) {
                        }
                    }
                } catch (e: SocketTimeoutException) {
                    // Normal timeout while waiting for probes
                }
            }
        } catch (e: InterruptedException) {
            // Stopping
        } catch (e: Exception) {
            Logger.w(TAG, "Discovery responder error: ${e.message}")
        } finally {
            socket?.close()
        }
    }
}
