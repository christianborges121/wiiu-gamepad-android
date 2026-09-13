package com.cemupad.audio

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.cemupad.util.Logger
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/**
 * Real-time microphone blow detector for Wii U GamePad emulation.
 * Samples audio via AudioRecord, calculates RMS acoustic energy, and
 * detects blowing into the microphone with hysteresis.
 */
class MicBlowDetector(
    private val context: Context,
    private val onBlowStateChanged: (Boolean) -> Unit
) {
    companion object {
        private const val TAG = "MicBlowDetector"
        private const val SAMPLE_RATE = 16000
        private const val BLOW_THRESHOLD_RMS = 2800.0
        private const val RELEASE_THRESHOLD_RMS = 1800.0
        private const val BLOW_HOLD_MS = 250L
    }

    private val isRunning = AtomicBoolean(false)
    private var recordThread: Thread? = null
    private var manualBlow = false
    private var lastBlowTime = 0L

    @Volatile
    var isBlowActive = false
        private set

    /**
     * Manually asserts or releases the mic blow state (e.g. from an on-screen button).
     */
    fun setManualBlow(active: Boolean) {
        manualBlow = active
        updateBlowState(active || (System.currentTimeMillis() - lastBlowTime < BLOW_HOLD_MS))
    }

    fun start() {
        if (isRunning.getAndSet(true)) return

        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            Logger.w(TAG, "RECORD_AUDIO permission not granted; blow detector running manual only")
            return
        }

        recordThread = Thread({
            runDetectorLoop()
        }, "CemuPad-MicDetector").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        isRunning.set(false)
        recordThread?.interrupt()
        recordThread = null
        updateBlowState(false)
    }

    @SuppressLint("MissingPermission")
    private fun runDetectorLoop() {
        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(1024)

        var audioRecord: AudioRecord? = null
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                Logger.w(TAG, "Failed to initialize AudioRecord")
                return
            }

            audioRecord.startRecording()
            Logger.i(TAG, "Mic blow detector recording active")

            val audioBuffer = ShortArray(bufferSize / 2)
            var currentBlow = false

            while (isRunning.get()) {
                val readSamples = audioRecord.read(audioBuffer, 0, audioBuffer.size)
                if (readSamples <= 0) continue

                // Compute Root Mean Square (RMS) energy
                var sumSq = 0.0
                for (i in 0 until readSamples) {
                    val s = audioBuffer[i].toDouble()
                    sumSq += s * s
                }
                val rms = sqrt(sumSq / readSamples)
                val now = System.currentTimeMillis()

                if (rms >= BLOW_THRESHOLD_RMS) {
                    lastBlowTime = now
                    currentBlow = true
                } else if (rms < RELEASE_THRESHOLD_RMS && (now - lastBlowTime >= BLOW_HOLD_MS)) {
                    currentBlow = false
                }

                updateBlowState(currentBlow || manualBlow)
            }
        } catch (e: InterruptedException) {
            // Shutting down
        } catch (e: Exception) {
            Logger.w(TAG, "Mic detector error: ${e.message}")
        } finally {
            try {
                audioRecord?.stop()
                audioRecord?.release()
            } catch (ignored: Exception) {}
            Logger.i(TAG, "Mic blow detector stopped")
        }
    }

    private fun updateBlowState(active: Boolean) {
        if (isBlowActive != active) {
            isBlowActive = active
            onBlowStateChanged(active)
        }
    }
}
