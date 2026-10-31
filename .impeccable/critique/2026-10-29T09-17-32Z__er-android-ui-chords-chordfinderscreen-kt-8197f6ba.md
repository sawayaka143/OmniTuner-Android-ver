---
target: Chords chord card landscape
total_score: 25
max_score: 40
na_heuristics: 
p0_count: 0
p1_count: 2
target_identity: "file:C:\\Projects\\OmniTuner-Android-ver\\app\\src\\main\\java\\com\\omnituner\\android\\ui\\chords\\ChordFinderScreen.kt"
target_fingerprint: "sha256:cd0ee4dbe6b7606d52e8d3abf9b7dead198aeedf3b12b8116adc05b123734ca4"
target_path: "C:\\Projects\\OmniTuner-Android-ver\\app\\src\\main\\java\\com\\omnituner\\android\\ui\\chords\\ChordFinderScreen.kt"
timestamp: 2026-10-29T09-17-32Z
slug: er-android-ui-chords-chordfinderscreen-kt-8197f6ba
---
# Critique: Chords chord card, landscape (ChordFinderScreen.kt ChordCard + NeckDiagram.kt)

Method: design review only (detector not applicable, native platform)

Score 25/40 (Acceptable). H1 2, H2 3, H3 3, H4 3, H5 3, H6 2, H7 2, H8 2, H9 3, H10 2.

## Priority issues
- [P1] Three stacked header lines (name+numeral, "maj . G B D", "FINGERING 1") take ~30% of a height-bound card; the diagram shrinks to match and leaves gutters. Fix: one header line in landscape; drop the tones line (rings already name every note) or show it only when labels are degrees.
- [P1] Diagram is locked to its aspect ratio (COL=30 fixed), so once height-bound it centres with ~20% empty on each side. Fix: when fitted, widen fret columns to fill the width (rows set by height, columns by width), clamped to a max ratio.
- [P2] Fingerings scroll vertically inside a horizontally paged card, labelled "FINGERING 1" with no total. Two-axis navigation with no position cue. Fix: "1 / 3" counter in the header row (plus dots or a chevron), drop the caps label.
- [P2] Tap-to-play on the diagram is invisible. Fix: a small play icon in the header row (landscape has the room).

## Minor
- "maj" duplicates the symbol "G"; the quality word is only useful for unusual symbols.
- Index prefix "2." competes with the symbol; could become a faint counter.
- Fret numbers row is fine; keep.
