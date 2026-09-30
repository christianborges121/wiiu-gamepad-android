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
    private val onFrameReceived: (nalData: ByteArray, ptsUs: Long) -> Unit,
    private val getAuthCredential: () -> Long = { 0L },
    private val onAuthToken: (token: Long) -> Unit = {},
    private val onPinRequired: () -> Unit = {},
    private val onAuthFailed: ((String) -> Unit)? = null,
    private val onAuthSucceeded: (() -> Unit)? = null,
    private val onAuthCancelled: (() -> Unit)? = null
) {
    companion object {
        const val TAG = "VideoStreamClient"
        const val DEFAULT_PORT = 26761
        const val PACKET_TYPE_VIDEO = 0x01
        const val PACKET_TYPE_CONFIG = 0x02
        const val PACKET_TYPE_RUMBLE = 0x03
        const val OPCODE_IDR_REQUEST = 0x10
        const val OPCODE_TRANSPORT_UDP = 0x11
        const val OPCODE_TRANSPORT_TCP = 0x12
        const val OPCODE_MIC_BLOW = 0x13
        const val OPCODE_SET_BITRATE = 0x14
        const val OPCODE_SET_RESOLUTION = 0x15
        const val OPCODE_CODEC_SELECT = 0x16
        const val OPCODE_STATS_REPORT = 0x17
        const val OPCODE_AUTH_REQUEST = 0x30
        const val AUTH_STATUS_OK = 0x00
        private const val CONNECT_TIMEOUT_MS = 5000
        private const val READ_TIMEOUT_MS = 15000
        private const val AUTH_TIMEOUT_MS = 8000
        private const val PIN_WAIT_SECONDS = 90L

        const val OPCODE_PUSH_MAPPINGS = 0x18
        const val OPCODE_SET_MAPPING = 0x19

        /**
         * Builds the 2-byte CODEC_SELECT packet: [0x16][uint8 codec] (0 = H.264, 1 = HEVC).
         * Pure function for unit tests.
         */
        fun buildCodecSelectPacket(useHevc: Boolean): ByteArray {
            return byteArrayOf(OPCODE_CODEC_SELECT.toByte(), if (useHevc) 1.toByte() else 0.toByte())
        }

        fun buildPushMappingsPacket(entries: List<Pair<Int, Int>>): ByteArray {
            require(entries.isNotEmpty() && entries.size <= 32)
            val buf = ByteBuffer.allocate(1 + 1 + entries.size * 8).order(ByteOrder.LITTLE_ENDIAN)
            buf.put(OPCODE_PUSH_MAPPINGS.toByte())
            buf.put(entries.size.toByte())
            for ((mapping, button) in entries) {
                buf.putInt(mapping)
                buf.putInt(button)
            }
            return buf.array()
        }

        fun buildSetMappingPacket(mapping: Int, button: Int, applyNow: Boolean = true): ByteArray {
            val buf = ByteBuffer.allocate(1 + 4 + 4 + 1).order(ByteOrder.LITTLE_ENDIAN)
            buf.put(OPCODE_SET_MAPPING.toByte())
            buf.putInt(mapping)
            buf.putInt(button)
            buf.put(if (applyNow) 1 else 0)
            return buf.array()
        }

        /**
         * Builds the 9-byte STATS_REPORT packet: [0x17][uint16 loss LE][uint16 drop LE][uint16 rtt LE][uint16 flags LE].
         * Pure function for unit tests.
         */
        fun buildStatsReportPacket(lossHundredths: Int, dropHundredths: Int, rttMs: Int = 0, flags: Int = 0): ByteArray {
            val packet = ByteArray(9)
            packet[0] = OPCODE_STATS_REPORT.toByte()
            packet[1] = (lossHundredths and 0xFF).toByte()
            packet[2] = ((lossHundredths shr 8) and 0xFF).toByte()
            packet[3] = (dropHundredths and 0xFF).toByte()
            packet[4] = ((dropHundredths shr 8) and 0xFF).toByte()
            packet[5] = (rttMs and 0xFF).toByte()
            packet[6] = ((rttMs shr 8) and 0xFF).toByte()
            packet[7] = (flags and 0xFF).toByte()
            packet[8] = ((flags shr 8) and 0xFF).toByte()
            return packet
        }

        /**
         * Builds the 9-byte AUTH_REQUEST packet: [0x30][uint64 credential LE].
         * Pure function for unit tests.
         */
        fun buildAuthRequest(credential: Long): ByteArray {
            return ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN).apply {
                put(OPCODE_AUTH_REQUEST.toByte())
                putLong(credential)
            }.array()
        }

        /**
         * Parses the 9-byte AUTH_RESPONSE into (success, token), or null when
         * the buffer is too short. Pure function for unit tests.
         */
        fun parseAuthResponse(data: ByteArray): Pair<Boolean, Long>? {
            if (data.size < 9) return null
            val buf = ByteBuffer.wrap(data, 0, 9).order(ByteOrder.LITTLE_ENDIAN)
            val status = buf.get().toInt() and 0xFF
            val token = buf.long
            return Pair(status == AUTH_STATUS_OK, token)
        }

        /**
         * Builds the 5-byte little-endian SET_BITRATE packet: [0x14][uint32 bps].
         * Pure function so unit tests can verify the wire format without sockets.
         */
        fun buildBitratePacket(bitrateBps: Int): ByteArray {
            return ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN).apply {
                put(OPCODE_SET_BITRATE.toByte())
                putInt(bitrateBps)
            }.array()
        }

        /**
         * Builds the 5-byte little-endian SET_RESOLUTION packet:
         * [0x15][uint16 width][uint16 height]. Pure function for unit tests.
         */
        fun buildResolutionPacket(width: Int, height: Int): ByteArray {
            return ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN).apply {
                put(OPCODE_SET_RESOLUTION.toByte())
                putShort(width.toShort())
                putShort(height.toShort())
            }.array()
        }
    }

    private val isRunning = AtomicBoolean(false)
    private var workerThread: Thread? = null
    private var sendExecutor: java.util.concurrent.ExecutorService? = null
    private var socket: Socket? = null
    private var outStream: DataOutputStream? = null
    // One-shot handoff for a user-typed PIN while the worker parks on auth.
    // Empty string = user cancelled.
    private val pinQueue = java.util.concurrent.LinkedBlockingQueue<String>(1)

    var onConnected: (() -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null
    var onError: ((Throwable) -> Unit)? = null
    var onRumbleReceived: ((active: Boolean, intensity: Int, durationMs: Int) -> Unit)? = null

    private val isAuthenticated = AtomicBoolean(false)

    val isConnected: Boolean
        get() = isRunning.get() && isAuthenticated.get() && socket?.isConnected == true && socket?.isClosed == false

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
        isAuthenticated.set(false)
        pinQueue.offer("")
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

    /**
     * Sends microphone blow state to Cemu.
     */
    fun sendMicBlow(isBlowing: Boolean) {
        val out = synchronized(this) { outStream }
        val executor = sendExecutor ?: return
        if (executor.isShutdown) return
        try {
            executor.execute {
                try {
                    out?.let {
                        it.writeByte(OPCODE_MIC_BLOW)
                        it.writeByte(if (isBlowing) 1 else 0)
                        it.flush()
                        Logger.v(TAG, "Sent MIC_BLOW state=$isBlowing to Cemu")
                    }
                } catch (e: Exception) {
                    Logger.w(TAG, "Failed to send MIC_BLOW: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Logger.w(TAG, "Failed to dispatch MIC_BLOW: ${e.message}")
        }
    }

    /**
     * Asks Cemu to switch the encoder target bitrate live (e.g. 6000000 = 6 Mbps).
     */
    fun sendBitrate(bitrateBps: Int) {
        sendControlPacket(buildBitratePacket(bitrateBps), "SET_BITRATE=$bitrateBps")
    }

    /**
     * Asks Cemu to reconfigure the encoder resolution live (e.g. 1280x720).
     * The PC emits an IDR keyframe after reconfiguring so the decoder re-syncs.
     */
    fun sendResolution(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        sendControlPacket(buildResolutionPacket(width, height), "SET_RESOLUTION=${width}x$height")
    }

    /**
     * Asks Cemu to switch host video codec live (false = H.264, true = HEVC).
     * The PC emits parameter sets and an IDR keyframe immediately after switching.
     */
    fun sendCodec(useHevc: Boolean) {
        sendControlPacket(buildCodecSelectPacket(useHevc), "CODEC_SELECT=${if (useHevc) "HEVC" else "H.264"}")
    }

    /**
      * Sends rolling network and frame telemetry to Cemu for adaptive dynamic bitrate adjustment.
      */
    fun sendStatsReport(lossHundredths: Int, dropHundredths: Int, rttMs: Int = 0, flags: Int = 0) {
        sendControlPacket(
            buildStatsReportPacket(lossHundredths, dropHundredths, rttMs, flags),
            "STATS_REPORT(loss=${lossHundredths / 100f}%, drop=${dropHundredths / 100f}%)"
        )
    }

    fun sendPushedMappings(entries: List<Pair<Int, Int>>) {
        if (entries.isEmpty()) return
        // Chunk to 32 per bulk packet
        for (chunk in entries.chunked(32)) {
            sendControlPacket(buildPushMappingsPacket(chunk), "PUSH_MAPPINGS(${chunk.size})")
        }
    }

    fun sendMapping(mapping: Int, button: Int) {
        sendControlPacket(buildSetMappingPacket(mapping, button), "SET_MAPPING($mapping->$button)")
    }

    private fun sendControlPacket(packet: ByteArray, name: String) {
        val out = synchronized(this) { outStream }
        val executor = sendExecutor ?: return
        if (executor.isShutdown) return
        try {
            executor.execute {
                try {
                    out?.let {
                        it.write(packet)
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

    /**
     * Delivers a user-typed 6-digit PIN to a worker parked on auth.
     */
    fun submitPin(pin: String) {
        pinQueue.offer(pin)
    }

    /**
     * Aborts a parked PIN wait (user cancelled the dialog).
     */
    fun cancelAuth() {
        pinQueue.offer("")
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

    enum class AuthOutcome {
        SUCCESS,
        DENIED,
        IO_ERROR
    }

    /**
     * Blocking session authentication, executed on the worker thread BEFORE
     * the frame reader starts (the 9-byte auth response is unframed and must
     * never reach the frame parser). Returns true when streaming may proceed.
     */
    private fun performAuth(
        sock: Socket,
        out: DataOutputStream,
        inp: java.io.DataInputStream
    ): Boolean {
        val initialCred = getAuthCredential()
        val initialOutcome = exchangeAuth(out, inp, initialCred)
        when (initialOutcome) {
            AuthOutcome.SUCCESS -> {
                onAuthSucceeded?.invoke()
                return true
            }
            AuthOutcome.IO_ERROR -> {
                // Socket / network error (e.g. Cemu closed, broken pipe).
                // Do NOT open PIN modal; return false to reconnect cleanly.
                return false
            }
            AuthOutcome.DENIED -> {
                // Cemu responded and explicitly rejected the credential.
            }
        }

        // Cached credential rejected: Cemu requires the session PIN.
        onPinRequired()
        val deadline = System.currentTimeMillis() + PIN_WAIT_SECONDS * 1000L
        var lastPcPollMs = 0L
        while (System.currentTimeMillis() < deadline && isRunning.get()) {
            val pin = try {
                pinQueue.poll(1, java.util.concurrent.TimeUnit.SECONDS)
            } catch (e: InterruptedException) {
                onAuthCancelled?.invoke()
                return false
            }
            if (sock.isClosed || !sock.isConnected) {
                onAuthCancelled?.invoke()
                return false
            }
            if (pin != null) {
                if (pin.isEmpty()) {
                    Logger.i(TAG, "PIN entry cancelled by user")
                    onAuthCancelled?.invoke()
                    return false
                }
                val pinValue = pin.filter { it.isDigit() }.toLongOrNull()
                if (pinValue == null) {
                    onAuthFailed?.invoke("Invalid PIN format. Enter 6 digits.")
                    continue
                }
                val outcome = exchangeAuth(out, inp, pinValue)
                when (outcome) {
                    AuthOutcome.SUCCESS -> {
                        onAuthSucceeded?.invoke()
                        return true
                    }
                    AuthOutcome.DENIED -> {
                        Logger.w(TAG, "PIN authentication rejected by Cemu")
                        onAuthFailed?.invoke("Incorrect PIN. Please re-enter the PIN shown in Cemu.")
                    }
                    AuthOutcome.IO_ERROR -> {
                        Logger.w(TAG, "Socket error during PIN submission; reconnecting")
                        onAuthCancelled?.invoke()
                        return false
                    }
                }
                continue
            }
            // Check if cached credential changed (e.g. fresh token saved)
            val currentCred = getAuthCredential()
            if (currentCred != 0L && currentCred != initialCred) {
                val outcome = exchangeAuth(out, inp, currentCred)
                if (outcome == AuthOutcome.SUCCESS) {
                    Logger.i(TAG, "New cached credential accepted while PIN prompt was open; auto-reconnected")
                    onAuthSucceeded?.invoke()
                    return true
                } else if (outcome == AuthOutcome.IO_ERROR) {
                    onAuthCancelled?.invoke()
                    return false
                }
            }
            // Check if Cemu authorized this device via "Allow Connection" on PC.
            // Throttled to every 3 seconds to reduce protocol overhead and
            // avoid flooding Cemu's TriggerPairingPrompt rate-limiter.
            val nowMs = System.currentTimeMillis()
            if (nowMs - lastPcPollMs >= 3000L) {
                lastPcPollMs = nowMs
                val pcOutcome = exchangeAuth(out, inp, 0L)
                if (pcOutcome == AuthOutcome.SUCCESS) {
                    Logger.i(TAG, "Cemu approved connection from PC dialog; auto-reconnected")
                    onAuthSucceeded?.invoke()
                    return true
                } else if (pcOutcome == AuthOutcome.IO_ERROR) {
                    Logger.w(TAG, "Socket disconnected while waiting for pairing; reconnecting")
                    onAuthCancelled?.invoke()
                    return false
                }
            }
        }
        Logger.i(TAG, "PIN entry cancelled or timed out; aborting connection")
        onAuthCancelled?.invoke()
        return false
    }

    private fun exchangeAuth(
        out: DataOutputStream,
        inp: java.io.DataInputStream,
        credential: Long
    ): AuthOutcome {
        return try {
            out.write(buildAuthRequest(credential))
            out.flush()
            val response = ByteArray(9)
            inp.readFully(response)
            val (ok, token) = parseAuthResponse(response) ?: return AuthOutcome.IO_ERROR
            if (ok) {
                if (token != 0L) onAuthToken(token)
                Logger.i(TAG, "Session authenticated with Cemu")
                AuthOutcome.SUCCESS
            } else {
                Logger.w(TAG, "Session authentication denied by Cemu")
                AuthOutcome.DENIED
            }
        } catch (e: Exception) {
            Logger.w(TAG, "Session authentication failed: ${e.message}")
            AuthOutcome.IO_ERROR
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

                    val inStream = DataInputStream(BufferedInputStream(sock.getInputStream(), 64 * 1024))

                    // Authenticate before any framed traffic: the 9-byte auth
                    // response is unframed and must precede the frame reader.
                    // A short timeout keeps a dead peer from hanging connect.
                    sock.soTimeout = AUTH_TIMEOUT_MS
                    val out = synchronized(this) { outStream }
                    if (out == null) {
                        closeSocket()
                        Thread.sleep(1500)
                        continue
                    }
                    if (!performAuth(sock, out, inStream)) {
                        closeSocket()
                        Thread.sleep(1500)
                        continue
                    }
                    isAuthenticated.set(true)
                    pinQueue.clear() // drop any late PIN submissions
                    sock.soTimeout = READ_TIMEOUT_MS

                    Logger.i(TAG, "Connected to Cemu video server at $host:$port")
                    onConnected?.invoke()

                    // Request initial IDR keyframe immediately upon connection
                    requestIDR()

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

                            when (packetType) {
                                PACKET_TYPE_VIDEO -> onFrameReceived(payload, ptsUs)
                                PACKET_TYPE_RUMBLE -> {
                                    if (payload.size >= 4) {
                                        val active = payload[0] != 0.toByte()
                                        val intensity = payload[1].toInt() and 0xFF
                                        val durationMs = (payload[2].toInt() and 0xFF) or ((payload[3].toInt() and 0xFF) shl 8)
                                        onRumbleReceived?.invoke(active, intensity, durationMs)
                                    }
                                }
                            }
                        } catch (e: SocketTimeoutException) {
                            // Cemu is idle (e.g. no title loaded yet, loading level, or video is on UDP).
                            // A read timeout is normal and must not tear down the connection.
                            if (!isRunning.get() || socket?.isClosed == true || socket?.isConnected == false) {
                                throw e
                            }
                            continue
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
        isAuthenticated.set(false)
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
