package com.omnituner.android.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import com.omnituner.core.audio.BiquadFilter
import com.omnituner.core.audio.PitchDetector
import com.omnituner.core.audio.PitchEngine
import com.omnituner.core.audio.PitchTrackingState

data class PitchFrame(
    val frequency: Double?,
    val inputLevel: Double,
    val tracking: PitchTrackingState,
) {
    companion object {
        val Silent = PitchFrame(null, 0.0, PitchTrackingState.IDLE)
    }
}

class PitchCapture(private val context: Context) {

    private val engine = PitchEngine()

    // 4th-order Butterworth high-pass (two cascaded biquads) and a 2nd-order low-pass.
    private val highpassA = BiquadFilter()
    private val highpassB = BiquadFilter()
    private val lowpass = BiquadFilter()

    // Lowest string of the current tuning; sets the detection floor and the high-pass cutoff.
    @Volatile
    private var lowestFrequency: Double? = null

    @Volatile
    private var running = false
    private var worker: Thread? = null
    private var recorder: AudioRecord? = null

    @SuppressLint("MissingPermission")
    fun start(onFrame: (PitchFrame) -> Unit): String? {
        if (running) return null

        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) return "Microphone initialization failed."

        val source = if (unprocessedSupported()) {
            MediaRecorder.AudioSource.UNPROCESSED
        } else {
            MediaRecorder.AudioSource.MIC
        }
        val bufferSize = maxOf(minBuffer * 2, WINDOW_SIZE * 2)
        val created = try {
            AudioRecord(source, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
        } catch (_: SecurityException) {
            return "Microphone permission is required."
        } catch (_: IllegalArgumentException) {
            return "Microphone initialization failed."
        }
        if (created.state != AudioRecord.STATE_INITIALIZED) {
            created.release()
            return "Microphone is unavailable."
        }

        applyFilters(lowestFrequency)
        highpassA.reset()
        highpassB.reset()
        lowpass.reset()
        engine.start()

        recorder = created
        running = true
        created.startRecording()
        worker = Thread(
            {
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                captureLoop(created, onFrame)
            },
            "omnituner-capture",
        ).also { it.start() }
        return null
    }

    fun stop() {
        if (!running) return
        running = false
        worker?.join(JOIN_TIMEOUT_MS)
        worker = null
        recorder?.let { active ->
            try {
                active.stop()
            } catch (_: IllegalStateException) {
            }
            active.release()
        }
        recorder = null
        engine.stop()
    }

    /** Retunes the detection range for a new tuning; safe to call while capturing. */
    fun setLowestFrequency(hz: Double?) {
        lowestFrequency = hz
    }

    private fun applyFilters(lowest: Double?) {
        val rate = SAMPLE_RATE.toDouble()
        val cutoff = PitchDetector.highpassHzFor(lowest)
        highpassA.setHighpassLinear(rate, cutoff, HIGHPASS_Q_A)
        highpassB.setHighpassLinear(rate, cutoff, HIGHPASS_Q_B)
        lowpass.setLowpassLinear(rate, LOWPASS_HZ, LOWPASS_Q)
    }

    private fun captureLoop(active: AudioRecord, onFrame: (PitchFrame) -> Unit) {
        val ring = FloatArray(WINDOW_SIZE)
        val shortChunk = ShortArray(READ_CHUNK)
        val chunk = FloatArray(READ_CHUNK)
        val filtered = FloatArray(READ_CHUNK)
        var ringWrite = 0
        var ringFilled = 0
        var lastAnalysisAt = 0L
        var appliedLowest = lowestFrequency

        while (running) {
            val read = active.read(shortChunk, 0, READ_CHUNK)
            if (read < 0) break
            if (read == 0) continue

            val lowest = lowestFrequency
            if (lowest != appliedLowest) {
                applyFilters(lowest)
                appliedLowest = lowest
            }

            for (i in 0 until read) chunk[i] = shortChunk[i] / 32768f
            highpassA.process(chunk, filtered, read)
            highpassB.process(filtered, chunk, read)
            lowpass.process(chunk, filtered, read)
            System.arraycopy(filtered, 0, chunk, 0, read)
            for (i in 0 until read) {
                ring[ringWrite] = chunk[i]
                ringWrite = (ringWrite + 1) % WINDOW_SIZE
            }
            ringFilled = minOf(WINDOW_SIZE, ringFilled + read)

            val now = System.currentTimeMillis()
            if (ringFilled == WINDOW_SIZE && now - lastAnalysisAt >= ANALYSIS_INTERVAL_MS) {
                val window = FloatArray(WINDOW_SIZE) { ring[(ringWrite + it) % WINDOW_SIZE] }
                val estimate = engine.process(window, SAMPLE_RATE.toDouble(), PitchDetector.minFrequencyFor(lowest))
                onFrame(PitchFrame(engine.frequency, estimate.inputLevel, engine.trackingState))
                lastAnalysisAt = now
            }
        }
    }

    private fun unprocessedSupported(): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val property = audioManager?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)
        return property != "false"
    }

    private companion object {
        const val SAMPLE_RATE = 48000
        const val WINDOW_SIZE = 8192
        const val READ_CHUNK = 2048
        const val ANALYSIS_INTERVAL_MS = 45L
        const val JOIN_TIMEOUT_MS = 500L
        const val HIGHPASS_Q_A = 0.5412
        const val HIGHPASS_Q_B = 1.3066
        const val LOWPASS_HZ = 1800.0
        const val LOWPASS_Q = 0.7
    }
}
