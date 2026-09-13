package com.cemupad.video

/**
 * Orders reassembled UDP frames for a stateful H.264 decoder.
 *
 * Feeding P-frames out of order (or after a gap) corrupts the decoder's
 * reference chain into smear/ghosting until the next IDR, so this buffer
 * only releases frames in frameId order: it holds newer completes, drops
 * anything already fed, and on any loss in the unfed region it flushes
 * and holds everything until the next IDR resyncs the chain.
 *
 * All arithmetic is modulo 2^32 (frameIds wrap).
 */
class UdpFrameSequencer(private val maxHold: Int = 8) {

    sealed interface Out {
        data class Feed(val data: ByteArray, val ptsUs: Long) : Out
        data object NeedsIdr : Out
    }

    private var expected: Long? = null
    private var needIdrSync = false
    private var lastFed: Long = -1L
    private val held = LinkedHashMap<Long, FrameReassembler.CompletedFrame>()

    /** Call when a fresh UDP burst begins (negotiated transport switch). */
    fun onTransportStart() {
        expected = null
        needIdrSync = false
        held.clear()
    }

    fun onOffer(events: List<FrameReassembler.OfferResult>): List<Out> {
        val out = mutableListOf<Out>()
        for (event in events) {
            when (event) {
                is FrameReassembler.OfferResult.FrameComplete -> handleComplete(event.frame, out)
                is FrameReassembler.OfferResult.FrameDropped -> handleDrop(event, out)
                FrameReassembler.OfferResult.Waiting -> {}
            }
        }
        return out
    }

    private fun handleComplete(
        frame: FrameReassembler.CompletedFrame,
        out: MutableList<Out>
    ) {
        // lastFed < 0 means nothing fed yet: accept any first frame.
        if (lastFed >= 0 && !isNewer(frame.frameId, lastFed)) return // replay or late: ignore
        if (needIdrSync) {
            if (frame.isIdr) {
                needIdrSync = false
                feed(frame, out)
                drainHeld(out)
            }
            return
        }
        val exp = expected
        if (exp == null || frame.frameId == exp) {
            feed(frame, out)
            drainHeld(out)
            return
        }
        if (isNewer(frame.frameId, exp)) {
            if (!held.containsKey(frame.frameId)) {
                if (held.size >= maxHold) {
                    requestResync(out)
                    return
                }
                held[frame.frameId] = frame
            }
        }
        // Older than expected but newer than fed: already covered region, ignore.
    }

    private fun handleDrop(event: FrameReassembler.OfferResult.FrameDropped, out: MutableList<Out>) {
        if (lastFed < 0 || isNewer(event.frameId, lastFed)) {
            requestResync(out)
        }
    }

    private fun requestResync(out: MutableList<Out>) {
        held.clear()
        needIdrSync = true
        expected = null
        out += Out.NeedsIdr
    }

    private fun feed(frame: FrameReassembler.CompletedFrame, out: MutableList<Out>) {
        out += Out.Feed(frame.data, frame.ptsUs)
        lastFed = frame.frameId
        expected = (frame.frameId + 1) and 0xFFFFFFFFL
    }

    private fun drainHeld(out: MutableList<Out>) {
        while (true) {
            val exp = expected ?: break
            val next = held.remove(exp) ?: break
            feed(next, out)
        }
    }

    private fun dist(a: Long, b: Long) = (a - b) and 0xFFFFFFFFL

    private fun isNewer(a: Long, b: Long): Boolean {
        val d = dist(a, b)
        return d in 1..0x7FFFFFFFL
    }
}
