package com.cemupad.dsu

import com.cemupad.util.Logger as Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/**
 * UDP Server implementing the DSU (CemuHook) protocol on port 26760.
 * Cemu acts as the client, initiating requests to this server.
 */
class DSUServer(
    val port: Int = DEFAULT_PORT,
    private val serverUid: Int = (Math.random() * Int.MAX_VALUE).toInt()
) {
    companion object {
        const val TAG = "DSUServer"
        const val DEFAULT_PORT = 26760
        private const val CLIENT_TIMEOUT_MS = 5000L
    }

    private var socket: DatagramSocket? = null
    private val isRunning = AtomicBoolean(false)

    // Current controller state (thread-safe snapshot)
    private val stateLock = Any()
    private var currentState = DSUPacket.ControllerState()
    private val packetCounter = AtomicInteger(0)

    val controllerState: DSUPacket.ControllerState
        get() = synchronized(stateLock) { currentState.copy() }

    // Active registered subscribers: Address -> Last Seen Timestamp (ms)
    private val activeClients = ConcurrentHashMap<InetSocketAddress, Long>()

    // Diagnostics
    val packetsReceived = AtomicLong(0)
    val packetsSent = AtomicLong(0)
    val activeClientCount: Int get() = activeClients.size
    val activeClientAddress: InetSocketAddress? get() = activeClients.keys.firstOrNull()

    // Listener for UI callbacks
    var onClientConnected: ((InetSocketAddress) -> Unit)? = null
    var onClientDisconnected: ((InetSocketAddress) -> Unit)? = null

    /**
     * Updates the current physical/touch/motion state of the controller.
     */
    fun updateState(updater: (DSUPacket.ControllerState) -> Unit) {
        synchronized(stateLock) {
            updater(currentState)
        }
    }

    /**
     * Replaces the entire controller state.
     */
    fun setState(state: DSUPacket.ControllerState) {
        synchronized(stateLock) {
            currentState = state
        }
    }

    /**
     * Starts the DSU server on a background thread.
     */
    fun start(): Boolean {
        if (isRunning.get()) return true

        try {
            val sock = DatagramSocket(port)
            sock.reuseAddress = true
            socket = sock
            isRunning.set(true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind DSU socket on port $port", e)
            return false
        }

        // Receive thread
        thread(name = "DSU-RecvThread") {
            runReceiveLoop()
        }

        // Periodic maintenance & push thread (~100Hz)
        thread(name = "DSU-SendLoop") {
            runPushLoop()
        }

        Log.i(TAG, "DSU Server listening on 0.0.0.0:$port (Server UID: $serverUid)")
        return true
    }

    /**
     * Stops the DSU server and releases socket resources.
     */
    fun stop() {
        isRunning.set(false)
        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null
        activeClients.clear()
        Log.i(TAG, "DSU Server stopped.")
    }

    private fun runReceiveLoop() {
        val recvBuffer = ByteArray(1024)
        val recvPacket = DatagramPacket(recvBuffer, recvBuffer.size)

        while (isRunning.get()) {
            val sock = socket ?: break
            try {
                // CRITICAL FIX: Reset length before every receive iteration.
                // DatagramSocket.receive() alters packet.length to the size of the received packet.
                recvPacket.length = recvBuffer.size

                sock.receive(recvPacket)
                packetsReceived.incrementAndGet()

                val clientAddr = InetSocketAddress(recvPacket.address, recvPacket.port)
                handlePacket(sock, recvBuffer, recvPacket.length, clientAddr)
            } catch (e: SocketException) {
                if (!isRunning.get()) break
                Log.e(TAG, "SocketException in receive loop", e)
            } catch (e: Exception) {
                if (!isRunning.get()) break
                Log.e(TAG, "Error handling incoming packet", e)
            }
        }
    }

    private fun handlePacket(
        sock: DatagramSocket,
        buffer: ByteArray,
        length: Int,
        clientAddr: InetSocketAddress
    ) {
        val header = DSUPacket.parseClientHeader(buffer, length) ?: return
        val (_, messageType) = header

        when (messageType) {
            DSUPacket.TYPE_VERSION -> {
                val resp = DSUPacket.createVersionResponse(serverUid)
                sendPacket(sock, resp, clientAddr)
            }

            DSUPacket.TYPE_PORT_INFO -> {
                val resp = DSUPacket.createPortInfoResponse(serverUid)
                sendPacket(sock, resp, clientAddr)
            }

            DSUPacket.TYPE_DATA -> {
                // Register / update subscriber
                val wasActive = activeClients.containsKey(clientAddr)
                activeClients[clientAddr] = System.currentTimeMillis()
                if (!wasActive) {
                    Log.i(TAG, "New Cemu client subscribed: $clientAddr")
                    onClientConnected?.invoke(clientAddr)
                }

                // Send immediate data response
                val snapshot = synchronized(stateLock) { currentState.copy() }
                val resp = DSUPacket.createDataResponse(
                    serverUid = serverUid,
                    packetCounter = packetCounter.getAndIncrement(),
                    state = snapshot
                )
                sendPacket(sock, resp, clientAddr)
            }
        }
    }

    private fun runPushLoop() {
        while (isRunning.get()) {
            val now = System.currentTimeMillis()
            val sock = socket ?: break

            // Prune inactive clients
            val iterator = activeClients.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (now - entry.value > CLIENT_TIMEOUT_MS) {
                    iterator.remove()
                    Log.i(TAG, "Cemu client timed out: ${entry.key}")
                    onClientDisconnected?.invoke(entry.key)
                }
            }

            // Stream continuous updates to registered clients at ~100Hz
            if (activeClients.isNotEmpty()) {
                val snapshot = synchronized(stateLock) { currentState.copy() }
                val resp = DSUPacket.createDataResponse(
                    serverUid = serverUid,
                    packetCounter = packetCounter.getAndIncrement(),
                    state = snapshot
                )

                for (client in activeClients.keys) {
                    sendPacket(sock, resp, client)
                }
            }

            try {
                Thread.sleep(10) // 10ms ~ 100Hz
            } catch (_: InterruptedException) {
                break
            }
        }
    }

    private fun sendPacket(sock: DatagramSocket, data: ByteArray, dest: InetSocketAddress) {
        try {
            val pkt = DatagramPacket(data, data.size, dest)
            sock.send(pkt)
            packetsSent.incrementAndGet()
        } catch (e: Exception) {
            if (isRunning.get()) {
                Log.w(TAG, "Failed to send packet to $dest: ${e.message}")
            }
        }
    }
}
