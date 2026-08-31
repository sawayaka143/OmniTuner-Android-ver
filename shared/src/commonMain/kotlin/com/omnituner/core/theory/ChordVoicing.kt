package com.omnituner.core.theory

const val MAX_FRET = 12
const val RESULTS_PER_CHORD = 6

private const val MAX_SPAN = 4
private const val MAX_THUMB_REACH = 4
private const val MIN_NOTES = 3
private const val MIN_DISTINCT = 3

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

private fun diagramToKey(diagram: List<Int?>): String =
    diagram.joinToString(",") { f -> f?.toString() ?: "x" }

private data class ChordOptions(
    val noteCurve: Map<Int, Int>,
    val defaultNotePenalty: Int,
    val openPenaltyThreshold: Int,
    val openPenaltyPer: Int,
    val upperSpanLimit: Int,
    val upperSpanPenaltyPerSemitone: Int,
    val lowHalfstepPenalty: Int,
    val lowWholestepPenalty: Int,
    val rootBias: Int,
    val doublingMultiplier: Double,
)

private val VOICING_STYLES: Map<String, ChordOptions> = mapOf(
    "open_pop" to ChordOptions(
        noteCurve = mapOf(3 to 50, 4 to 80, 5 to 92, 6 to 96),
        defaultNotePenalty = -25,
        openPenaltyThreshold = 3,
        openPenaltyPer = 25,
        upperSpanLimit = 17,
        upperSpanPenaltyPerSemitone = 2,
        lowHalfstepPenalty = 12,
        lowWholestepPenalty = 6,
        rootBias = 120,
        doublingMultiplier = 1.0,
    ),
    "jazz_comping" to ChordOptions(
        noteCurve = mapOf(3 to 45, 4 to 92, 5 to 40, 6 to -35),
        defaultNotePenalty = -35,
        openPenaltyThreshold = 3,
        openPenaltyPer = 30,
        upperSpanLimit = 12,
        upperSpanPenaltyPerSemitone = 5,
        lowHalfstepPenalty = 25,
        lowWholestepPenalty = 10,
        rootBias = 140,
        doublingMultiplier = 1.2,
    ),
)

private val TEMPLATE_BONUSES: Map<String, Map<String, Int>> = mapOf(
    "Cm" to mapOf(
        "x,3,5,5,4,3" to 300,
        "8,10,10,8,8,8" to 300,
        "x,3,5,5,4,x" to 250,
        "8,10,10,8,x,x" to 220,
    ),
    "C" to mapOf(
        "x,3,2,0,1,0" to 300,
        "8,10,10,9,8,8" to 300,
        "x,3,5,5,5,3" to 280,
    ),
    "G" to mapOf(
        "3,2,0,0,0,3" to 300,
        "3,2,0,0,3,3" to 300,
        "3,5,5,4,3,3" to 280,
    ),
    "Cm7" to mapOf(
        "x,3,5,3,4,3" to 300,
        "8,10,8,8,8,8" to 300,
        "x,3,5,3,4,x" to 270,
        "x,3,1,3,4,x" to 260,
        "8,x,8,8,8,x" to 250,
        "8,10,8,8,x,x" to 240,
    ),
    "Cm9" to mapOf(
        "x,3,1,3,3,x" to 350,
        "8,6,8,8,8,x" to 320,
        "x,3,5,3,3,3" to 220,
    ),
    "Dm9" to mapOf(
        "x,5,3,5,5,x" to 350,
        "10,8,10,10,10,x" to 320,
        "x,5,7,5,6,5" to 220,
    ),
    "Gmaj7" to mapOf("3,x,4,4,3,x" to 250),
    "B7" to mapOf("x,2,1,2,0,2" to 250),
    "C7" to mapOf(
        "x,3,2,3,1,0" to 250,
        "x,3,2,3,1,x" to 250,
    ),
    "Em7" to mapOf("0,2,0,0,0,0" to 250),
    "C#m7b5" to mapOf("x,4,5,4,5,x" to 250),
    "D7" to mapOf("x,x,0,2,1,2" to 250),
)

private val JAZZ_QUALITIES = setOf("maj7", "m7", "m7b5", "dim7", "7")

private fun resolveStyle(chord: ParsedChord): ChordOptions {
    val q = chord.quality
    if (q in JAZZ_QUALITIES) return VOICING_STYLES.getValue("jazz_comping")
    if (q.contains("maj7") || q.contains("m7b5") || q.contains("ø")) {
        return VOICING_STYLES.getValue("jazz_comping")
    }
    if (chord.intervals.contains(11) || chord.intervals.contains(10)) {
        return VOICING_STYLES.getValue("jazz_comping")
    }
    return VOICING_STYLES.getValue("open_pop")
}

