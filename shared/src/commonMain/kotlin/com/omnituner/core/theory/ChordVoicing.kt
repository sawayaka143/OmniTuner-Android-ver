package com.omnituner.core.theory

import com.omnituner.core.data.CHORD_FORMS
import com.omnituner.core.data.ChordForm

const val MAX_FRET = 12
const val DEFAULT_VOICING_COUNT = 6
const val MIN_VOICING_COUNT = 1
const val MAX_VOICING_COUNT = 12

private const val MAX_SPAN = 4
private const val MAX_THUMB_REACH = 4
private const val MAX_FINGERS = 4
private const val MIN_NOTES = 3
private const val MIN_DISTINCT = 3
private const val OPEN_STRING_MAX_FRET = 3
private const val FIRST_POSITION_MAX_FRET = 4
private const val COMFORTABLE_SPAN = 2
private const val COMFORTABLE_RANGE = 24
private const val REVERSE_STRETCH_FRETS = 3
// Muted strings between sounding ones (each costs PER_INTERIOR_MUTE); more is never a voicing
// worth showing and only multiplies the search on long tunings.
private const val MAX_INTERIOR_MUTES = 2
// Adjacent strings one strum covers. A no-op up to 8-string tunings; on 9-12 strings it keeps
// the search to one 8-string window at a time instead of every combination of the whole neck.
private const val MAX_SOUNDING_STRINGS = 8
// How many ranked shapes the search holds at a time (see VoicingSearch.run).
private const val RANKED_KEEP = 400

private object Weights {
    const val ROOT_IN_BASS = 100
    const val OTHER_BASS = -150
    const val PER_NOTE = 14
    const val PER_DISTINCT_PC = 8
    const val PER_FINGER = -8
    const val PER_BARRE = -55
    const val PER_POSITION_FRET = -6
    const val PER_INTERIOR_MUTE = -30
    const val PER_OUT_OF_POSITION_OPEN = -30
    const val PER_SPAN_FRET_BEYOND_COMFORT = -15
    const val PER_SEMITONE_BEYOND_RANGE = -2
    const val PER_REVERSE_STRETCH_FRET = -50
    const val EXTENSION_ON_TOP = 10
    const val STANDARD_GRIP = 45
}

val EXTENSION_INTERVALS: Set<Int> = setOf(13, 14, 15, 17, 18, 20, 21)
private val ALTERED_EXTENSION_INTERVALS: Set<Int> = setOf(13, 15, 18, 20)

data class SoundingNote(
    val stringIndex: Int,
    val fret: Int,
    val midi: Int,
)

data class VoicingShape(
    val frets: List<Int?>,
    val sounding: List<SoundingNote>,
    val span: Int,
    val bassMidi: Int,
    val bassIsRoot: Boolean,
    val position: Int,
    val openCount: Int,
    val cost: Int,
)

data class Fingering(
    val fingers: Int,
    val barres: Int,
)

private class ShapeFeatures(
    val frets: List<Int?>,
    val sounding: List<SoundingNote>,
    val pcs: Set<Int>,
    val bassMidi: Int,
    val topPc: Int,
    val range: Int,
    val position: Int,
    val maxFret: Int,
    val span: Int,
    val openCount: Int,
    val interiorMutes: Int,
    val fingering: Fingering,
)

private class RankedShape(val features: ShapeFeatures, val score: Int)

private fun fretsKey(frets: List<Int?>): String = frets.joinToString(",") { it?.toString() ?: "x" }

/** The frets of [form] rooted on [rootPc], or null when the form doesn't fit [tuning] or the neck. */
fun instantiateForm(form: ChordForm, tuning: ParsedTuning, rootPc: Int): List<Int?>? {
    if (form.offsets.size != tuning.midi.size) return null
    val anchorFret = mod12(rootPc - tuning.midi[form.anchor])
    val frets = mutableListOf<Int?>()
    for (offset in form.offsets) {
        if (offset == null) {
            frets.add(null)
            continue
        }
        val fret = anchorFret + offset
        if (fret < 0 || fret > MAX_FRET) return null
        frets.add(fret)
    }
    return frets
}

/**
 * Chord tones every voicing must contain: everything but optional tones and extensions, and
 * without the natural fifth once a third and a sixth/seventh already define the chord.
 */
