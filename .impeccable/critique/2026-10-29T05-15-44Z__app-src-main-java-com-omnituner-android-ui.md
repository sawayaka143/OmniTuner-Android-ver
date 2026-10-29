---
target: whole app (Tuner, Chords, Scales, Metronome)
total_score: 25
max_score: 40
na_heuristics: 
p0_count: 0
p1_count: 3
target_identity: "file:C:\\Projects\\OmniTuner-Android-ver\\app\\src\\main\\java\\com\\omnituner\\android\\ui"
timestamp: 2026-10-29T05-15-44Z
slug: app-src-main-java-com-omnituner-android-ui
---
Method: design review only (detector not applicable, native platform). Evidence: emulator screenshots (dark, phone portrait) of Tuner, Chords, Scales, Metronome and its options sheet, plus screen code.

## Design Health Score
| # | Heuristic | Score | Key Issue |
|---|-----------|-------|-----------|
| 1 | Visibility of System Status | 3 | Idle tuner shows only a grey bar, no "play a string" cue |
| 2 | Match System / Real World | 3 | "Beat only" over 4/4, "Poly", "4 readable" |
| 3 | User Control and Freedom | 3 | Metronome options sheet needs two Backs |
| 4 | Consistency and Standards | 2 | Options live in three different places; Notes/Degrees both sheet and inline |
| 5 | Error Prevention | 3 | Steppers/pickers constrain input; chord field validates live |
| 6 | Recognition Rather Than Recall | 2 | Three unlabeled icons on Chords; unlabeled options icon on Metronome |
| 7 | Flexibility and Efficiency | 3 | Tap tempo, presets, repeating steppers, shuffle |
| 8 | Aesthetic and Minimalist Design | 2 | Scales accent soup; Chords input stacks six controls |
| 9 | Error Recovery | 3 | Inline parse feedback; mic-denied state not seen |
| 10 | Help and Documentation | 1 | No contextual help for Polyrhythm, Bar mute, Tempo ramp, Tonic, degrees |
| Total | | 25/40 | Acceptable |

## Design Specificity Verdict
Instruments (needle meter, fretboard, neck diagram, BPM readout) are product-specific; the option chrome is generic settings-list rows used uniformly, which is where the crowding comes from. Deterministic scan: not applicable (native platform).

## Priority Issues
- [P1] Scales: settings as loud as the music (six accent marks; display settings inline). Fix: move frets, Notes/Degrees, Show outside scale, Sharps/Flats into a Display options sheet; keep map card + Root wheel. Command: distill Scales.
- [P1] Options live in a different place on each tool. Fix: one tune button in the same top-bar slot on every tab; page = instrument + one primary action. Command: layout.
- [P1] Chords: six controls before results; Shuffle/Play tonal containers invisible on the board-grey card; three unlabeled top-bar icons. Fix: tonic via the Key chip, page-color buttons, rely on Done to generate. Command: distill + clarify Chords.
- [P2] Metronome options sheet: 13 controls in 5 sections; duplicate "Polyrhythm" title; clipped chip row; lone bar cell; Back half-closes. Fix: Meter + Sounds open, niche features as switch rows that reveal their controls; single Back closes. Command: distill Metronome.
- [P2] Accent beyond DESIGN.md: BPM readout, Start, all segmented pills pink. Fix: record as deliberate or neutralize. Command: quieter.

## Persona Red Flags
- Jordan (first-timer): idle tuner gives no cue; "Tonic" / "4 readable" jargon; unlabeled Chords icons.
- Casey (phone on stand): Scales display controls push the board; sheet needs two Backs.
- Maya (gigging, alt tunings): tuning is changed three different ways across tabs.

## Minor Observations
- Metronome void between Tap and Start.
- "Beat only" eyebrow over 4/4 is ambiguous.
- Preset "None" looks disabled.
- Scales map card repeats scale and tuning from the top bar.
- Outlined Key chip is a one-off style.
- Idle tuner placeholder reads as a loading bar.

## Questions to Consider
- What if every tab were readout + one action, with everything else behind one tune button?
- Does a beginner need "Show outside scale" or "21 frets" on first open?
- If pink meant only "the music is talking", what would keep it?