private fun makeShape(
    frets: List<Int?>,
    tuning: ParsedTuning,
    chord: ParsedChord,
    cost: Int,
): VoicingShape {
    val sounding = mutableListOf<SoundingNote>()
    for (i in frets.indices) {
        val fret = frets[i]
        if (fret != null) sounding.add(SoundingNote(i, fret, tuning.midi[i] + fret))
    }
    val frettedOnly = frets.filterNotNull().filter { it > 0 }
    val span = if (frettedOnly.isNotEmpty()) frettedOnly.max() - frettedOnly.min() else 0
    var bass = Int.MAX_VALUE
    for (note in sounding) if (note.midi < bass) bass = note.midi
    val bassIsRoot = sounding.isNotEmpty() && mod12(bass - chord.rootPc) == 0
    val position = if (frettedOnly.isNotEmpty()) frettedOnly.min() else 0
    val openCount = frets.count { it == 0 }
    return VoicingShape(
        frets = frets.toList(),
        sounding = sounding,
        span = span,
        bassMidi = bass,
        bassIsRoot = bassIsRoot,
        position = position,
        openCount = openCount,
        cost = cost,
    )
}

private class BiomechanicalEngine(
    private val tuning: ParsedTuning,
    private val chord: ParsedChord,
) {
    private val pcs: Set<Int> = chord.pcs.toSet()
    private val requiredPcs: Set<Int> = chord.pcs.filter { pc -> pc !in chord.optionalPcs.toSet() }.toSet()
    private val style: ChordOptions = resolveStyle(chord)
    private val options: List<List<Int?>> = buildStringOptions()

    private fun buildStringOptions(): List<List<Int?>> {
        val options = mutableListOf<List<Int?>>()
        for (s in tuning.midi.indices) {
            val stringOpts = mutableListOf<Int?>(null)
            for (fret in 0..MAX_FRET) {
                if (mod12(tuning.midi[s] + fret) in pcs) stringOpts.add(fret)
            }
            options.add(stringOpts)
        }
        return options
    }

    fun generate(limit: Int = RESULTS_PER_CHORD): List<Pair<Int, List<Int?>>> {
        val n = tuning.midi.size
        val suffixCover = Array(n + 1) { mutableSetOf<Int>() }
        for (s in n - 1 downTo 0) {
            suffixCover[s].addAll(suffixCover[s + 1])
            for (fret in options[s]) {
                if (fret == null) continue
                suffixCover[s].add(mod12(tuning.midi[s] + fret))
            }
        }

        val seen = mutableSetOf<String>()
        val scored = mutableListOf<Pair<Int, List<Int?>>>()
        val current = mutableListOf<Int?>()
        val covered = mutableSetOf<Int>()

        fun dfs(idx: Int, voiced: Int) {
            for (pc in requiredPcs) {
                if (pc !in covered && pc !in suffixCover[idx]) return
            }
            if (voiced + (n - idx) < MIN_NOTES) return
            val pcsNow = pcsForPartial(current, covered)
            if (pcsNow.size + (n - idx) < MIN_DISTINCT) return

            if (idx == n) {
                val key = diagramToKey(current)
                if (key in seen) return
                seen.add(key)
                if (!isValid(current)) return
                scored.add(score(current) to current.toList())
                return
            }

            for (fret in options[idx]) {
                current.add(fret)
                var added = false
                var pc: Int? = null
                if (fret != null) {
                    val f = mod12(tuning.midi[idx] + fret)
                    pc = f
                    if (f in requiredPcs && f !in covered) {
                        covered.add(f)
                        added = true
                    }
                }
                dfs(idx + 1, voiced + if (fret != null) 1 else 0)
                if (added && pc != null) covered.remove(pc)
                current.removeAt(current.size - 1)
            }
        }

        dfs(0, 0)

        scored.sortWith { a, b ->
            val ka = sortKey(a.first, a.second)
            val kb = sortKey(b.first, b.second)
            for (i in ka.indices) {
                val va = ka[i]
                val vb = kb[i]
                if (va != vb) return@sortWith va - vb
            }
            0
        }

        return scored.take(limit)
    }

    private fun pcsForPartial(partial: List<Int?>, covered: Set<Int>): Set<Int> {
        val s = covered.toMutableSet()
        for (i in partial.indices) {
            val fret = partial[i] ?: continue
            val pc = mod12(tuning.midi[i] + fret)
            if (pc in pcs) s.add(pc)
        }
        return s
    }

    private fun playedIndices(diagram: List<Int?>): List<Int> =
        diagram.withIndex().filter { it.value != null }.map { it.index }

    private fun pcAt(stringIdx: Int, fret: Int): Int = mod12(tuning.midi[stringIdx] + fret)

    private fun pcsForDiagram(diagram: List<Int?>): Set<Int> =
        playedIndices(diagram).map { pcAt(it, diagram[it]!!) }.toSet()

    private fun bassIntervalForDiagram(diagram: List<Int?>): Int? {
        val played = playedIndices(diagram)
        if (played.isEmpty()) return null
        val bassPc = pcAt(played[0], diagram[played[0]]!!)
        return mod12(bassPc - chord.rootPc)
    }

    private fun isValid(diagram: List<Int?>): Boolean {
        val played = playedIndices(diagram)
        if (played.size < MIN_NOTES) return false
        val diagramPcs = pcsForDiagram(diagram)
        if (diagramPcs.size < MIN_DISTINCT && requiredPcs.size >= MIN_DISTINCT) return false
        if (!requiredPcs.all { it in diagramPcs }) return false
        if (!dampingOk(diagram, played)) return false
        if (!frettedCountOk(diagram)) return false
        if (!spanOk(diagram)) return false
        return true
    }

    private fun dampingOk(diagram: List<Int?>, played: List<Int>): Boolean {
        if (played.size < 2) return true
        val minP = played.min()
        val maxP = played.max()
        for (i in (minP + 1) until maxP) {
            if (diagram[i] == null) {
                val ok = listOf(-1, 1).any { adj ->
                    val adjIdx = i + adj
                    adjIdx in diagram.indices && diagram[adjIdx] != null && diagram[adjIdx]!! > 0
                }
                if (!ok) return false
            }
        }
        return true
    }

    private fun spanOk(diagram: List<Int?>): Boolean {
        val positives = diagram.filterNotNull().filter { it > 0 }
        if (positives.isEmpty()) return true
        if (positives.max() - positives.min() <= MAX_SPAN) return true
        val thumbF = diagram[0]
        if (thumbF != null && thumbF > 0 && diagram.size >= 4) {
            val others = diagram.withIndex()
                .filter { it.index != 0 && (it.value ?: -1) > 0 }
                .map { it.value!! }
            if (others.isNotEmpty() &&
                others.max() - others.min() <= MAX_SPAN &&
                thumbF <= others.min() &&
                others.min() - thumbF <= MAX_THUMB_REACH
            ) {
                return true
            }
        }
        return false
    }

    private fun frettedCountOk(diagram: List<Int?>): Boolean {
        val positives = diagram.filterNotNull().filter { it > 0 }
        if (positives.isEmpty()) return true
        return minFingersRequired(diagram) <= 4
    }

    private fun minFingersRequired(diagram: List<Int?>): Int {
        val n = diagram.size
        val INF = 99
        val dp = IntArray(n + 1) { INF }
        dp[n] = 0
        for (i in n - 1 downTo 0) {
            val v = diagram[i]
            if (v == null || v == 0) {
                dp[i] = dp[i + 1]
                continue
            }
            val f = v
            var best = INF
            for (j in i until n) {
                val w = diagram[j]
                if (w == 0 || (w != null && w > 0 && w != f)) break
                if (w == f) {
                    val candidate = 1 + dp[j + 1]
                    if (candidate < best) best = candidate
                }
            }
            dp[i] = best
        }
        return dp[0]
    }

    private fun score(diagram: List<Int?>): Int {
        val played = playedIndices(diagram)
        val diagramPcs = pcsForDiagram(diagram)
        val noteCount = played.size
        val distinct = diagramPcs.size
        var score = 0
        score += style.noteCurve[noteCount] ?: style.defaultNotePenalty
        score += distinct * 28

        val bassInterval = bassIntervalForDiagram(diagram)
        if (bassInterval == 0) score += style.rootBias else score -= 150

        val hasExtension = chord.intervals.any { it in setOf(13, 14, 15, 18, 20, 21) }
        val hi = diagram.size - 1
        val hiPrev = diagram.size - 2
        if (hasExtension && diagram.size >= 2) {
            if (diagram[hi] == null) {
                score += 60
            } else if (diagram[hi] == diagram[hiPrev] && diagram[hi]!! > 0) {
                score -= 70
            }
        }

        for (i in 0 until diagram.size - 1) {
            val di = diagram[i]
            if (di != null && di > 0) {
                for (j in (i + 1) until minOf(i + 3, diagram.size)) {
                    val dj = diagram[j]
                    if (dj != null && dj > 0 && di > dj + 1) {
                        score -= (di - dj) * 50
                    }
                }
            }
        }

        val positives = diagram.filterNotNull().filter { it > 0 }
        val maxFret = if (positives.isNotEmpty()) positives.max() else 0
        val openCount = diagram.count { it == 0 }
        if (maxFret >= style.openPenaltyThreshold) score -= openCount * style.openPenaltyPer

        if (diagram.size >= 2 && diagram[hi] != null && diagram[hiPrev] == null) score -= 40

        if (diagram.size == 6) {
            val bonuses = TEMPLATE_BONUSES[chord.symbol]
            val key = diagramToKey(diagram)
            bonuses?.get(key)?.let { score += it }
        }

        score -= minFingersRequired(diagram) * 10
        return score
    }

    private fun sortKey(score: Int, diagram: List<Int?>): List<Int> {
        val played = playedIndices(diagram)
        val positives = diagram.filterNotNull().filter { it > 0 }
        val maxF = if (positives.isNotEmpty()) positives.max() else 0
        val minF = if (positives.isNotEmpty()) positives.min() else 0
        val span = if (maxF != 0) maxF - minF else 0
        return listOf(-score, -played.size, span, maxF) +
            diagram.map { f -> f ?: -1 }
    }
}

fun searchChord(tuning: ParsedTuning, chord: ParsedChord): List<VoicingShape> {
    val engine = BiomechanicalEngine(tuning, chord)
    val ranked = engine.generate(RESULTS_PER_CHORD)
    return ranked.map { (score, diagram) -> makeShape(diagram, tuning, chord, score) }
}
