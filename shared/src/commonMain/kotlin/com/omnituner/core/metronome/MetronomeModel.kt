package com.omnituner.core.metronome

const val BPM_MIN = 1
const val BPM_MAX = 800
const val BPM_DEFAULT = 100

const val NUMERATOR_MIN = 1
const val NUMERATOR_MAX = 32

val DENOMINATORS: List<Int> = listOf(2, 4, 8, 16)

fun isDenominator(value: Int): Boolean = value in DENOMINATORS

const val DIVISIONS_MIN = 1
const val DIVISIONS_MAX = 12

data class SubdivisionOption(
    val n: Int,
    val label: String,
    val shortLabel: String,
)

val SUBDIVISIONS: List<SubdivisionOption> = listOf(
    SubdivisionOption(1, "Beat only", "beat"),
    SubdivisionOption(2, "Eighths", "8ths"),
    SubdivisionOption(3, "Triplets", "trips"),
    SubdivisionOption(4, "Sixteenths", "16ths"),
    SubdivisionOption(5, "Quintuplets", "quint"),
    SubdivisionOption(6, "Sextuplets", "sext"),
    SubdivisionOption(7, "Septuplets", "sept"),
)

const val PATTERN_MIN_BARS = 1
const val PATTERN_MAX_BARS = 16

const val POLY_MIN = 1
const val POLY_MAX = 32

const val VOLUME_MIN = 0.0
const val VOLUME_MAX = 1.0

data class TimeSignature(
    val numerator: Int,
    val denominator: Int,
)

data class PolyState(
    val enabled: Boolean,
    val events: Int,
    val accentFirst: Boolean,
)

data class SoundRole(
    val id: String,
    val vol: Double,
    val accentVol: Double? = null,
)

data class MetronomeSoundRoles(
    val downbeat: SoundRole,
    val beat: SoundRole,
    val subdivision: SoundRole,
    val poly: SoundRole,
)

data class TempoRamp(
    val enabled: Boolean,
    val targetBpm: Double,
    val bars: Int,
)

const val RAMP_BARS_MIN = 1
const val RAMP_BARS_MAX = 32

val DEFAULT_METRONOME_RAMP: TempoRamp = TempoRamp(enabled = false, targetBpm = 120.0, bars = 8)

data class MetronomeState(
    val bpm: Double,
    val timeSignature: TimeSignature,
    val divisionsPerBeat: Int,
    val barPattern: List<Int>,
    val poly: PolyState,
    val sounds: MetronomeSoundRoles,
    val masterVol: Double,
    val countIn: Boolean,
    val ramp: TempoRamp,
)

data class MetronomePreset(
    val id: String,
    val name: String,
    val state: MetronomeState,
)

const val PRESETS_MAX = 50

val DEFAULT_METRONOME_SOUNDS: MetronomeSoundRoles = MetronomeSoundRoles(
    downbeat = SoundRole("beep-hi", 1.0),
    beat = SoundRole("beep-mid", 0.8),
    subdivision = SoundRole("beep-lo", 0.5),
    poly = SoundRole("shaker", 0.55, accentVol = 1.0),
)

val DEFAULT_METRONOME_STATE: MetronomeState = MetronomeState(
    bpm = BPM_DEFAULT.toDouble(),
    timeSignature = TimeSignature(4, 4),
    divisionsPerBeat = 1,
    barPattern = listOf(1),
    poly = PolyState(enabled = false, events = 3, accentFirst = true),
    sounds = DEFAULT_METRONOME_SOUNDS,
    masterVol = 0.9,
    countIn = false,
    ramp = DEFAULT_METRONOME_RAMP,
)

val METER_PRESETS: List<TimeSignature> = listOf(
    TimeSignature(2, 4),
    TimeSignature(3, 4),
    TimeSignature(4, 4),
    TimeSignature(5, 4),
    TimeSignature(6, 8),
    TimeSignature(7, 8),
    TimeSignature(9, 8),
    TimeSignature(10, 16),
    TimeSignature(11, 8),
    TimeSignature(12, 8),
)

data class PatternPreset(
    val label: String,
    val bars: List<Int>,
)

val PATTERN_PRESETS: List<PatternPreset> = listOf(
    PatternPreset("All on", listOf(1)),
    PatternPreset("1 : 1", listOf(1, 0)),
    PatternPreset("2 : 1", listOf(1, 1, 0)),
    PatternPreset("2 : 2", listOf(1, 1, 0, 0)),
    PatternPreset("3 : 1", listOf(1, 1, 1, 0)),
    PatternPreset("4 : 4", listOf(1, 1, 1, 1, 0, 0, 0, 0)),
)

val POLY_PRESETS: List<Pair<Int, Int>> = listOf(
    2 to 3,
    3 to 4,
    4 to 3,
    3 to 5,
    4 to 5,
    5 to 4,
    5 to 7,
    7 to 5,
    9 to 7,
)
