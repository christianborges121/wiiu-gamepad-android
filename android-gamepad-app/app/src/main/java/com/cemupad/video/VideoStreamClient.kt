package com.cemupad.video

import com.cemupad.util.Logger
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Low-latency TCP client for receiving framed H.264 video from Cemu (port 26761).
 *
 * Framing Protocol:
 * [1 byte: packetType] (0x01 = Video Frame, 0x02 = Stream Config)
 * [4 bytes: payloadSize] (uint32 little-endian)
 * [8 bytes: ptsUs] (uint64 little-endian presentation timestamp in microseconds)
 * [payloadSize bytes: Annex B NAL units]
 *
 * Reverse Control:
 * [1 byte: 0x10] -> IDR_REQUEST sent to trigger an immediate keyframe
 */
class VideoStreamClient(
    var host: String,
    var port: Int = DEFAULT_PORT,
    private val onFrameReceived: (nalData: ByteArray, ptsUs: Long) -> Unit
) {
    companion object {
        const val TAG = "VideoStreamClient"
        const val DEFAULT_PORT = 26761
        const val PACKET_TYPE_VIDEO = 0x01
        const val PACKET_TYPE_CONFIG = 0x02
        const val OPCODE_IDR_REQUEST = 0x10
        const val OPCODE_TRANSPORT_UDP = 0x11
        const val OPCODE_TRANSPORT_TCP = 0x12
        private const val CONNECT_TIMEOUT_MS = 5000
        private const val READ_TIMEOUT_MS = 15000
    }

    private val isRunning = AtomicBoolean(false)
    private var workerThread: Thread? = null
    private var sendExecutor: java.util.concurrent.ExecutorService? = null
    private var socket: Socket? = null
    private var outStream: DataOutputStream? = null

    var onConnected: (() -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null
    var onError: ((Throwable) -> Unit)? = null

    val isConnected: Boolean
        get() = isRunning.get() && socket?.isConnected == true && socket?.isClosed == false

    /**
     * When video flows over UDP, this TCP connection is control-only and
     * legitimately idle for long stretches. Read timeouts then must not
     * tear it down (tearing it down drops the client server-side and
     * starves the encoder). Server death is still covered by DSU pruning.
     */
    @Volatile var idleControlMode: Boolean = false

    fun start() {
        if (isRunning.getAndSet(true)) return

        sendExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
            Thread(r, "CemuPad-OpcodeSender").apply { isDaemon = true }
        }

        workerThread = Thread({
            runClientLoop()
        }, "CemuPad-VideoClient").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return
        closeSocket()
        workerThread?.interrupt()
        workerThread = null
        sendExecutor?.shutdownNow()
        sendExecutor = null
    }

    /** Watchdog hook: true while the worker thread is alive. */
    fun isWorkerAlive(): Boolean = workerThread?.isAlive == true

    /** Restarts a dead worker when this client is still supposed to run. */
    fun restartIfStalled(): Boolean {
        if (!isRunning.get() || isWorkerAlive()) return false
        Logger.w(TAG, "Video worker dead while supposed to run; restarting")
        if (sendExecutor == null || sendExecutor?.isShutdown == true) {
            sendExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
                Thread(r, "CemuPad-OpcodeSender").apply { isDaemon = true }
            }
        }
        workerThread = Thread({
            runClientLoop()
        }, "CemuPad-VideoClient").apply {
            isDaemon = true
            start()
        }
        return true
    }

    /**
     * Sends an IDR (Keyframe) request to Cemu to recover from frame loss or initialize a new stream.
     */
    fun requestIDR() {
        sendOpcode(OPCODE_IDR_REQUEST, "IDR_REQUEST")
    }

    /**
     * Asks Cemu to switch this client's video transport. UDP carries the
     * frames once negotiated; TCP stays up for control and fallback.
     */
    fun requestTransport(useUdp: Boolean) {
        sendOpcode(
            if (useUdp) OPCODE_TRANSPORT_UDP else OPCODE_TRANSPORT_TCP,
            if (useUdp) "TRANSPORT_UDP" else "TRANSPORT_TCP"
        )
    }

    private fun sendOpcode(opcode: Int, name: String) {
        val out = synchronized(this) { outStream }
        val executor = sendExecutor ?: return
        if (executor.isShutdown) return
        try {
            executor.execute {
                try {
                    out?.let {
                        it.writeByte(opcode)
                        it.flush()
                        Logger.i(TAG, "Sent $name to Cemu video server")
                    }
                } catch (e: Exception) {
                    Logger.w(TAG, "Failed to send $name: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Logger.w(TAG, "Failed to dispatch $name: ${e.message}")
        }
    }

    private fun runClientLoop() {
        try {
            while (isRunning.get()) {
                try {
                    Logger.i(TAG, "Connecting to Cemu video stream at $host:$port...")
                    val sock = Socket()
                    sock.tcpNoDelay = true
                    sock.soTimeout = READ_TIMEOUT_MS
                    sock.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)

                    synchronized(this) {
                        socket = sock
                        outStream = DataOutputStream(BufferedOutputStream(sock.getOutputStream()))
                    }

                    Logger.i(TAG, "Connected to Cemu video server at $host:$port")
                    onConnected?.invoke()

                    // Request initial IDR keyframe immediately upon connection
                    requestIDR()

                    val inStream = DataInputStream(BufferedInputStream(sock.getInputStream(), 64 * 1024))
                    val headerBuf = ByteArray(13) // 1 (type) + 4 (size) + 8 (pts)

                    while (isRunning.get()) {
                        try {
                            inStream.readFully(headerBuf)
                            val headerWrap = ByteBuffer.wrap(headerBuf).order(ByteOrder.LITTLE_ENDIAN)
                            val packetType = headerWrap.get().toInt() and 0xFF
                            val payloadSize = headerWrap.getInt()
                            val ptsUs = headerWrap.getLong()

                            if (payloadSize <= 0 || payloadSize > 4 * 1024 * 1024) {
                                Logger.w(TAG, "Invalid payload size: $payloadSize bytes. Reconnecting...")
                                break
                            }

                            val payload = ByteArray(payloadSize)
                            inStream.readFully(payload)

                            if (packetType == PACKET_TYPE_VIDEO) {
                                onFrameReceived(payload, ptsUs)
                            }
                        } catch (e: SocketTimeoutException) {
                            if (idleControlMode) {
                                continue
                            }
                            throw e
                        }
                    }
                } catch (e: InterruptedException) {
                    // stop() requested: exit only when no longer supposed to run,
                    // otherwise keep retrying (spurious wakeup safety).
                    if (!isRunning.get()) throw e
                    Logger.w(TAG, "Video worker interrupted while running; continuing")
                } catch (e: Exception) {
                    if (isRunning.get()) {
                        Logger.w(TAG, "Video stream error/disconnect: ${e.message}. Retrying in 1.5s...")
                        onError?.invoke(e)
                    }
                } catch (t: Throwable) {
                    // Never let the retry loop die silently: an uncaught throwable
                    // here used to strand the client with isRunning true forever.
                    Logger.e(TAG, "Fatal error in video worker; retrying in 1.5s", t)
                } finally {
                    closeSocket()
                    onDisconnected?.invoke()
                    if (isRunning.get()) {
                        try {
                            Thread.sleep(1500)
                        } catch (ie: InterruptedException) {
                            if (!isRunning.get()) throw ie
                            Logger.w(TAG, "Video worker sleep interrupted while running; continuing")
                        }
                    }
                }
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            if (isRunning.get()) {
                Logger.e(TAG, "Video worker loop exited while still supposed to run")
            } else {
                Logger.i(TAG, "Video client loop terminated.")
            }
        }
    }

    private fun closeSocket() {
        // Snapshot under lock, close outside it: close() must never wait
        // behind a stuck sender, or the retry loop parks here forever.
        val s: Socket?
        val o: DataOutputStream?
        synchronized(this) {
            s = socket
            o = outStream
            socket = null
            outStream = null
        }
        try {
            o?.close()
        } catch (_: Exception) {
        }
        try {
            s?.close()
        } catch (_: Exception) {
        }
    }
}
