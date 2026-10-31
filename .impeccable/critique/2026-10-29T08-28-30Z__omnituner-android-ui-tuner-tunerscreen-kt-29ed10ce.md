---
target: Tuner page, landscape
total_score: 25
max_score: 40
na_heuristics: 
p0_count: 0
p1_count: 3
target_identity: "file:C:\\Projects\\OmniTuner-Android-ver\\app\\src\\main\\java\\com\\omnituner\\android\\ui\\tuner\\TunerScreen.kt"
target_fingerprint: "sha256:6eb79906ef1d52df248efbe2c96050fdbf6a6fe548f1704f6eac685204a93d2f"
target_path: "C:\\Projects\\OmniTuner-Android-ver\\app\\src\\main\\java\\com\\omnituner\\android\\ui\\tuner\\TunerScreen.kt"
timestamp: 2026-10-29T08-28-30Z
slug: omnituner-android-ui-tuner-tunerscreen-kt-29ed10ce
---
# Critique: Tuner, landscape (TunerScreen.kt + TuningPicker.kt + TuningEditor)

Method: design review only (detector not applicable, native platform)

| # | Heuristic | Score | Key issue |
|---|---|---|---|
| 1 | Visibility of system status | 2 | Idle readout is a tiny grey dash; nothing says the mic is listening or which string is the target |
| 2 | Match system / real world | 3 | High string on top, cents ruler, Tune up/down all read naturally |
| 3 | User control and freedom | 3 | Sheet and editor exit cleanly |
| 4 | Consistency and standards | 2 | Tuner uses its own TuningPickerSheet, Chords/Scales use LibraryTuningSheet; docs promise an unfolded tuning list that the code no longer has |
| 5 | Error prevention | 2 | Custom tuning delete is one tap, no confirm or undo |
| 6 | Recognition over recall | 3 | Recent tunings; but Recent rows lead with the instrument, not the tuning |
| 7 | Flexibility and efficiency | 3 | Auto/Manual, recent list |
| 8 | Aesthetic and minimalist | 2 | Large dead zone mid-panel; Auto floats mid-panel |
| 9 | Error recovery | 3 | Mic permission/error states are clear with an action |
| 10 | Help and documentation | 2 | Auto switch unexplained; manual mode has no cue |
| Total | | 25/40 | Acceptable |

## Priority issues
1. [P1] Idle landscape readout is a void: note falls back to a ~56sp grey "—", frequency and status empty, SpaceBetween pushes everything apart. Fix: show the target string (manual) or "Play a string" (auto) as a ghosted readout; add a quiet listening cue.
2. [P1] Auto switch floats mid-panel: Spacer(weight 1) + picker weight(1f, fill=false) leaves the picker's unused share at the end of the row. Fix: drop the Spacer, use Arrangement.SpaceBetween.
3. [P1] Not glanceable at stand distance: note capped at maxHeight*0.26 (about 56-60dp on a phone), meter labels 11sp at 45-75% alpha. Landscape has width to spare; let the note take ~45% of panel height and push labels to labelMedium.
4. [P2] Tuning sheet in landscape: ~2.5 rows visible, Create and All instruments below the fold; Recent rows repeat "Guitar 6-string" as headline and accent the instrument not the tuning; separate from LibraryTuningSheet used on Chords/Scales.
5. [P2] Docs drift + stale comments: AGENTS.md/DESIGN.md say the landscape Tuner unfolds TuningMenuItems on the right; code has none. Orphan comment at TunerScreen.kt:600, wrong KDoc on AutoSwitch (:271), TuningPickerSheet KDoc says "upright".

## Minor
- Custom tuning delete in the sheet has no confirm/undo.
- Settings gear sits above the rail, not level with the panel header.
- Tuning editor landscape: 6th string below the fold; left column half empty.
- Tuned-check icon 14dp in a ~40dp chip is crowded.
