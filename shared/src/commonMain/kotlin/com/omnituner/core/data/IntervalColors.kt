package com.omnituner.core.data

import kotlin.math.pow

val INTERVAL_COLORS: Map<String, String> = mapOf(
    "R" to "#779900",
    "1" to "#779900",

    "3" to "#ff9900",
    "m3" to "#ff9900",
    "♭3" to "#ff9900",
    "sus2" to "#ff9900",
    "sus4" to "#ff9900",

    "5" to "#227799",
    "b5" to "#227799",
    "♭5" to "#227799",
    "#5" to "#227799",

    "b6" to "#ee6600",
    "♭6" to "#ee6600",
    "6" to "#ee6600",
    "dim7" to "#ee6600",
    "7" to "#ee6600",
    "♭7" to "#ee6600",
    "maj7" to "#ee6600",

    "2" to "#ee0000",
    "4" to "#ee0000",
    "9" to "#ee0000",
    "11" to "#ee0000",
    "13" to "#ee0000",

    "b9" to "#bb3366",
    "♭2" to "#bb3366",
    "#9" to "#bb3366",
    "#11" to "#bb3366",
    "♯4" to "#bb3366",
    "b13" to "#bb3366",
)

const val DEFAULT_INTERVAL_COLOR = "#94948e"

fun colorForLabel(label: String): String =
    INTERVAL_COLORS[label] ?: DEFAULT_INTERVAL_COLOR

fun textColorOn(hex: String): String {
    val normalized = hex.replace("#", "")
    if (normalized.length != 6) return "#f5f5f3"
    val r = normalized.substring(0, 2).toInt(16) / 255.0
    val g = normalized.substring(2, 4).toInt(16) / 255.0
    val b = normalized.substring(4, 6).toInt(16) / 255.0

    fun toLinear(channel: Double): Double =
        if (channel <= 0.03928) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)

    val luminance = 0.2126 * toLinear(r) + 0.7152 * toLinear(g) + 0.0722 * toLinear(b)
    return if (luminance > 0.45) "#121211" else "#f5f5f3"
}
