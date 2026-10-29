# OmniTuner — agent instructions

## Workflow

- Commits: one coherent unit of work (one feature, one fix, one refactor chunk) per commit; stage only the files belonging to it (leave unrelated churn, e.g. `.idea/`, out), and follow the repo's commit message style (see `git log`): short milestone-ish prefix + concise description.

## Build & verification

- JDK: `C:\Program Files\Android\Android Studio\jbr` (set `JAVA_HOME` for Gradle).
- Compile check: `.\gradlew.bat :app:compileDebugKotlin`
- Lint: `.\gradlew.bat :app:lintDebug` (must pass clean; if lint fails, fix the cause, do not suppress)
- APK output (AGP 9): `app\build\intermediates\apk\debug\app-debug.apk`
- **Never install, launch, screenshot, or drive the app on the emulator** — no `adb install`, `am start`, `uiautomator dump`, `screencap`, or `input` taps/swipes. The user performs all visual and functional checks on the emulator themselves. Agent verification ends at compile + lint (+ unit tests); when those pass, report the step as ready for the user to review.

## UI patterns

- The app is **native Material 3**: `Scaffold` + `TopAppBar` + `NavigationBar`, M3 `Card`, `ListItem`, `Switch`, `Slider`, `FilterChip`, `SingleChoiceSegmentedButtonRow`, `AlertDialog`, `ModalBottomSheet`. Do not reintroduce custom look-alikes of web components.
- Option selection goes through the shared pickers in `ui/common/OptionSheet.kt`: `MenuPickerRow` (a row whose value opens an anchored stock `DropdownMenu`, iOS pull-down style, for short lists such as the Settings choices), or `PickerRow` (the trigger row) + `OptionSheet` (a `ModalBottomSheet` list with optional sections, trailing actions and a footer) for long or sectioned lists. Pickers opened from a top bar or panel header (the Scales scale and tuning, the metronome's meter and presets, the Chords display options) open a bottom sheet, not an anchored menu: `OptionSheet` for a pick-one list (it closes itself with a slide-down after a pick; per-row actions go in `trailing`, a Save-style button in `footer`), `LibraryTuningSheet` for instrument + tuning, or a `ModalBottomSheet` headed by `SheetTitle` for a sheet of mixed controls that stays open while you adjust it. Anchored menus are left for inline grouped-list rows (`MenuPickerRow`) and action overflow menus (the Chords "More"); those are built from `PullDownMenu` + `PullDownMenuItem` / `PullDownMenuSection` / `PullDownMenuDivider` in the same file, never a raw `DropdownMenu`, so all menus share width, shape and check (Settings passes the group card color as `menuColor`; elsewhere menus keep the theme default). Do not build custom dropdown, wheel, or spinner components. The exceptions are the Metronome's tempo readout, `BpmWheel` (`ui/metronome/BpmWheel.kt`): a slider can't land on an exact BPM across 1–800 and M3 has no wheel; and its sideways sibling `NoteWheel` (`ui/common/NoteWheel.kt`), a compact looping drum for picking a note (the Scales root). Reuse these rather than adding another.
- Bottom navigation is the stock `NavigationBar` holding custom tabs (`NavTabs` in `ui/AppRoot.kt`): one sliding pill behind icon + label, outlined icon when unselected and filled when selected. This is the one deliberate exception to stock items. In landscape the bar becomes a stock `NavigationRail` holding the same tabs stood on end (`NavRailTabs`), on the phone's physical bottom edge (it swaps sides with the rotation), and the rail takes over the top bar there: no title, the Settings button in its header. Settings itself keeps its top bar and has no rail.
- Landscape layouts: a screen decides with `BoxWithConstraints` and `maxWidth > maxHeight`, not the configuration. The fixed panels split: the Tuner runs its strings down the left edge (highest on top), the readout in the middle and its tuning menu unfolded into a list on the right (`TuningMenuItems`, the same rows as the pull-down); the Metronome puts the readout over a large Tap in one half and the menus over a Start block filling the rest in the other. Results are fitted to the screen, not scrolled to: Chords puts the input card (with the key and display options in a bar of its own, under the chord field and above Generate) beside one chord card at a time filling the results pane (snap-paged sideways, the diagram showing one fret either side of the shape and centred in the pane); Scales fits its map card to the screen (`FretboardCanvas(fitHeight = true)`, tuning on the title line, no tone row) and pairs the other cards in columns below it; grouped lists cap their width (Settings, 640 dp).
- All rounded shapes are continuous "squircle" corners: `SmoothShape` (`ui/theme/SmoothShape.kt`, via AndroidX graphics-shapes), never `RoundedCornerShape`. `MaterialTheme.shapes` is built from it, so cards, dialogs, sheets, menus, text fields and chips get it automatically; fully rounded components don't read the theme, so pass `shape = SmoothShape.Full` to `Button` / `FilledTonalButton` / `TextButton`. True circles (icon buttons, swatches, dots) stay `CircleShape`.
- One home for options: a tool's page shows its instrument and at most one primary action; everything that adjusts it goes in that tool's options sheet behind `OptionsButton` (`ui/common/OptionsButton.kt`, the tune icon). Upright it sits in the top bar; in landscape, on the panel or card header (Metronome panel header, Scales map title line, Chords input card bar). The Tuner has no options sheet; its settings live in Settings. Scales is used mainly in landscape.
- Header grammar (the top bar upright; the panel or card header in landscape): the title says what the page is on or in, as a `HeaderPicker` (`ui/common/HeaderPicker.kt`, a caption over the value with a drop-down arrow): the instrument and tuning on the Tuner and Chords, root + scale as one phrase on Scales ("E Minor"), the meter on the Metronome. Instrument and tuning always go through `TuningHeaderPicker` (instrument as the caption, tuning as the value), so they read the same on every tab. At most one secondary picker shares the bar (the Scales tuning, the Metronome preset), at the same size, through `HeaderPickerRow` in the title slot: never put a picker in `actions`, which the top bar measures without a width limit, so a long name pushes the title and buttons off screen. The row lets the longer of the two give way with an ellipsis, the title keeping at least half. The actions are `OptionsButton`, then Settings. Don't build another header trigger. The picker sets its own colors because the top bar's actions slot greys whatever it holds. Upright, the page doesn't repeat what the header names (the Scales map card has no title line); in landscape, where the rail replaces the bar, the panel or card carries it. Current exceptions: the Tuner's Auto switch and the Chords overflow menu.
- Top-bar actions (navigation icon and `actions`) are `FilledTonalIconButton`s, not bare `IconButton`s. On the Settings page the tonal container matches the grouped-list page color, so there it takes `LocalGroupedListColors.current.group` via `IconButtonDefaults.filledTonalIconButtonColors(containerColor = ...)`.
- Single-choice segmented controls use `SegmentedRow` (`ui/common/SegmentedRow.kt`): equal segments on a soft neutral track (no outline or dividers), one accent pill behind the selected label that springs between segments like the bottom nav's pill, 16 dp squircle track with a concentric 12 dp pill. Do not build a `SingleChoiceSegmentedButtonRow` or another segmented control by hand.
- Numeric adjustments use `NumberStepper` (`ui/common/NumberStepper.kt`), which repeats while held.
- Reuse `SwitchRow` (`ui/common/SwitchRow.kt`) for boolean settings.
- Transient indicators (progress bars, spinners, "copied" ticks, busy states) must never shift the layout: overlay them (`Box` + `align`/`offset`) or swap them into space that is always reserved; never add or remove a row that pushes the content below it. Example: the Chords generate progress bar floats in the gap under the input card.

## Colors & theme

- **Minimal accent.** `MaterialTheme.colorScheme` is a neutral grey scheme (`ui/theme/Theme.kt`), so stock components render black/white. The user's accent (Settings → Accent color: match wallpaper by default, or a preset) lives in `LocalAccentColors` and is used only where it carries meaning: the tuner needle, the in-tune note, the metronome's sounding beat dot, its BPM readout and Start button, every `Switch` and `Slider` (via `accentSwitchColors()` / `accentSliderColors()` in `ui/theme/AccentControls.kt`), the selected segment of every `SegmentedRow`, the Scales root (filled on the board, outlined in the tone row, the centre of the root wheel), the active/tuned string chips, the selected item of every picker (menu or sheet: its check and label), the selected bottom-nav tab's icon and label (its pill stays neutral, from `LocalNavBarColors`), and in the Chords section (copied from the web app) the chord diagram's root ring, an "altered" numeral chip and a "moderate" key-finder result. Do not spread it to other chrome.
- Settings is an inset grouped list (iOS-style): `LocalGroupedListColors` gives the page (#F3F3F2 light / black dark) and group card (white / #1C1C1C) colors, section titles use `onSurfaceVariant`. Rows inside cards or sheets are `ListItem`s with a transparent container so the card color shows through.
- Every menu, sheet and dialog wears the Settings look: sheets sit on the grouped-list page color with their rows in `GroupedSection` cards (inset dividers between rows), anchored menus and dialogs use the group color, titles are `headlineSmall` SemiBold, section titles grey `bodyLarge`, checks 24 dp. No surface uses `surfaceContainer*` greys.
- Hex values live only in `ui/theme/Theme.kt`. Screens read `MaterialTheme.colorScheme` or `LocalAccentColors`.
- Semantic roles: in-tune = accent, out-of-tune = `error`, good/valid = `tertiary`. `LocalStatusColors` adds the web app's `warn` (sevenths/altered tones in the chord diagram, chromatic chords, weak key matches) and `info` (extensions, borrowed chords, chord-tone lines); they belong to the Chords section, not general chrome.

## Typography & icons

- Text uses the system font through `MaterialTheme.typography.*`. Do not bundle custom fonts.
- Icons use `androidx.compose.material.icons` (`Icons.Filled.*` / `Icons.AutoMirrored.Filled.*`; `Outlined` only for unselected nav tabs).

## State & data

- Settings are typed DataStore keys, wrapped by `SettingsStore` in `app/src/main/java/com/omnituner/android/data/`. Scalar values get typed keys; lists (metronome presets, custom instruments and tunings) are stored as kotlinx-serialization JSON in one key each.
- Pure domain logic (theory, pitch detection, metronome timing, validation rules) lives in `:shared` (commonMain) and has no Android or Compose dependency.

## Comments

- Comment where it genuinely helps (non-obvious intent, gotchas); do not narrate obvious code.
- Do not strip pre-existing comments in files you touch unless the user asks for a cleanup.

## Encoding & file editing

- **Never round-trip source files through PowerShell** (`Get-Content`/`Set-Content`/`Out-File`). Windows PowerShell 5.1 reads non-BOM UTF-8 as the ANSI codepage (mojibake) and `Set-Content -Encoding UTF8` writes a BOM — both have already corrupted this repo (see commit M11c). Use the file edit tools, or `[System.IO.File]::ReadAllText/WriteAllText` with an explicit `UTF8Encoding($false)`.
- Shell-typed commit messages stay ASCII (no em-dashes/arrows/special punctuation) — the console encoding mangles them and can break parsing.

## Environment notes

- Emulator flakiness: if installs fail with empty error codes or the app freezes on the splash screen, suspect the emulator, not the code — Cold Boot the AVD (Device Manager → ⋮ → Cold Boot Now) or relaunch with `-no-snapshot` before debugging app code.