fun requiredPitchClasses(chord: ParsedChord): Set<Int> {
    val optional = chord.optionalPcs.toSet()
    val hasThird = 3 in chord.intervals || 4 in chord.intervals
    val hasGuideTone = listOf(9, 10, 11).any { it in chord.intervals }
    val dropFifth = hasThird && hasGuideTone
    return chord.pcs.filterIndexed { index, pc ->
        val interval = chord.intervals[index]
        pc !in optional && interval !in EXTENSION_INTERVALS && !(dropFifth && interval == 7)
    }.toSet()
}

/** The extensions a voicing should carry: the highest one plus every altered tension. */
fun requiredExtensions(chord: ParsedChord): List<Int> {
    val extensions = chord.intervals.filter { it in EXTENSION_INTERVALS }
    if (extensions.isEmpty()) return emptyList()
    val highest = extensions.max()
    return chord.pcs.filterIndexed { index, _ ->
        val interval = chord.intervals[index]
        interval == highest || interval in ALTERED_EXTENSION_INTERVALS
    }
}

/** True when [narrow] is [wide] with zero or more strings muted. */
fun isNarrowingOf(narrow: List<Int?>, wide: List<Int?>): Boolean =
    narrow.indices.all { i -> narrow[i] == null || narrow[i] == wide.getOrNull(i) }

/**
 * Fingers needed to hold [frets], barring where more than four notes are fretted, or null when
 * four fingers can't do it. A barre runs under higher frets but is broken by an open string or
 * a lower fret between its strings.
 */
fun analyseFingering(frets: List<Int?>): Fingering? {
    val fretted = frets.indices.filter { (frets[it] ?: 0) > 0 }
    if (fretted.size <= MAX_FINGERS) return Fingering(fretted.size, 0)

    val groupSizes = mutableListOf<Int>()
    for (fret in fretted.map { frets[it]!! }.distinct()) {
        val strings = fretted.filter { frets[it] == fret }
        var size = 1
        for (k in 1 until strings.size) {
            var blocked = false
            for (s in strings[k - 1] + 1 until strings[k]) {
                val between = frets[s]
                if (between == 0 || (between != null && between < fret)) blocked = true
            }
            if (blocked) {
                groupSizes.add(size)
                size = 1
            } else {
                size++
            }
        }
        groupSizes.add(size)
    }

    var fingers = fretted.size
    var barres = 0
    for (size in groupSizes.filter { it > 1 }.sortedDescending()) {
        if (fingers <= MAX_FINGERS) break
        fingers -= size - 1
        barres++
    }
    return if (fingers <= MAX_FINGERS) Fingering(fingers, barres) else null
}

/**
 * Whether the fretting hand can still hold [prefix], the frets chosen so far on the lowest
 * strings of a [stringCount]-string shape. False means no completion is playable: adding strings
 * only widens the span and adds finger groups, so the search can drop the branch.
 */
fun handFits(prefix: List<Int?>, stringCount: Int): Boolean =
    fingerGroups(prefix) <= MAX_FINGERS && spanFits(prefix, stringCount)

// Fingers the shape needs at best: one per run of same-fret notes a barre can cover (see
// analyseFingering). Later strings never merge earlier runs, so the count only grows.
private fun fingerGroups(frets: List<Int?>): Int {
    var groups = 0
    for (s in frets.indices) {
        val fret = frets[s] ?: continue
        if (fret == 0) continue
        var joins = false
        for (p in s - 1 downTo 0) {
            val other = frets[p] ?: continue
            if (other == 0 || other < fret) break
            if (other == fret) {
                joins = true
                break
            }
        }
        if (!joins) groups++
    }
    return groups
}

// The partial form of spanReachable: a span past MAX_SPAN can only work with the thumb on the
// lowest string, below every other fretted note and at most MAX_THUMB_REACH under them.
private fun spanFits(frets: List<Int?>, stringCount: Int): Boolean {
    var low = Int.MAX_VALUE
    var high = Int.MIN_VALUE
    for (s in 1 until frets.size) {
        val fret = frets[s] ?: continue
        if (fret == 0) continue
        low = minOf(low, fret)
        high = maxOf(high, fret)
    }
    val others = low <= high
    if (others && high - low > MAX_SPAN) return false
    val thumb = frets.firstOrNull()?.takeIf { it > 0 } ?: return true
    if (!others || maxOf(high, thumb) - minOf(low, thumb) <= MAX_SPAN) return true
    if (stringCount < 4 || thumb > low) return false
    return high - thumb <= MAX_SPAN + MAX_THUMB_REACH
}

