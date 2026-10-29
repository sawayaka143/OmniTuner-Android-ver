---
name: compose-ui-change
description: Procedure and gotchas for changing any OmniTuner screen in Jetpack Compose: Tuner, Metronome, Chords, Scales, Settings, the nav bar, pickers, sheets, menus and animations. Use for every UI fix, polish pass, new control, layout tweak, landscape adaptation or animation change, even small ones like "make the Tap button bigger" or "this menu looks off". Use it alongside impeccable when that skill handles the design direction. This skill covers how to land the change in this codebase. Not for pure domain logic in :shared.
---

# Changing the UI

`AGENTS.md` holds the design rules (components, colors, shapes, landscape). This skill is the order of work and the mistakes past sessions made anyway.

## Steps

1. **Find what already exists.** Before writing any control, list `app/src/main/java/com/omnituner/android/ui/common/` and grep for the closest match (`OptionSheet.kt` has `PullDownMenu`, `MenuPickerRow`, `PickerRow`, `OptionSheet`; also `SegmentedRow`, `NumberStepper`, `SwitchRow`, `NoteWheel`, `WheelPickerPage`, `GroupedSection`, `LibraryTuningSheet`, `TopBarPicker`). Reuse it or extend it with a parameter. The user has asked "why are we not using the same drop-down menus?" before.
2. **Scope check.** Change only what was asked. Any other visual change (color, border, size, spacing, copy) is a question for the user first. If the request is vague ("something feels off", "make it prettier"), run the `grilling` skill before coding.
3. **Make the change** in both orientations: the screen's `BoxWithConstraints` landscape branch usually needs the same edit. Portrait is the canonical layout. Landscape adapts it.
4. **Trace every new value to every consumer.** Example: the Scales root picker once updated the tone row but never reached `FretboardCanvas`, so the board stayed in C for weeks.
5. Run the `verify-build` skill, unless the user said "don't do any checks, just update the file".
6. Report what changed and say the visual check is theirs.

## Gotchas

- **No instructional copy.** No "Play a string", no "Enter a chord progression and the key is detected live", no hint captions, no descriptive tags like "simple" / "compound feel" / "fast" under options. The user removed every one of these. Labels name things. They don't coach.
- **No duplicated text.** If a value already shows in a title or chip, don't repeat it in a subtitle or caption.
- **Layout must not change between options.** Picking a different scale, meter or chord must not add or remove a row (the pentatonic scale once dropped a second tone row and the user called it out). Reserve the space or use a fixed slot. The same rule applies to transient indicators (see `AGENTS.md`).
- **Toggling a mode must not move unrelated content.** Pressing "Key: N" on Chords once shifted the diagrams. Check what sits below anything that changes size.
- **Look reference is iOS / the ChatGPT app**, built from native M3 parts. An Android-style full-width settings list was tried and rejected ("my design is way better"). Don't propose switching to stock Android look as a fix.
- **Animations:** the user finds defaults too quick and too stiff, and dislikes color cross-fades on selection. Match the existing springs instead of inventing new ones: nav pill `PillStiffness = 120f, PillDamping = 0.8f` in `ui/AppRoot.kt`, `SegmentedRow` pill `StiffnessLow / 0.75f`, wheel snaps `StiffnessMediumLow`, no bounce. Expanding sections animate their size (`MetronomeScreen.kt` `FoldSpring`).
- **Sizes the user asks for are exact.** "Twice as big" means 2×, "0.75 vertically" means 0.75. Don't round to a nicer dp.
- Black and white are pure in chrome: icons and inactive nav items are pure black (light) / pure white (dark). Greys come from `Theme.kt` / `LocalGroupedListColors`, never a new hex.
- UI strings are hardcoded Kotlin literals for now (no `strings.xml` yet; localization is planned). Don't start migrating strings inside an unrelated change.
- `remember` keyed on the wrong value is the usual cause of "it doesn't update" bugs. Check the keys before adding state.
