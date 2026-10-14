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
- Option selection goes through the shared pickers in `ui/common/OptionSheet.kt`: `PickerRow` (the trigger row) + `OptionSheet` (a `ModalBottomSheet` list with optional sections, trailing actions and a footer). Do not add new dropdown, wheel, or spinner components.
- Numeric adjustments use `NumberStepper` (`ui/common/NumberStepper.kt`), which repeats while held.
- Reuse `SwitchRow` (`ui/common/SwitchRow.kt`) for boolean settings.

## Colors & theme

- **Colors come only from `MaterialTheme.colorScheme`** (dynamic color on Android 12+, otherwise the M3 defaults). Do not add hex values or hand-built palettes to screens or to the theme.
- Semantic roles: in-tune = `primary`, out-of-tune = `error`, good/valid = `tertiary`, warning = `secondary`.

## Typography & icons

- Text uses the system font through `MaterialTheme.typography.*`. Do not bundle custom fonts.
- Icons use `androidx.compose.material.icons` (`Icons.Filled.*` / `Icons.AutoMirrored.Filled.*`).

## State & data

- Settings are typed DataStore keys, wrapped by `SettingsStore` in `app/src/main/java/com/omnituner/android/data/`. Scalar values get typed keys; lists (metronome presets, custom instruments and tunings) are stored as kotlinx-serialization JSON in one key each.
- Pure domain logic (theory, pitch detection, metronome timing, validation rules) lives in `:shared` (commonMain) and has no Android or Compose dependency.

## Comments

- **Never add comments with code changes.** No inline notes, no KDoc on new or changed members, no ported-from/design-token annotations — the user reviews diffs and does not want prose in them; names and structure must speak for themselves. Only write a comment when the user explicitly asks for one. This extends to new files: ship them comment-free.
- Do not strip pre-existing comments in files you touch unless the user asks for a cleanup.

## Encoding & file editing

- **Never round-trip source files through PowerShell** (`Get-Content`/`Set-Content`/`Out-File`). Windows PowerShell 5.1 reads non-BOM UTF-8 as the ANSI codepage (mojibake) and `Set-Content -Encoding UTF8` writes a BOM — both have already corrupted this repo (see commit M11c). Use the file edit tools, or `[System.IO.File]::ReadAllText/WriteAllText` with an explicit `UTF8Encoding($false)`.
- Shell-typed commit messages stay ASCII (no em-dashes/arrows/special punctuation) — the console encoding mangles them and can break parsing.

## Environment notes

- Emulator flakiness: if installs fail with empty error codes or the app freezes on the splash screen, suspect the emulator, not the code — Cold Boot the AVD (Device Manager → ⋮ → Cold Boot Now) or relaunch with `-no-snapshot` before debugging app code.