private fun describeShape(frets: List<Int?>, tuning: ParsedTuning): ShapeFeatures? {
    val fingering = analyseFingering(frets) ?: return null
    val sounding = frets.indices.mapNotNull { s ->
        frets[s]?.let { SoundingNote(s, it, tuning.midi[s] + it) }
    }
    if (sounding.isEmpty()) return null
    val midis = sounding.map { it.midi }
    val pressed = sounding.map { it.fret }.filter { it > 0 }
    val position = pressed.minOrNull() ?: 0
    val maxFret = pressed.maxOrNull() ?: 0
    return ShapeFeatures(
        frets = frets,
        sounding = sounding,
        pcs = midis.map { mod12(it) }.toSet(),
        bassMidi = midis.min(),
        topPc = mod12(midis.max()),
        range = midis.max() - midis.min(),
        position = position,
        maxFret = maxFret,
        span = maxFret - position,
        openCount = sounding.count { it.fret == 0 },
        interiorMutes = sounding.last().stringIndex - sounding.first().stringIndex + 1 - sounding.size,
        fingering = fingering,
    )
}

private val RANKED_ORDER: Comparator<RankedShape> =
    compareByDescending<RankedShape> { it.score }
        .thenByDescending { it.features.sounding.size }
        .thenBy { it.features.span }
        .thenBy { it.features.maxFret }
        .thenBy { fretsKey(it.features.frets) }

