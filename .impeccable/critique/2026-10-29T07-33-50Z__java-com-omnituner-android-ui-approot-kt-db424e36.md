---
target: bottom nav bar and top-bar headers
total_score: 24
max_score: 36
na_heuristics: 9
p0_count: 0
p1_count: 2
target_identity: "file:C:\\Projects\\OmniTuner-Android-ver\\app\\src\\main\\java\\com\\omnituner\\android\\ui\\AppRoot.kt"
target_fingerprint: "sha256:aaf7850ac30ceadf87b38d66ae909713b131afd7a02fc53f21caea024cdde2c7"
target_path: "C:\\Projects\\OmniTuner-Android-ver\\app\\src\\main\\java\\com\\omnituner\\android\\ui\\AppRoot.kt"
timestamp: 2026-10-29T07-33-50Z
slug: java-com-omnituner-android-ui-approot-kt-db424e36
---
Method: design review only (detector not applicable, native platform). Scope: bottom nav bar / rail and the top-bar headers (menus, toggles, buttons). Evidence: source only (AppRoot.kt, TopBarSlots, TopBarPicker, OptionsButton, each tab's TopBarContent).

## Design Health Score
| # | Heuristic | Score | Key Issue |
|---|-----------|-------|-----------|
| 1 | Visibility of System Status | 3 | Header pickers show tuning/scale/meter/preset; Chords shows only "Chords" |
| 2 | Match System / Real World | 3 | Nav icons are media-player glyphs; "Beat only" caption over 4/4 |
| 3 | User Control and Freedom | 3 | Travelling Settings/Back, predictive back; preset delete has no undo |
| 4 | Consistency and Standards | 2 | Three header trigger components; instrument/tuning shown three ways; Chords options button hides and moves |
| 5 | Error Prevention | 3 | Pickers constrain choices |
| 6 | Recognition Rather Than Recall | 3 | Values visible; tune and gear are identical adjacent circles |
| 7 | Flexibility and Efficiency | 3 | Rail follows the physical edge; no tab swipe |
| 8 | Aesthetic and Minimalist Design | 2 | Scales/Metronome bars: two equal titleLarge values + two circles at 360 dp |
| 9 | Error Recovery | n/a | No error states originate in nav/headers |
| 10 | Help and Documentation | 2 | Captions are the only help |
| Total | | 24/36 | Acceptable (67%) |

## Design Specificity Verdict
Nav pill/spring/16 dp alignment/rail-on-physical-edge is authored; icons (GraphicEq, LibraryMusic, QueueMusic, Timer) are generic media-player glyphs. Header concept (title = what you're on) is strong but each tab built its own version.

## Priority Issues
- [P1] No shared header grammar: TopBarPicker / Metronome HeaderMenuTrigger / Tuner TuningButton; Tuner instrument-big/tuning-small vs Scales instrument-small/tuning-big vs Chords inline in card; Chords options is a raw FilledTonalIconButton, hidden on Key Finder, shifts 48 dp when More appears. Fix: one HeaderPicker, written rule (title = context, actions = max one secondary picker, Options, Settings), Chords tuning to title, OptionsButton in a fixed slot. Command: extract + layout.
- [P1] Scales/Metronome bars: two equal titleLarge SemiBold values + two circles; title truncates first at 360 dp. Fix: smaller secondary value style; Metronome Preset into the options sheet. Command: distill + adapt.
- [P2] Options (tune) and Settings (gear) identical adjacent tonal circles. Fix: labelled Options pill or separation. Command: clarify.
- [P2] Nav: media-player icons; unselected tabs onSurface not Muted Label (Black accent nearly indistinguishable); "Metronome" ellipsizes at font scale ~1.3. Fix: custom vector set (ask), align color with DESIGN.md, shrink label. Command: delight + polish.
- [P2] Title text starts ~32 dp on picker tabs vs 16 dp on Chords and cards. Fix: offset picker by its 16 dp start padding. Command: layout.

## Persona Red Flags
- Jordan: icon-only reading says EQ/library/playlist/timer; tune vs gear; "Beat only" opens Meter.
- Casey: all pickers + Auto in the top bar; Start/Tap at bottom.
- Maya (DADGAD on a stand): tuning shown three ways on three tabs; unclear whether it's shared.

## Minor Observations
- Arrow flips under the sheet scrim (unseen); Tuner's never flips.
- Preset delete without undo.
- Chords More scaleIn shifts the action row.
- Auto switch is a "how" in a "what" header.

## Questions to Consider
- Should Scales' header read "C Major", fusing root and scale?
- Is tuning one shared choice? Then one header style.
- What nav icons would only a guitarist recognise?
