package com.omnituner.core.data

import kotlin.math.abs
import kotlin.math.pow

data class GuitarSample(
    val file: String,
    val rootMidi: Int,
)

const val GUITAR_SAMPLE_MIN_MIDI = 37

const val GUITAR_SAMPLE_MAX_MIDI = 86

val GUITAR_SAMPLES: List<GuitarSample> = listOf(
    GuitarSample("db2_mf_rr1.wav", 37),
    GuitarSample("e2_mf_rr1.wav", 40),
    GuitarSample("gb2_mf_rr1.wav", 42),
    GuitarSample("a2_mf_rr1.wav", 45),
    GuitarSample("c3_mf_rr1.wav", 48),
    GuitarSample("eb3_mf_rr1.wav", 51),
    GuitarSample("gb3_mf_rr1.wav", 54),
    GuitarSample("a3_mf_rr1.wav", 57),
    GuitarSample("c4_mf_rr1.wav", 60),
    GuitarSample("eb4_mf_rr1.wav", 63),
    GuitarSample("gb4_mf_rr1.wav", 66),
    GuitarSample("a4_mf_rr1.wav", 69),
    GuitarSample("c5_mf_rr1.wav", 72),
    GuitarSample("eb5_mf_rr1.wav", 75),
    GuitarSample("gb5_mf_rr1.wav", 78),
    GuitarSample("a5_mf_rr1.wav", 81),
    GuitarSample("c6_mf_rr1.wav", 84),
    GuitarSample("d6_mf_rr1.wav", 86),
)

fun nearestGuitarSample(midi: Int): GuitarSample? {
    if (midi < GUITAR_SAMPLE_MIN_MIDI || midi > GUITAR_SAMPLE_MAX_MIDI) return null
    var best = GUITAR_SAMPLES.first()
    for (sample in GUITAR_SAMPLES) {
        if (abs(sample.rootMidi - midi) < abs(best.rootMidi - midi)) best = sample
    }
    return best
}

fun guitarSamplePlaybackRate(midi: Int, rootMidi: Int): Double =
    2.0.pow((midi - rootMidi) / 12.0)