private class VoicingSearch(
    private val tuning: ParsedTuning,
    private val chord: ParsedChord,
    enforceBass: Boolean,
    enforceExtensions: Boolean,
) {
    private val chordPcs: Set<Int> = chord.pcs.toSet()
    private val requiredPcs: Set<Int> =
        requiredPitchClasses(chord) + if (enforceExtensions) requiredExtensions(chord) else emptyList()
    private val extensionPcs: Set<Int> =
        chord.pcs.filterIndexed { index, _ -> chord.intervals[index] in EXTENSION_INTERVALS }.toSet()
    private val bassPc: Int? = if (enforceBass) chord.bassPc else null
    private val allowedPcs: Set<Int> = if (bassPc == null) chordPcs else chordPcs + bassPc
    // Root-in-bass scoring only makes sense when the first string is the lowest (not a
    // re-entrant tuning like a ukulele's).
    private val hasBassString: Boolean = tuning.midi[0] == tuning.midi.min()
    private val grips: List<List<Int?>> = CHORD_FORMS
        .filter { chord.quality in it.qualities }
        .mapNotNull { instantiateForm(it, tuning, chord.rootPc) }
    private val standardGrips: Set<String> = grips.map { fretsKey(it) }.toSet()

    fun run(count: Int): List<VoicingShape> {
        // The enumeration never repeats a shape and its pruning only drops unplayable branches,
        // so every playable standard grip comes out of it too; the grips only add a score bonus.
        // Only the best RANKED_KEEP shapes are kept: on long-scale or 12-string tunings there are
        // millions of playable shapes, and pickDistinct rarely looks past the first few dozen.
        val keep = maxOf(RANKED_KEEP, count * 20)
        val ranked = mutableListOf<RankedShape>()
        enumerate { frets ->
            val features = describeShape(frets, tuning) ?: return@enumerate
            if (!isPlayable(features)) return@enumerate
            ranked.add(RankedShape(features, score(features)))
            if (ranked.size >= keep * 2) {
                ranked.sortWith(RANKED_ORDER)
                ranked.subList(keep, ranked.size).clear()
            }
        }
        ranked.sortWith(RANKED_ORDER)
        return pickDistinct(ranked, count).map(::toShape)
    }

    private fun stringOptions(): List<List<Int?>> = tuning.midi.map { open ->
        listOf<Int?>(null) + (0..MAX_FRET).filter { mod12(open + it) in allowedPcs }
    }

    private fun enumerate(visit: (List<Int?>) -> Unit) {
        val options = stringOptions()
        val n = options.size
        // reachable[s]: pitch classes some string from s upward can still supply.
        val reachable = Array(n + 1) { mutableSetOf<Int>() }
        for (s in n - 1 downTo 0) {
            reachable[s].addAll(reachable[s + 1])
            for (fret in options[s]) if (fret != null) reachable[s].add(mod12(tuning.midi[s] + fret))
        }
        val current = mutableListOf<Int?>()
        val counts = IntArray(12)
        val needsDistinct = requiredPcs.size >= MIN_DISTINCT
        val muteOnly = listOf<Int?>(null)

        // muteRest: an earlier muted string has no fretted neighbour, so it may only stay
        // unplayed if every string from here on is muted too (see mutesReachable).
        // pendingMutes: muted strings since the last sounding one, interior once a string sounds.
        // firstSounding: the lowest sounding string so far, -1 before any.
        fun walk(
            idx: Int,
            played: Int,
            muteRest: Boolean,
            pendingMutes: Int,
            interiorMutes: Int,
            firstSounding: Int,
        ) {
            for (pc in requiredPcs) if (counts[pc] == 0 && pc !in reachable[idx]) return
            if (played + (n - idx) < MIN_NOTES) return
            if (needsDistinct && distinctChordTones(counts) + (n - idx) < MIN_DISTINCT) return
            if (idx == n) {
                visit(current.toList())
                return
            }
            val outOfReach = firstSounding >= 0 && idx - firstSounding >= MAX_SOUNDING_STRINGS
            for (fret in if (muteRest || outOfReach) muteOnly else options[idx]) {
                val interior = if (fret != null && played > 0) interiorMutes + pendingMutes else interiorMutes
                if (interior > MAX_INTERIOR_MUTES) continue
                current.add(fret)
                val lock = muteRest || strandsMute(current, played)
                if ((fret == null || !lock) && handFits(current, n)) {
                    val pc = fret?.let { mod12(tuning.midi[idx] + it) }
                    if (pc != null) counts[pc]++
                    val pending = when {
                        fret != null -> 0
                        played > 0 -> pendingMutes + 1
                        else -> 0
                    }
                    val first = if (firstSounding < 0 && fret != null) idx else firstSounding
                    walk(idx + 1, played + if (pc == null) 0 else 1, lock, pending, interior, first)
                    if (pc != null) counts[pc]--
                }
                current.removeAt(current.size - 1)
            }
        }
        walk(0, 0, false, 0, 0, -1)
    }

    // True when the string before the newest one is muted between a sounding string and a
    // string that isn't fretted, so nothing may sound after it. [playedBefore] counts the
    // sounding strings before the newest one.
    private fun strandsMute(frets: List<Int?>, playedBefore: Int): Boolean {
        val last = frets.size - 1
        if (last < 1 || frets[last - 1] != null) return false
        if (playedBefore == 0) return false
        val below = frets.getOrNull(last - 2)
        val above = frets[last]
        return !(below != null && below > 0) && !(above != null && above > 0)
    }

    private fun distinctChordTones(counts: IntArray): Int = chordPcs.count { counts[it] > 0 }

    private fun isPlayable(shape: ShapeFeatures): Boolean {
        if (shape.sounding.size < MIN_NOTES) return false
        if (bassPc != null && mod12(shape.bassMidi) != bassPc) return false
        if (shape.pcs.size < MIN_DISTINCT && requiredPcs.size >= MIN_DISTINCT) return false
        if (!shape.pcs.containsAll(requiredPcs)) return false
        return mutesReachable(shape.frets) && spanReachable(shape)
    }

    // A muted string between sounding ones has to be damped by a fretting finger next to it.
    private fun mutesReachable(frets: List<Int?>): Boolean {
        val played = frets.indices.filter { frets[it] != null }
        if (played.size < 2) return true
        for (i in played.first() + 1 until played.last()) {
            if (frets[i] != null) continue
            val touched = listOf(frets[i - 1], frets[i + 1]).any { it != null && it > 0 }
            if (!touched) return false
        }
        return true
    }

    // Spans past MAX_SPAN are only allowed when the thumb takes the lowest string from behind.
    private fun spanReachable(shape: ShapeFeatures): Boolean {
        if (shape.span <= MAX_SPAN) return true
        val thumb = shape.frets[0]
        if (thumb == null || thumb == 0 || shape.frets.size < 4) return false
        val others = shape.frets.drop(1).filterNotNull().filter { it > 0 }
        if (others.isEmpty()) return false
        val low = others.min()
        return others.max() - low <= MAX_SPAN && thumb <= low && low - thumb <= MAX_THUMB_REACH
    }

    private fun score(shape: ShapeFeatures): Int {
        var score = 0
        if (bassPc == null && hasBassString) {
            score += if (mod12(shape.bassMidi) == chord.rootPc) Weights.ROOT_IN_BASS else Weights.OTHER_BASS
        }
        score += shape.sounding.size * Weights.PER_NOTE
        score += shape.pcs.size * Weights.PER_DISTINCT_PC
        score += shape.fingering.fingers * Weights.PER_FINGER
        score += shape.fingering.barres * Weights.PER_BARRE
        if (shape.maxFret > FIRST_POSITION_MAX_FRET) {
            score += (shape.position - 1) * Weights.PER_POSITION_FRET
        }
        score += shape.interiorMutes * Weights.PER_INTERIOR_MUTE
        if (shape.maxFret > OPEN_STRING_MAX_FRET) {
            score += shape.openCount * Weights.PER_OUT_OF_POSITION_OPEN
        }
        score += maxOf(0, shape.span - COMFORTABLE_SPAN) * Weights.PER_SPAN_FRET_BEYOND_COMFORT
        score += maxOf(0, shape.range - COMFORTABLE_RANGE) * Weights.PER_SEMITONE_BEYOND_RANGE
        score += reverseStretch(shape.frets) * Weights.PER_REVERSE_STRETCH_FRET
        if (shape.topPc in extensionPcs) score += Weights.EXTENSION_ON_TOP
        if (fretsKey(shape.frets) in standardGrips) score += Weights.STANDARD_GRIP
        return score
    }

    // Frets by which a lower string sits more than REVERSE_STRETCH_FRETS above one of the next
    // two strings, a stretch the hand can't make comfortably.
    private fun reverseStretch(frets: List<Int?>): Int {
        var total = 0
        for (i in 0 until frets.size - 1) {
            val low = frets[i]
            if (low == null || low == 0) continue
            for (j in i + 1 until minOf(i + 3, frets.size)) {
                val high = frets[j]
                if (high != null && high > 0 && low > high + REVERSE_STRETCH_FRETS) total += low - high
            }
        }
        return total
    }

    // Skips a shape that is just a better-ranked one with strings muted (or vice versa).
    private fun pickDistinct(ranked: List<RankedShape>, count: Int): List<RankedShape> {
        val picked = mutableListOf<RankedShape>()
        for (entry in ranked) {
            if (picked.size >= count) break
            val frets = entry.features.frets
            val duplicate = picked.any {
                isNarrowingOf(frets, it.features.frets) || isNarrowingOf(it.features.frets, frets)
            }
            if (!duplicate) picked.add(entry)
        }
        return picked
    }

    private fun toShape(entry: RankedShape): VoicingShape {
        val f = entry.features
        return VoicingShape(
            frets = f.frets,
            sounding = f.sounding,
            span = f.span,
            bassMidi = f.bassMidi,
            bassIsRoot = mod12(f.bassMidi) == chord.rootPc,
            position = f.position,
            openCount = f.openCount,
            cost = entry.score,
        )
    }
}

/**
 * The [count] best-ranked playable voicings of [chord] on [tuning]. Tries the strictest rules
 * first (slash bass under the chord, extensions present), then relaxes the extensions, then the
 * bass, and returns the first non-empty result.
 */
fun searchChord(
    tuning: ParsedTuning,
    chord: ParsedChord,
    count: Int = DEFAULT_VOICING_COUNT,
): List<VoicingShape> {
    val attempts = listOf(true to true, true to false, false to false)
    for ((enforceBass, enforceExtensions) in attempts) {
        if (!enforceBass && chord.bassPc == null) break
        val shapes = VoicingSearch(tuning, chord, enforceBass, enforceExtensions).run(count)
        if (shapes.isNotEmpty()) return shapes
    }
    return emptyList()
}
