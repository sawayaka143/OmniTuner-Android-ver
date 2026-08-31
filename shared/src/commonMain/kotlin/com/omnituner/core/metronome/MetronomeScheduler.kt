package com.omnituner.core.metronome

import com.omnituner.core.timing.BarEvent
import com.omnituner.core.timing.MeterModel
import com.omnituner.core.timing.PolyEvents
import com.omnituner.core.timing.buildBarEvents
import com.omnituner.core.timing.meterModel

data class ScheduledSound(
    val time: Double,
    val role: String,
    val layer: String,
    val id: String,
    val vol: Double,
)

data class TransportSnapshot(
    val progress: Double,
    val barIndex: Int,
    val patternPos: Int,
    val beatsPerBar: Int,
    val barActive: Boolean,
    val countIn: Boolean,
    val bpm: Int,
)

/**
 * Pure port of the metronome-audio.service.ts scheduling state machine.
 * The platform engine (AudioTrack on Android) drives the clock, consumes
 * [tick] events and owns gain/compressor rendering; all grid math lives here.
 */
class MetronomeScheduler(
    private val clock: () -> Double,
) {

    var config: MetronomeState = DEFAULT_METRONOME_STATE
        private set

    var isPlaying: Boolean = false
        private set

    private var dirty = false

    private var barCount = 0
    private var patternPos = 0
    private var barBase = 0.0
    private var anchorT = 0.0
    private var anchorB = 0.0
    private var spb: Double = 60.0 / config.bpm
    private var rampStartBpm: Double = config.bpm
    private var inCountIn = false
    private var barEvents: List<BarEvent> = emptyList()
    private var nextIdx = 0
    private var model: MeterModel = meterModel(
        config.timeSignature.numerator,
        config.timeSignature.denominator,
    )

    fun configure(state: MetronomeState) {
        if (state.bpm != config.bpm) {
            config = config.copy(bpm = state.bpm)
            if (isPlaying) retune() else spb = 60.0 / state.bpm
        }
        if (state.masterVol != config.masterVol) {
            config = config.copy(masterVol = state.masterVol)
        }
        if (state.sounds != config.sounds) {
            config = config.copy(sounds = state.sounds)
        }
        if (
            state.timeSignature != config.timeSignature ||
            state.divisionsPerBeat != config.divisionsPerBeat ||
            state.barPattern != config.barPattern ||
            state.poly != config.poly
        ) {
            config = config.copy(
                timeSignature = state.timeSignature,
                divisionsPerBeat = state.divisionsPerBeat,
                barPattern = state.barPattern,
                poly = state.poly,
            )
            dirty = true
        }
        if (state.countIn != config.countIn) {
            config = config.copy(countIn = state.countIn)
        }
        if (state.ramp != config.ramp) {
            config = config.copy(ramp = state.ramp)
        }
    }

    fun start(now: Double = clock()) {
        if (isPlaying) return
        dirty = false
        model = meterModel(config.timeSignature.numerator, config.timeSignature.denominator)
        spb = 60.0 / config.bpm
        rampStartBpm = config.bpm
        inCountIn = config.countIn
        barCount = if (inCountIn) -1 else 0
        patternPos = 0
        barBase = if (inCountIn) -model.beatsPerBar.toDouble() else 0.0
        anchorB = barBase
        anchorT = now + START_DELAY_S
        buildBar()
        nextIdx = 0
        isPlaying = true
    }

    fun stop() {
        if (!isPlaying) return
        isPlaying = false
        inCountIn = false
    }

    fun getTransport(now: Double = clock()): TransportSnapshot? {
        if (!isPlaying) return null
        val barStart = anchorT + (barBase - anchorB) * spb
        val dur = model.beatsPerBar * spb
        val progress = (now - barStart) / dur
        return TransportSnapshot(
            progress = progress.coerceIn(0.0, 1.0),
            barIndex = barCount,
            patternPos = patternPos,
            beatsPerBar = model.beatsPerBar,
            barActive = barCount < 0 || config.barPattern.getOrNull(patternPos) == 1,
            countIn = barCount < 0,
            bpm = (60.0 / spb).toInt(),
        )
    }

    /**
     * Schedules events up to `lookaheadSeconds` ahead of the current clock time.
     * Returns the sounds the platform engine must write into its timeline.
     */
    fun tick(lookaheadSeconds: Double, now: Double = clock()): List<ScheduledSound> {
        if (!isPlaying) return emptyList()
        val horizon = now + lookaheadSeconds
        val due = mutableListOf<ScheduledSound>()
        var guard = 0
        while (guard++ < 512) {
            if (nextIdx >= barEvents.size) {
                val endBeat = barBase + model.beatsPerBar
                if (timeOf(endBeat) > horizon) break
                advanceBar()
                continue
            }
            val event = barEvents[nextIdx]
            val t = timeOf(barBase + event.beats)
            if (t > horizon) break
            nextIdx++
            if (t >= now - 0.01) {
                val spec = soundFor(event)
                if (spec != null && spec.vol > 0.002) {
                    due.add(
                        ScheduledSound(
                            time = maxOf(t, now + 0.001),
                            role = event.role,
                            layer = event.layer,
                            id = spec.id,
                            vol = spec.vol,
                        ),
                    )
                }
            }
        }
        return due
    }

    private fun timeOf(beat: Double): Double = anchorT + (beat - anchorB) * spb

    private fun retune(now: Double = clock()) {
        val elapsedB = anchorB + (now - anchorT) / spb
        spb = 60.0 / config.bpm
        anchorT = now
        anchorB = elapsedB
        rampStartBpm = config.bpm
    }

    private fun setSpbAt(bpm: Double, atBeat: Double) {
        if (!bpm.isFinite() || bpm <= 0) return
        val t = anchorT + (atBeat - anchorB) * spb
        anchorT = t
        anchorB = atBeat
        spb = 60.0 / bpm
    }

    private fun applyRampStep() {
        if (!config.ramp.enabled || inCountIn || barCount < 0) return
        val bars = config.ramp.bars
        val targetBpm = config.ramp.targetBpm
        val progress = (barCount.toDouble() / maxOf(1, bars)).coerceIn(0.0, 1.0)
        val effBpm = rampStartBpm + (targetBpm - rampStartBpm) * progress
        setSpbAt(effBpm, barBase)
    }

    private fun advanceBar() {
        val finishedBeats = model.beatsPerBar
        if (dirty) {
            dirty = false
            model = meterModel(config.timeSignature.numerator, config.timeSignature.denominator)
        }
        barBase += finishedBeats
        barCount++
        if (inCountIn) {
            inCountIn = false
            patternPos = 0
        } else {
            patternPos = (patternPos + 1) % maxOf(1, config.barPattern.size)
        }
        applyRampStep()
        buildBar()
        nextIdx = 0
    }

    private fun buildBar() {
        val countInBar = inCountIn
        val active = countInBar || config.barPattern.getOrNull(patternPos) == 1
        barEvents = if (active) {
            if (countInBar) {
                buildCountInEvents()
            } else {
                buildBarEvents(
                    model,
                    subdivision = config.divisionsPerBeat,
                    poly = PolyEvents(config.poly.enabled, config.poly.events, config.poly.accentFirst),
                )
            }
        } else {
            emptyList()
        }
    }

    private fun buildCountInEvents(): List<BarEvent> {
        val events = mutableListOf<BarEvent>()
        for (b in 0 until model.beatsPerBar) {
            events.add(
                BarEvent(
                    beats = b.toDouble(),
                    layer = "meter",
                    role = if (b == 0) "downbeat" else "beat",
                ),
            )
        }
        return events
    }

    private fun soundFor(event: BarEvent): SoundRole? {
        val sounds = config.sounds
        if (event.layer == "poly") {
            val poly = sounds.poly
            return if (event.role == "polyAccent") {
                poly.copy(vol = minOf(1.0, poly.accentVol ?: 1.0))
            } else {
                poly
            }
        }
        val role = when (event.role) {
            "downbeat" -> sounds.downbeat
            "subdivision" -> sounds.subdivision
            else -> sounds.beat
        }
        return role
    }

    companion object {
        const val TICK_MS = 25L
        const val LOOKAHEAD_VISIBLE_S = 0.12
        const val LOOKAHEAD_HIDDEN_S = 1.1
        const val START_DELAY_S = 0.08
    }
}
