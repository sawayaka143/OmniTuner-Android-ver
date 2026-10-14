package com.omnituner.android.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import com.omnituner.core.metronome.MetronomeScheduler
import com.omnituner.core.metronome.MetronomeState
import com.omnituner.core.sound.DynamicsCompressor
import com.omnituner.core.sound.MetronomeVoices
import kotlin.math.roundToInt
import kotlin.random.Random

class MetronomeAudioEngine {

    fun interface TransportListener {
        fun onTransport(barIndex: Int, patternPos: Int, progress: Double, countIn: Boolean, barActive: Boolean)
    }

    private val scheduler = MetronomeScheduler { clockSeconds() }
    private val compressor = DynamicsCompressor(SAMPLE_RATE.toDouble())
    private val renderRandom = Random(20260901)
    private val pendingMix = mutableListOf<Pair<Long, FloatArray>>()

    private var track: AudioTrack? = null
    private var thread: Thread? = null
    private var t0Nanos = 0L
    private var playStartClock = 0.0
    private var writeCursorFrames = 0L

    @Volatile
    private var running = false

    @Volatile
    private var background = false

    @Volatile
    private var transportListener: TransportListener? = null

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
        val active = PcmOutput.stream(
            sampleRate = SAMPLE_RATE,
            bufferBytes = maxOf(minBuffer, CHUNK_FRAMES * 2 * Float.SIZE_BYTES),
            contentType = AudioAttributes.CONTENT_TYPE_SONIFICATION,
        )

        compressor.reset()
        pendingMix.clear()
        writeCursorFrames = 0
        t0Nanos = System.nanoTime()
        scheduler.start(0.0)
        running = true

        active.play()
        playStartClock = clockSeconds()
        track = active

        thread = Thread(
            {
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                engineLoop(active)
            },
            "omnituner-metronome",
        ).also { it.start() }
    }

    fun stop() {
        if (!running) return
        running = false
        scheduler.stop()
        thread?.join(JOIN_TIMEOUT_MS)
        thread = null
        track?.let { active ->
            try {
                active.pause()
                active.flush()
                active.stop()
            } catch (_: IllegalStateException) {
            }
            active.release()
        }
        track = null
        pendingMix.clear()
        compressor.reset()
    }

    fun previewVoice(id: String, vol: Double) {
        val buffer = MetronomeVoices.render(id, SAMPLE_RATE.toDouble(), vol, renderRandom)
        DynamicsCompressor(SAMPLE_RATE.toDouble()).processInPlace(buffer)
        PcmOutput.playOnce(buffer, buffer.size, SAMPLE_RATE, AudioAttributes.CONTENT_TYPE_SONIFICATION)
    }

    private fun clockSeconds(): Double = (System.nanoTime() - t0Nanos) / 1_000_000_000.0

    private fun engineLoop(audioTrack: AudioTrack) {
        var lastTransportEmit = 0.0

        while (running) {
            val now = clockSeconds()
            val lookahead = if (background) {
                MetronomeScheduler.LOOKAHEAD_HIDDEN_S
            } else {
                MetronomeScheduler.LOOKAHEAD_VISIBLE_S
            }

            for (event in scheduler.tick(lookahead, now)) {
                val frame = clockToTrackFrame(event.time)
                val pcm = MetronomeVoices.render(event.id, SAMPLE_RATE.toDouble(), event.vol, renderRandom)
                pendingMix.add(frame to pcm)
            }

            val targetFrames = ((now - playStartClock) * SAMPLE_RATE).toLong() + WRITE_AHEAD_FRAMES
            while (running && writeCursorFrames < targetFrames) {
                val chunk = mixChunk(writeCursorFrames, CHUNK_FRAMES)
                compressor.processInPlace(chunk)
                val gain = scheduler.config.masterVol.toFloat()
                val output = if (gain != 1.0f) FloatArray(chunk.size) { chunk[it] * gain } else chunk
                audioTrack.write(output, 0, output.size, AudioTrack.WRITE_BLOCKING)
                writeCursorFrames += CHUNK_FRAMES
            }

            pendingMix.removeAll { it.first + it.second.size <= writeCursorFrames }

            val listener = transportListener
            if (listener != null && now - lastTransportEmit > TRANSPORT_EMIT_INTERVAL_S) {
                lastTransportEmit = now
                scheduler.getTransport(now)?.let { snapshot ->
                    listener.onTransport(
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

    private companion object {
        const val SAMPLE_RATE = PcmOutput.SAMPLE_RATE
        const val CHUNK_FRAMES = 1200
        const val WRITE_AHEAD_FRAMES = 4800L
        const val JOIN_TIMEOUT_MS = 400L
        const val TRANSPORT_EMIT_INTERVAL_S = 0.04
    }
}
