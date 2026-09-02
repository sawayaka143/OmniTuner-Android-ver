package com.omnituner.android.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.os.Process
import android.util.Log
import com.omnituner.core.audio.BiquadFilter
import com.omnituner.core.audio.PitchDetector
import com.omnituner.core.audio.PitchSmoother
import com.omnituner.core.audio.PitchTrackingState
import java.util.concurrent.atomic.AtomicInteger

class AudioCaptureEngine(private val context: Context) {

    fun interface Listener {
        fun onAnalysis(frequency: Double?, inputLevel: Double, trackingState: PitchTrackingState)
    }

    private val smoother = PitchSmoother()
    private val detector = PitchDetector()

    @Volatile
    private var running = false

    private val generation = AtomicInteger(0)
    private var thread: Thread? = null
    private var record: AudioRecord? = null

    private val highpass = BiquadFilter()
    private val lowpass = BiquadFilter()
    private val filterScratch = FloatArray(READ_CHUNK)

    @SuppressLint("MissingPermission")
    fun start(listener: Listener): String? {
        if (running) return null

        val audioSource = if (unprocessedSupported()) {
            android.media.MediaRecorder.AudioSource.UNPROCESSED
        } else {
            android.media.MediaRecorder.AudioSource.MIC
        }

        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            return "Microphone initialization failed."
        }

        val bufferSize = maxOf(minBuffer * 2, WINDOW_SIZE * 2)
        val newRecord = try {
            AudioRecord(
                audioSource,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
            )
        } catch (_: SecurityException) {
            return "Microphone permission is required."
        } catch (_: IllegalArgumentException) {
            return "Microphone initialization failed."
        }

        if (newRecord.state != AudioRecord.STATE_INITIALIZED) {
            newRecord.release()
            return "Microphone is unavailable."
        }

        highpass.setHighpass(SAMPLE_RATE.toDouble(), 38.0, 0.7)
        lowpass.setLowpass(SAMPLE_RATE.toDouble(), 1250.0, 0.7)
        highpass.reset()
        lowpass.reset()

        smoother.reset()
        smoother.markListening()

        val myGeneration = generation.incrementAndGet()
        this.record = newRecord
        running = true
        newRecord.startRecording()

        val worker = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            captureLoop(newRecord, myGeneration, listener)
        }, "omnituner-capture")
        this.thread = worker
        worker.start()
        return null
    }

    fun stop() {
        if (!running) return
        running = false
        generation.incrementAndGet()
        thread?.let { current ->
            try {
                current.join(JOIN_TIMEOUT_MS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        thread = null
        record?.let { current ->
            try {
                current.stop()
            } catch (_: IllegalStateException) {
            }
            current.release()
        }
        record = null
        smoother.reset()
        smoother.markIdle()
    }

    private fun captureLoop(record: AudioRecord, myGeneration: Int, listener: Listener) {
        val ring = FloatArray(WINDOW_SIZE)
        var ringWrite = 0
        var ringFilled = 0
        val shortChunk = ShortArray(READ_CHUNK)
        val chunkFloat = FloatArray(READ_CHUNK)
        var lastAnalysisAt = 0L

        while (running && generation.get() == myGeneration) {
            val now = System.currentTimeMillis()

            val read = record.read(shortChunk, 0, READ_CHUNK)
            if (read > 0) {
                for (i in 0 until read) {
                    chunkFloat[i] = shortChunk[i] / 32768.0f
                }
                highpass.process(chunkFloat, filterScratch, read)
                lowpass.process(filterScratch, chunkFloat, read)
                for (i in 0 until read) {
                    ring[ringWrite] = chunkFloat[i]
                    ringWrite = (ringWrite + 1) % WINDOW_SIZE
                }
                ringFilled = minOf(WINDOW_SIZE, ringFilled + read)
            }

            val filled = ringFilled == WINDOW_SIZE
            if (filled && now - lastAnalysisAt >= ANALYSIS_INTERVAL_MS) {
                val window = FloatArray(WINDOW_SIZE)
                for (i in 0 until WINDOW_SIZE) {
                    window[i] = ring[(ringWrite + i) % WINDOW_SIZE]
                }

                val estimate = detector.analyse(window, SAMPLE_RATE.toDouble())
                smoother.onAnalysisResult(estimate.frequency, estimate.confidence, estimate.inputLevel)
                listener.onAnalysis(smoother.frequency, estimate.inputLevel, smoother.trackingState)

                lastAnalysisAt = now
            } else if (!filled || read <= 0) {
                try {
                    Thread.sleep(SLEEP_WHEN_IDLE_MS)
                } catch (_: InterruptedException) {
                    return
                }
            }
        }
    }

    private fun unprocessedSupported(): Boolean {
        return try {
            val audioManager =
                context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val property = audioManager?.getProperty(
                AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED,
            )
            property != "false"
        } catch (_: Throwable) {
            true
        }
    }

    companion object {
        private const val TAG = "AudioCaptureEngine"

        const val SAMPLE_RATE = 48000
        const val WINDOW_SIZE = 8192
        const val ANALYSIS_INTERVAL_MS = 45L
        const val READ_CHUNK = 2048
        const val SLEEP_WHEN_IDLE_MS = 4L
        const val JOIN_TIMEOUT_MS = 500L
    }
}
