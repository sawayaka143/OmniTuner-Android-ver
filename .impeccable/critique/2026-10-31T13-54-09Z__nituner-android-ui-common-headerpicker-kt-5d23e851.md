---
target: upright header pickers vs main card
total_score: 24
max_score: 36
na_heuristics: 9
p0_count: 0
p1_count: 2
target_identity: "file:C:\\Projects\\OmniTuner-Android-ver\\app\\src\\main\\java\\com\\omnituner\\android\\ui\\common\\HeaderPicker.kt"
target_fingerprint: "sha256:4378a43e77b39c34fa2ffdf79ac8a51aa1d63604b69279e8c760764798a77665"
target_path: "C:\\Projects\\OmniTuner-Android-ver\\app\\src\\main\\java\\com\\omnituner\\android\\ui\\common\\HeaderPicker.kt"
timestamp: 2026-10-31T13-54-09Z
slug: nituner-android-ui-common-headerpicker-kt-5d23e851
---
# Critique: upright header pickers (all four tabs) vs. pickers on the main card

Method: design review only (detector not applicable, native platform)

| # | Heuristic | Score | Key issue |
|---|---|---|---|
| 1 | Visibility of system status | 3 | Current instrument/tuning/scale/meter always shown |
| 2 | Match real world | 3 | "Preset: None" promotes an empty state to the header |
| 3 | User control | 3 | Pickers are sheets, easy to back out |
| 4 | Consistency | 2 | Tuner puts the picker on the card, the other three in the bar; upright vs landscape use two grammars |
| 5 | Error prevention | 3 | Library-only tunings |
| 6 | Recognition | 3 | Captions name each picker |
| 7 | Flexibility | 3 | n/a-ish; fine |
| 8 | Aesthetic / minimalist | 2 | Unanchored bold text floating on the white page, competing with the card content |
| 9 | Error recovery | n/a | No error paths in a header picker |
| 10 | Help | 2 | Captions only |
| Total | | 24/36 | Acceptable (67%) |

Verdict: move the context pickers onto the main card's header on every tab (the Tuner pattern), Options with them; leave only Settings in the top bar. This matches the landscape panel headers, so one grammar for both orientations, and fits the Pedalboard north star (each unit carries its own controls).

Priority issues:
- [P1] Floating header text: two titleLarge bold values on the bare page with no container, visually disconnected from the grey card they control. Fix: card header row.
- [P1] Two header grammars: Tuner picker on card vs others in bar; upright vs landscape. Fix: one card-header grammar.
- [P2] Scales/Metronome two-picker bars: equal weight, no clear title; right picker caption reads centred (Preset) because the end-aligned caption stops before the arrow.
- [P2] Chords action cluster: Options + overflow + Settings = three round buttons plus a picker.
- [P3] Lone Settings gear on Tuner leaves a dead 64 dp band (will become the norm after the move: decide whether that band is acceptable or Settings moves too).

Questions: does Chords' tuning belong on the input card (and hide in Key finder)? Should Scales portrait reintroduce the map card title line? Is "Preset: None" worth header space at all?
