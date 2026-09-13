package com.cemupad.video

import com.cemupad.util.Logger
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * UDP video receiver (protocol v1). Listens on [port], reassembles
 * fragmented frames, and delivers complete Annex B payloads that are
 * byte-identical to the TCP framing.
 *
 * Runs its own worker thread; all reassembly state lives there. Calls
 * [onFrameReceived] for complete frames and [onUdpSilence] when no
 * complete frame arrives within [silenceTimeoutMs], so the owner can
 * fall back to TCP.
 */
class UdpVideoReceiver(
    var port: Int = DEFAULT_PORT,
    private val onFrameReceived: (nalData: ByteArray, ptsUs: Long) -> Unit,
    private val silenceTimeoutMs: Long = 2000L
) {
    companion object {
        const val TAG = "UdpVideoReceiver"
        const val DEFAULT_PORT = 26761
    }

    private val isRunning = AtomicBoolean(false)
    private var workerThread: Thread? = null
    private var socket: DatagramSocket? = null
    private val reassembler = FrameReassembler()
    private val sequencer = UdpFrameSequencer()
    private var lastIdrRequestMs = 0L
    @Volatile var isExpectingUdp = false
    @Volatile private var silenceReported = false
    @Volatile private var lastCompleteMs = 0L

    var onUdpSilence: (() -> Unit)? = null
    var onRequestIdr: (() -> Unit)? = null
    var onError: ((Throwable) -> Unit)? = null

    /** Resets reassembly + ordering for a fresh burst (transport switch). */
    fun resetStream() {
        sequencer.onTransportStart()
        reassembler.reset()
        lastIdrRequestMs = 0L
        silenceReported = false
        lastCompleteMs = System.currentTimeMillis()
        isExpectingUdp = true
    }

    val stats get() = reassembler.stats

    fun start() {
        if (isRunning.getAndSet(true)) return
        resetStream()
        workerThread = Thread({
            runReceiveLoop()
        }, "CemuPad-UdpVideo").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        isExpectingUdp = false
        if (!isRunning.getAndSet(false)) return
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        workerThread?.interrupt()
        workerThread = null
        socket = null
    }

    fun isWorkerAlive(): Boolean = workerThread?.isAlive == true

    fun restartIfStalled(): Boolean {
        if (!isRunning.get() || isWorkerAlive()) return false
        Logger.w(TAG, "UDP worker dead while supposed to run; restarting")
        workerThread = Thread({
            runReceiveLoop()
        }, "CemuPad-UdpVideo").apply {
            isDaemon = true
            start()
        }
        return true
    }

    private fun runReceiveLoop() {
        try {
            val sock: DatagramSocket
            try {
                sock = DatagramSocket(port).apply {
                    reuseAddress = true
                    soTimeout = 500
                    try {
                        receiveBufferSize = 2 * 1024 * 1024
                    } catch (e: Exception) {
                        Logger.w(TAG, "Failed to set UDP receiveBufferSize: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to bind UDP port $port", e)
                isRunning.set(false)
                return
            }
            socket = sock
            Logger.i(TAG, "UDP video listening on 0.0.0.0:$port")
            lastCompleteMs = System.currentTimeMillis()
            silenceReported = false
            val buf = ByteArray(UdpVideoPacket.HEADER_SIZE + UdpVideoPacket.MAX_PAYLOAD + 64)
            val packet = DatagramPacket(buf, buf.size)

            while (isRunning.get()) {
                try {
                    packet.length = buf.size
                    sock.receive(packet)
                    for (out in sequencer.onOffer(reassembler.offer(buf, packet.length))) {
                        when (out) {
                            is UdpFrameSequencer.Out.Feed -> {
                                lastCompleteMs = System.currentTimeMillis()
                                onFrameReceived(out.data, out.ptsUs)
                            }
                            UdpFrameSequencer.Out.NeedsIdr -> {
                                val now = System.currentTimeMillis()
                                if (now - lastIdrRequestMs >= 1000L) {
                                    lastIdrRequestMs = now
                                    Logger.w(TAG, "UDP frame loss; requesting IDR resync")
                                    onRequestIdr?.invoke()
                                }
                            }
                        }
                    }
                } catch (e: SocketTimeoutException) {
                    // Routine: no datagram within 500 ms. Silence handling below.
                } catch (e: SocketException) {
                    if (!isRunning.get()) break
                    Logger.e(TAG, "SocketException in UDP receive loop", e)
                } catch (e: Exception) {
                    if (!isRunning.get()) break
                    Logger.e(TAG, "Error in UDP receive loop", e)
                } catch (t: Throwable) {
                    Logger.e(TAG, "Fatal error in UDP receive loop", t)
                    if (!isRunning.get()) break
                }
                // Silence is checked every iteration, but only when actively expecting UDP:
                // a steady trickle of uncompletable datagrams must still trip the TCP
                // fallback instead of stalling silently.
                if (isExpectingUdp && !silenceReported &&
                    System.currentTimeMillis() - lastCompleteMs > silenceTimeoutMs
                ) {
                    silenceReported = true
                    val s = reassembler.stats
                    Logger.w(
                        TAG,
                        "No complete UDP frame for ${silenceTimeoutMs}ms " +
                            "(rx=${s.datagramsReceived} malformed=${s.datagramsMalformed} " +
                            "done=${s.framesCompleted} dropped=${s.framesDropped} " +
                            "lost=${s.packetsLost})"
                    )
                    onUdpSilence?.invoke()
                }
            }
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {
            }
            socket = null
            if (isRunning.get()) {
                Logger.e(TAG, "UDP worker loop exited while still supposed to run")
            } else {
                Logger.i(TAG, "UDP receiver loop terminated.")
            }
        }
    }
}
