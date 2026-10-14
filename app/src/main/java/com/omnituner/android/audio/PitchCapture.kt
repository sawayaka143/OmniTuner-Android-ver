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
import com.omnituner.core.audio.PitchSmoother
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

    private val smoother = PitchSmoother()
    private val detector = PitchDetector()
    private val highpass = BiquadFilter()
    private val lowpass = BiquadFilter()

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

        highpass.setHighpass(SAMPLE_RATE.toDouble(), 38.0, 0.7)
        lowpass.setLowpass(SAMPLE_RATE.toDouble(), 1250.0, 0.7)
        highpass.reset()
        lowpass.reset()
        smoother.reset()
        smoother.markListening()

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
        smoother.reset()
        smoother.markIdle()
    }

    private fun captureLoop(active: AudioRecord, onFrame: (PitchFrame) -> Unit) {
        val ring = FloatArray(WINDOW_SIZE)
        val shortChunk = ShortArray(READ_CHUNK)
        val chunk = FloatArray(READ_CHUNK)
        val filtered = FloatArray(READ_CHUNK)
        var ringWrite = 0
        var ringFilled = 0
        var lastAnalysisAt = 0L

        while (running) {
            val read = active.read(shortChunk, 0, READ_CHUNK)
            if (read < 0) break
            if (read == 0) continue

            for (i in 0 until read) chunk[i] = shortChunk[i] / 32768f
            highpass.process(chunk, filtered, read)
            lowpass.process(filtered, chunk, read)
            for (i in 0 until read) {
                ring[ringWrite] = chunk[i]
                ringWrite = (ringWrite + 1) % WINDOW_SIZE
            }
            ringFilled = minOf(WINDOW_SIZE, ringFilled + read)

            val now = System.currentTimeMillis()
            if (ringFilled == WINDOW_SIZE && now - lastAnalysisAt >= ANALYSIS_INTERVAL_MS) {
                val window = FloatArray(WINDOW_SIZE) { ring[(ringWrite + it) % WINDOW_SIZE] }
                val estimate = detector.analyse(window, SAMPLE_RATE.toDouble())
                smoother.onAnalysisResult(estimate.frequency, estimate.confidence, estimate.inputLevel)
                onFrame(PitchFrame(smoother.frequency, estimate.inputLevel, smoother.trackingState))
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
    }
}
