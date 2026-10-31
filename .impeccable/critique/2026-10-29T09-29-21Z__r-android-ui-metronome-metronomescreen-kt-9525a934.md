---
target: Metronome page, landscape
total_score: 26
max_score: 40
na_heuristics: 
p0_count: 0
p1_count: 2
target_identity: "file:C:\\Projects\\OmniTuner-Android-ver\\app\\src\\main\\java\\com\\omnituner\\android\\ui\\metronome\\MetronomeScreen.kt"
target_fingerprint: "sha256:7e594e454be9566d36f12173e049288e70a0d86a73ff25980770d52296a5d846"
target_path: "C:\\Projects\\OmniTuner-Android-ver\\app\\src\\main\\java\\com\\omnituner\\android\\ui\\metronome\\MetronomeScreen.kt"
timestamp: 2026-10-29T09-29-21Z
slug: r-android-ui-metronome-metronomescreen-kt-9525a934
---
Method: design review only (detector not applicable, native platform). Target: Metronome, landscape (MetronomeScreen.kt TempoPanel landscape branch), light theme, pink accent, stopped.

## Design Health Score

| # | Heuristic | Score | Key Issue |
|---|-----------|-------|-----------|
| 1 | Visibility of System Status | 3 | Beat dots are tiny and grey at the top edge; while playing, the lit dot sits next to a huge accent slab and gets lost |
| 2 | Match System / Real World | 3 | "Beat only" as the meter's caption is cryptic; "Andante" is a nice touch |
| 3 | User Control and Freedom | 3 | One-tap start/stop, steppers, wheel, type-in; nothing to trap you |
| 4 | Consistency and Standards | 2 | Tuner landscape gives the readout the full height; here it is small. Meter (the page title) sits mid-panel. Tap circle vs Start block |
| 5 | Error Prevention | 3 | The + stepper sits ~20 dp from the meter picker across the column seam |
| 6 | Recognition Rather Than Recall | 2 | Folded, nothing says the number is a wheel or that tapping it types a tempo |
| 7 | Flexibility and Efficiency | 3 | Wheel, repeating steppers, tap, type-in, presets |
| 8 | Aesthetic and Minimalist Design | 2 | Dead band between caption and Tap; the button outweighs the readout |
| 9 | Error Recovery | 3 | Tempo dialog flags out-of-range with the range shown |
| 10 | Help and Documentation | 2 | No hints for the hidden gestures |
| **Total** | | **26/40** | **Acceptable** |

## Design Specificity Verdict
Half-authored. The left half (beat dots, accent BPM that kicks on the beat, the folding BpmWheel, "Andante") is clearly OmniTuner's metronome. The right half is a generic "header + giant colored button" a timer or recorder app would use unchanged. The Tuner's landscape (readout takes the panel's full height) is not echoed here, so the two siblings no longer look like units on one pedalboard.
Deterministic scan: not applicable (native platform).

## Overall Impression
Works and is easy to start, but the hierarchy is upside down: the loudest object is the button you press once; the number you glance at from a music stand is about an eighth of the screen height. Biggest opportunity: make the BPM the hero, as the Tuner's note now is, and shrink the Start slab to a strong but secondary block.

## What's Working
- Start is huge and one-thumb reachable with the phone on a stand.
- Left/right split maps to "set the tempo" / "run it".
- The panel is one Board Grey surface with concentric inner corners; flat, quiet, on-system.

## Priority Issues
1. [P1] BPM readout is too small for landscape. ~45 px of a 375 px screen; bpmSize capped at 30% of height x 0.85 and the wheel band claims the rest. Fix: size the folded value to the free height of the left column (as the Tuner's note), unfold the wheel over it when turned. Command: /impeccable layout
2. [P1] Start slab drowns the beat signal. ~45% of the panel in solid accent; the sounding beat dot (same accent, ~10 dp) is the musical signal and loses. Fix: shrink Start (e.g. ~half the right column, or a tall pill at the bottom), make beat dots larger in landscape. Command: /impeccable quieter
3. [P2] Left column dead zone and misaligned halves. Readout in top third, Tap pinned bottom, empty band between (incl. reserved empty status line); dots/85 don't align with the header row. Fix: header row across the full panel width (meter leading, preset + options trailing); readout + steppers centered in remaining height; Tap and Start share one bottom row and height. Command: /impeccable layout
4. [P2] Meter, the page's title, doesn't lead. Header grammar puts it first; here it starts mid-screen, after the + stepper which crowds it. Fix: full-width header row (same as 3). Command: /impeccable layout
5. [P2] Hidden wheel / type-in affordances. Folded, the number shows no hint it's draggable or tappable. Fix: faint neighbour numbers or a subtle chevron hint when folded in landscape. Command: /impeccable clarify

## Persona Red Flags
- Gigging guitarist, phone on a stand 1 m away: can read "Start" from across the room but not the BPM; the beat dots are grey at rest and small when lit.
- Sam (low vision): default pink #FC48B3 on #F3F3F2 is ~2.8:1, below 3:1 for large text, for the BPM. White Tap on the light panel barely separates (~1.1:1 fill step). Inactive dots at 14-28% alpha nearly vanish.
- Jordan (first-timer): "Beat only" reads as a mode; "Preset None" spends a header slot on an empty state; no clue the number can be dragged or tapped.

## Minor Observations
- Tap (circle, white) and Start (squircle block, accent) speak two languages for the two actions.
- The + stepper and the 4/4 picker read as one group.
- Empty status line when stopped reserves height that shows as the dead band; fine for No Shift, but it could hold "Tap to set tempo" when stopped.

## Questions to Consider
- What if the right column were the readout and the left the controls (readout nearest the rail/thumb)?
- Does Start need to be bigger than Tap, which is pressed many times in a row?
- Should landscape show the bar/beat count huge while playing, since nobody changes tempo mid-take?
