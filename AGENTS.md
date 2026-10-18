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
- Option selection goes through the shared pickers in `ui/common/OptionSheet.kt`: `MenuPickerRow` (a row whose value opens an anchored stock `DropdownMenu`, iOS pull-down style, for short lists such as the Settings choices), or `PickerRow` (the trigger row) + `OptionSheet` (a `ModalBottomSheet` list with optional sections, trailing actions and a footer) for long or sectioned lists. Every other anchored menu (a panel header like the tuner's tuning menu or the metronome's meter and preset menus, any custom trigger) is built from `PullDownMenu` + `PullDownMenuItem` / `PullDownMenuSection` / `PullDownMenuDivider` in the same file, never a raw `DropdownMenu`, so all menus share width, shape and check (Settings passes the group card color as `menuColor`; elsewhere menus keep the theme default). Do not build custom dropdown, wheel, or spinner components. The exceptions are the Metronome's tempo readout, `BpmWheel` (`ui/metronome/BpmWheel.kt`): a slider can't land on an exact BPM across 1–800 and M3 has no wheel; and its sideways sibling `NoteWheel` (`ui/common/NoteWheel.kt`), a compact looping drum for picking a note (the Scales root). Reuse these rather than adding another.
- Bottom navigation is the stock `NavigationBar` holding custom tabs (`NavTabs` in `ui/AppRoot.kt`): one sliding pill behind icon + label, outlined icon when unselected and filled when selected. This is the one deliberate exception to stock items. In landscape the bar becomes a stock `NavigationRail` holding the same tabs stood on end (`NavRailTabs`), on the phone's physical bottom edge (it swaps sides with the rotation), and the rail takes over the top bar there: no title, the Settings button in its header. Settings itself keeps its top bar and has no rail.
- Landscape layouts: a screen decides with `BoxWithConstraints` and `maxWidth > maxHeight`, not the configuration. The fixed panels split: the Tuner runs its strings down the left edge (highest on top), the readout in the middle and its tuning menu unfolded into a list on the right (`TuningMenuItems`, the same rows as the pull-down); the Metronome puts the readout over a large Tap in one half and the menus over a Start block filling the rest in the other. Results are fitted to the screen, not scrolled to: Chords puts the input card (with the key and display options in a bar of its own, under the chord field and above Generate) beside one chord card at a time filling the results pane (snap-paged sideways, the diagram widening its fret window via `neckDiagramColumnsFor`); Scales fits its map card to the screen (`FretboardCanvas(fitHeight = true)`, tuning on the title line, no tone row) and pairs the other cards in columns below it; grouped lists cap their width (Settings, 640 dp).
- All rounded shapes are continuous "squircle" corners: `SmoothShape` (`ui/theme/SmoothShape.kt`, via AndroidX graphics-shapes), never `RoundedCornerShape`. `MaterialTheme.shapes` is built from it, so cards, dialogs, sheets, menus, text fields and chips get it automatically; fully rounded components don't read the theme, so pass `shape = SmoothShape.Full` to `Button` / `FilledTonalButton` / `TextButton`. True circles (icon buttons, swatches, dots) stay `CircleShape`.
- Top-bar actions (navigation icon and `actions`) are `FilledTonalIconButton`s, not bare `IconButton`s. On the Settings page the tonal container matches the grouped-list page color, so there it takes `LocalGroupedListColors.current.group` via `IconButtonDefaults.filledTonalIconButtonColors(containerColor = ...)`.
- Single-choice segmented controls use `SegmentedRow` (`ui/common/SegmentedRow.kt`): stock segments without the check icon, the selected one filled with the accent, 16 dp squircle ends. Do not build a `SingleChoiceSegmentedButtonRow` by hand.
- Numeric adjustments use `NumberStepper` (`ui/common/NumberStepper.kt`), which repeats while held.
- Reuse `SwitchRow` (`ui/common/SwitchRow.kt`) for boolean settings.
- Transient indicators (progress bars, spinners, "copied" ticks, busy states) must never shift the layout: overlay them (`Box` + `align`/`offset`) or swap them into space that is always reserved; never add or remove a row that pushes the content below it. Example: the Chords generate progress bar floats in the gap under the input card.

## Colors & theme

- **Minimal accent.** `MaterialTheme.colorScheme` is a neutral grey scheme (`ui/theme/Theme.kt`), so stock components render black/white. The user's accent (Settings → Accent color: match wallpaper by default, or a preset) lives in `LocalAccentColors` and is used only where it carries meaning: the tuner needle, the in-tune note, the metronome's sounding beat dot, every `Switch` and `Slider` (via `accentSwitchColors()` / `accentSliderColors()` in `ui/theme/AccentControls.kt`), the selected segment of every `SegmentedRow`, the Scales root (filled on the board, outlined in the tone row, the centre of the root wheel), the active/tuned string chips, the selected bottom-nav tab's icon and label (its pill stays neutral, from `LocalNavBarColors`), and in the Chords section (copied from the web app) the chord diagram's root ring, an "altered" numeral chip and a "moderate" key-finder result. Do not spread it to other chrome.
- Settings is an inset grouped list (iOS-style): `LocalGroupedListColors` gives the page (#F3F3F2 light / black dark) and group card (white / #1C1C1C) colors, section titles use `onSurfaceVariant`. Rows inside cards or sheets are `ListItem`s with a transparent container so the card color shows through.
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
