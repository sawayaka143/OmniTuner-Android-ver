package com.omnituner.android.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import com.omnituner.core.metronome.MetronomeScheduler
import com.omnituner.core.metronome.MetronomeState
import com.omnituner.core.metronome.ScheduledSound
import com.omnituner.core.sound.DynamicsCompressor
import com.omnituner.core.sound.MetronomeVoices
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Native metronome engine: the AudioTrack counterpart of metronome-audio.service.
 * A dedicated high-priority thread drives the shared [MetronomeScheduler] with a
 * 25 ms tick, renders due voices to PCM ahead of the playhead (the look-ahead
 * pattern) and streams them into an AudioTrack. Master gain + compressor mirror
 * the web master chain.
 */
class MetronomeAudioEngine {

    fun interface TransportListener {
        fun onTransport(barIndex: Int, patternPos: Int, progress: Double, countIn: Boolean, barActive: Boolean)
    }

    private val scheduler = MetronomeScheduler { clockSeconds() }

    private var track: AudioTrack? = null
    private var thread: Thread? = null

    @Volatile
    private var running = false

    @Volatile
    private var background = false

    @Volatile
    private var transportListener: TransportListener? = null

    private var t0Nanos = 0L
    private var playStartClock = 0.0
    private var writeCursorFrames = 0L

    private val pendingMix = mutableListOf<Pair<Long, FloatArray>>()
    private val compressor = DynamicsCompressor(SAMPLE_RATE.toDouble())
    private val renderRandom = Random(20260901)

    fun configure(state: MetronomeState) {
        scheduler.configure(state)
    }

    fun setTransportListener(listener: TransportListener?) {
        transportListener = listener
    }

    fun setBackground(value: Boolean) {
        background = value
    }

    fun start() {
        if (running) return
        val minBuffer = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        val trackBufferSize = maxOf(minBuffer, 2048 * Float.SIZE_BYTES)

        val newTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(trackBufferSize)
            .build()

        compressor.reset()
        pendingMix.clear()
        writeCursorFrames = 0
        playStartClock = 0.0

        t0Nanos = System.nanoTime()
        scheduler.start(0.0)
        running = true

        newTrack.play()
        // Track frame 0 becomes audible now; clock-to-frame mapping uses this anchor.
        playStartClock = clockSeconds()
        track = newTrack

        val worker = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            engineLoop(newTrack)
        }, "omnituner-metronome")
        this.thread = worker
        worker.start()
    }

    fun stop() {
        if (!running) return
        running = false
        scheduler.stop()
        thread?.let { current ->
            try {
                current.join(JOIN_TIMEOUT_MS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        thread = null
        track?.let { current ->
            try {
                current.pause()
                current.flush()
                current.stop()
            } catch (_: IllegalStateException) {
            }
            current.release()
        }
        track = null
        pendingMix.clear()
        compressor.reset()
    }

    fun previewVoice(id: String, vol: Double) {
        // One-shot preview through a short-lived track.
        val buffer = MetronomeVoices.render(id, SAMPLE_RATE.toDouble(), vol, renderRandom)
        compressor.processInPlace(buffer)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(buffer.size * Float.SIZE_BYTES)
            .build()
        track.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
        track.play()
        Thread {
            try {
                Thread.sleep(buffer.size * 1000L / SAMPLE_RATE + 150)
            } catch (_: InterruptedException) {
            }
            try {
                track.stop()
            } catch (_: IllegalStateException) {
            }
            track.release()
        }.apply { isDaemon = true }.start()
    }

    private fun clockSeconds(): Double = (System.nanoTime() - t0Nanos) / 1_000_000_000.0

    private fun engineLoop(audioTrack: AudioTrack) {
        val masterVol = { scheduler.config.masterVol }
        var lastTransportEmit = 0L

        while (running) {
            val now = clockSeconds()
            val lookahead =
                if (background) MetronomeScheduler.LOOKAHEAD_HIDDEN_S else MetronomeScheduler.LOOKAHEAD_VISIBLE_S

            // Schedule due events and pre-render their PCM into the mix-ahead list.
            val due = scheduler.tick(lookahead, now)
            for (event in due) {
                val frame = clockToTrackFrame(event.time)
                val pcm = MetronomeVoices.render(event.id, SAMPLE_RATE.toDouble(), event.vol, renderRandom)
                pendingMix.add(frame to pcm)
            }

            // Write chunks up to the write-ahead horizon.
            val targetFrames = ((now - playStartClock) * SAMPLE_RATE).toLong() + WRITE_AHEAD_FRAMES
            while (running && writeCursorFrames < targetFrames) {
                val chunk = mixChunk(writeCursorFrames, CHUNK_FRAMES)
                compressor.processInPlace(chunk)
                val gain = masterVol().toFloat()
                val output = if (gain != 1.0f) FloatArray(chunk.size) { chunk[it] * gain } else chunk
                audioTrack.write(output, 0, output.size, AudioTrack.WRITE_BLOCKING)
                writeCursorFrames += CHUNK_FRAMES
            }

            // Trim consumed mix events.
            pendingMix.removeAll { it.first + it.second.size <= writeCursorFrames }

            // Publish transport at ~25 Hz.
            if (transportListener != null && now - lastTransportEmit > 0.04) {
                lastTransportEmit = (now * 1000).toLong()
                val snapshot = scheduler.getTransport(now)
                if (snapshot != null) {
                    transportListener?.onTransport(
                        snapshot.barIndex,
                        snapshot.patternPos,
                        snapshot.progress,
                        snapshot.countIn,
                        snapshot.barActive,
                    )
                }
            }

            val sleepMs = (MetronomeScheduler.TICK_MS - (clockSeconds() - now) * 1000).roundToInt()
            if (sleepMs > 0) {
                try {
                    Thread.sleep(sleepMs.toLong())
                } catch (_: InterruptedException) {
                    return
                }
            }
        }
    }

    private fun clockToTrackFrame(clockSecondsValue: Double): Long =
        ((clockSecondsValue - playStartClock) * SAMPLE_RATE).roundToInt().toLong()

    private fun mixChunk(startFrame: Long, frames: Int): FloatArray {
        val out = FloatArray(frames)
        val endFrame = startFrame + frames
        for ((eventFrame, pcm) in pendingMix) {
            val overlapStart = maxOf(eventFrame, startFrame)
            val overlapEnd = minOf(eventFrame + pcm.size, endFrame)
            if (overlapEnd <= overlapStart) continue
            for (i in overlapStart until overlapEnd) {
                out[(i - startFrame).toInt()] += pcm[(i - eventFrame).toInt()]
            }
        }
        return out
    }

    companion object {
        const val SAMPLE_RATE = 48000
        const val CHUNK_FRAMES = 1200 // 25 ms
        const val WRITE_AHEAD_FRAMES = 4800L // 100 ms of pre-written PCM
        const val JOIN_TIMEOUT_MS = 400L
    }
}
