---
name: OmniTuner
description: A musician's toolkit (tuner, metronome, chords, scales) on one neutral board, with one accent that marks what the music is saying.
colors:
  ink-light: "#000000"
  ink-dark: "#FFFFFF"
  paper-light: "#FFFFFF"
  paper-dark: "#000000"
  board-grey-light: "#F3F3F2"
  board-grey-dark: "#1C1C1C"
  control-ink-light: "#171717"
  control-ink-dark: "#ECECEC"
  muted-label: "#868688"
  hairline-light: "#DADADA"
  hairline-dark: "#333333"
  good-green-light: "#1E7A45"
  good-green-dark: "#8FD3A8"
  off-pitch-red-light: "#B3261E"
  off-pitch-red-dark: "#F2B8B5"
  warn-amber-light: "#8A4D00"
  warn-amber-dark: "#C07A5E"
  info-violet-light: "#544A8F"
  info-violet-dark: "#9A8FB8"
  accent-pink: "#FC48B3"
  accent-blue: "#2B67FE"
  accent-cyan: "#00B2FF"
  accent-green: "#0EB4A0"
  accent-lime: "#B2D20C"
  accent-yellow: "#FFCC32"
  accent-orange: "#FF7F65"
  accent-black-light: "#0E0E0E"
  accent-black-dark: "#F2F2F2"
typography:
  readout:
    fontFamily: "Roboto (system)"
    fontSize: "fitted to the panel"
    fontWeight: 700
    fontFeature: "tnum"
  display:
    fontFamily: "Roboto (system)"
    fontSize: "57sp"
    fontWeight: 400
    lineHeight: "64sp"
  headline:
    fontFamily: "Roboto (system)"
    fontSize: "24sp"
    fontWeight: 600
    lineHeight: "32sp"
  title:
    fontFamily: "Roboto (system)"
    fontSize: "22sp"
    fontWeight: 400
    lineHeight: "28sp"
  body:
    fontFamily: "Roboto (system)"
    fontSize: "16sp"
    fontWeight: 400
    lineHeight: "24sp"
  label:
    fontFamily: "Roboto (system)"
    fontSize: "12sp"
    fontWeight: 500
    lineHeight: "16sp"
rounded:
  xs: "12dp"
  sm: "12dp"
  md: "16dp"
  lg: "20dp"
  xl: "28dp"
  full: "50%"
spacing:
  xs: "4dp"
  sm: "8dp"
  md: "16dp"
  lg: "24dp"
  xl: "32dp"
components:
  group-card:
    backgroundColor: "{colors.paper-light}"
    rounded: "{rounded.lg}"
  group-card-dark:
    backgroundColor: "{colors.board-grey-dark}"
    rounded: "{rounded.lg}"
  segmented-track:
    backgroundColor: "{colors.board-grey-light}"
    rounded: "{rounded.md}"
    height: "44dp"
    padding: "4dp"
  segmented-pill-selected:
    backgroundColor: "{colors.accent-pink}"
    rounded: "{rounded.sm}"
  nav-tab:
    textColor: "{colors.muted-label}"
    typography: "{typography.label}"
    rounded: "{rounded.full}"
    height: "64dp"
  nav-tab-selected:
    backgroundColor: "{colors.board-grey-light}"
    textColor: "{colors.accent-pink}"
    rounded: "{rounded.full}"
    height: "64dp"
  button-filled:
    backgroundColor: "{colors.control-ink-light}"
    textColor: "{colors.paper-light}"
    rounded: "{rounded.full}"
  button-tonal:
    backgroundColor: "{colors.board-grey-light}"
    textColor: "{colors.control-ink-light}"
    rounded: "{rounded.full}"
  menu:
    backgroundColor: "{colors.paper-light}"
    rounded: "{rounded.md}"
    width: "220-280dp"
---

# Design System: OmniTuner

## Overview

**Creative North Star: "The Pedalboard"**

OmniTuner is a row of compact, purpose-built units on one neutral board. The tuner, metronome, chords and scales each do one job with their own controls, but they sit on the same black-or-white surface, share the same squircle housing and are wired to the same signal: the user's accent color. The board stays quiet so the units can be read at a glance from a music stand.

The mood is calm and restrained, precise and trustworthy, soft and tactile. Calm comes from a grey-scale Material scheme where stock components render in black and white. Trust comes from exact readouts (tabular numerals, the cents offset, the needle) and steady, non-flashy motion. The tactile part lives in the form: continuous corners on every rounded surface, a selection pill that springs between tabs and segments, and an icon that pops once when its tab is chosen. Components are refined and restrained: stock Material 3 forms, carefully tuned, never decorated.

Density is moderate: one main readout per screen, controls grouped in cards with 16 dp margins, nothing scrolled when a landscape layout can fit it.

**Key Characteristics:**
- Neutral scheme: pure white / pure black pages, one grey per theme for every filled surface.
- One user-chosen accent, used only where it carries musical meaning.
- Continuous "squircle" corners everywhere (smoothing 0.6); true circles only for icon buttons, swatches and dots.
- Flat: no shadows, depth comes from the page/card grey step.
- Springy, single-pill selection shared by the bottom nav and every segmented control.
- System font through the Material type scale; big readouts in bold with tabular figures.

## Colors

A grey-scale board with one signal light the user picks.

### Primary
- **The Signal (user accent)** (default: the wallpaper's `system_accent1` tone 600 in light / 300 in dark; fallback `{colors.accent-pink}`): the one color that means something. It marks the tuner needle, the in-tune note, the metronome's sounding beat dot, the metronome's BPM readout and its Start button, every Switch and Slider, the selected segment of every segmented control, the Scales root, the active/tuned string chips, the selected item of every picker, the selected nav tab's icon and label, and in Chords the diagram's root ring, an "altered" numeral chip and a "moderate" key match. Its on-color is black or white, whichever contrasts more (luminance crossover 0.179).
- **Signal presets**: Blue, Cyan, Green, Lime, Yellow, Orange, Pink, and Black (near-black `{colors.accent-black-light}` in light, flipping to near-white `{colors.accent-black-dark}` in dark so it never vanishes).

### Tertiary
- **Good Green** (`{colors.good-green-light}` / `{colors.good-green-dark}`): good or valid states.

### Neutral
- **Ink** (`{colors.ink-light}` / `{colors.ink-dark}`): body text and icons, pure black on white, pure white on black.
- **Paper** (`{colors.paper-light}` / `{colors.paper-dark}`): the page surface; dark is true AMOLED black.
- **Board Grey** (`{colors.board-grey-light}` / `{colors.board-grey-dark}`): the single fill for every card, panel, chip, pill and sheet in its theme. In the Settings-style grouped list it flips roles in light mode: the page drops to Board Grey and the group cards are white.
- **Control Ink** (`{colors.control-ink-light}` / `{colors.control-ink-dark}`): Material `primary`, so filled buttons and stock controls render near-black / near-white.
- **Muted Label** (`{colors.muted-label}`): secondary text, section titles and unselected nav labels, both themes.
- **Hairline** (`{colors.hairline-light}` / `{colors.hairline-dark}`): inset dividers between rows in a group.

### Status (Chords only)
- **Off-Pitch Red** (`{colors.off-pitch-red-light}` / `{colors.off-pitch-red-dark}`): Material `error`; out of tune, wrong.
- **Warn Amber** (`{colors.warn-amber-light}` / `{colors.warn-amber-dark}`): sevenths and altered tones in the chord diagram, chromatic chords, weak key matches.
- **Info Violet** (`{colors.info-violet-light}` / `{colors.info-violet-dark}`): extensions, borrowed chords, chord-tone lines.

### Named Rules
**The One Signal Rule.** The accent appears only where it carries musical meaning (in tune, the beat, the root, the current choice). Never use it for decoration, headers, borders or general chrome.

**The One Grey Rule.** Each theme has exactly one fill grey (#F3F3F2 light, #1C1C1C dark). No `surfaceContainer*` ladder, no near-identical second grey.

**The Hex Lives in Theme.kt Rule.** Screens read `MaterialTheme.colorScheme`, `LocalAccentColors`, `LocalGroupedListColors`, `LocalNavBarColors` or `LocalStatusColors`; hex values exist only in `ui/theme/Theme.kt`.

## Typography

**Display Font:** the system font (Roboto on most devices)
**Body Font:** the system font
**Label/Mono Font:** none separate; numeric readouts use tabular figures (`tnum`)

**Character:** One family, the platform's own, so the app reads as native and follows the user's font-size setting. Hierarchy comes from size and weight, not from a second face.

### Hierarchy
- **Readout** (Bold 700, sized to fit its panel, line height equal to size, tabular figures): the tuner's note name and the metronome's BPM. The tuner's octave sits beside it on the baseline in headlineLarge SemiBold.
- **Display** (Regular 400, 57sp, 64sp): base style the readouts are built from.
- **Headline** (SemiBold 600, 24sp, 32sp): sheet, menu and dialog titles (`headlineSmall`).
- **Title** (Regular 400 / Medium 500, 22sp / 16sp): screen titles and card headings (`titleLarge`, `titleMedium`).
- **Body** (Regular 400, 16sp / 14sp): rows, values and descriptions (`bodyLarge`, `bodyMedium`); section titles are `bodyLarge` in Muted Label.
- **Label** (Medium 500, 12sp, 16sp): nav tab labels (`labelMedium`); canvas labels on the needle meter and fretboard draw at 11sp.

### Named Rules
**The Native Voice Rule.** No bundled fonts. All text goes through `MaterialTheme.typography.*` in sp, so it scales with the system setting.

## Layout

Screens are a `Scaffold` with a top app bar and a bottom navigation bar of four tools. Content sits in cards with a 16 dp side margin; the bottom nav's outer pills line up with that same 16 dp edge. Spacing steps are 4, 8, 16, 24 and 32 dp: 4 dp insets inside tracks and pills, 8 dp between small items and under section titles, 16 dp for card margins and row padding, 24 dp between grouped sections, 32 dp for sheet title indents.

Each screen picks its layout from its own size (`maxWidth > maxHeight`), not from the device configuration. In landscape the bottom bar becomes a navigation rail on the phone's physical bottom edge and takes over the top bar's job. Fixed panels split side by side (Tuner: strings, readout, tuning list; Metronome: readout over Tap, menus over Start). Results fit the screen instead of scrolling (Chords pages one chord card at a time; Scales fits its fretboard to the height). Grouped lists cap their width at 640 dp.

Scales is used mainly in landscape: there the fretboard fills the screen, and portrait is the secondary view.

**The One Options Button Rule.** A page shows its instrument and at most one primary action. Everything that adjusts it lives in that tool's options sheet, reached by the same tune button (`OptionsButton`): in the top bar upright, and on the panel or card header in landscape, where the rail replaces the bar.

**The Fit, Don't Scroll Rule.** In landscape, size the content to the screen; a tool on a music stand should never need scrolling to be read.

**The No Shift Rule.** Transient indicators (progress, spinners, "copied" ticks) overlay or fill reserved space; they never push the content below them.

## Elevation & Depth

The system is flat. No shadows and no tonal elevation (`tonalElevation = 0.dp`). Depth comes from one step of tone: a Board Grey card on a Paper page, or, in the grouped list, a white card on a Board Grey page in light and a #1C1C1C card on black in dark. Sheets sit on the grouped-list page color with their rows in group cards.

**The Flat Board Rule.** Separate layers by the single page/card tone step, never by drop shadows or extra greys.

## Shapes

Every rounded surface uses continuous "squircle" corners (`SmoothShape`, smoothing 0.6): the curve eases out of the straight edge instead of starting as a circular arc. Material's shape scale is rebuilt from it a step rounder than stock: extra-small and small 12 dp, medium 16 dp, large 20 dp (the group cards), extra-large 28 dp. Fully rounded components (buttons, nav pills) take `SmoothShape.Full`, which still blends into the long edges. Nested shapes are concentric: a 16 dp track holds a 12 dp pill inset by 4 dp. True circles (`CircleShape`) are kept for icon buttons, color swatches and dots.

**The Squircle Rule.** Never `RoundedCornerShape`. If it has a rounded corner, it is a `SmoothShape`.

## Components

### Buttons
Refined and restrained: stock Material buttons, fully rounded.
- **Shape:** fully rounded squircle pill (`SmoothShape.Full`), passed explicitly since pill buttons don't read the theme shapes.
- **Filled:** Control Ink background with Paper text, so the main action reads black on white or white on black.
- **Tonal / Text:** Board Grey container or no container, Control Ink label.
- **Top-bar actions:** `FilledTonalIconButton`, circular; on Settings their container takes the group card color.
- **On a grey panel or card:** tonal buttons and icon buttons take the Paper color as their container, or they vanish into the Board Grey.

### Segmented control
- **Style:** equal segments on a soft Board Grey track (44 dp tall, 16 dp squircle, 4 dp inset), no outline or dividers.
- **State:** one accent pill (12 dp squircle) behind the selected label, springing between segments (low stiffness, damping 0.75).

### Cards / Containers
- **Corner Style:** 20 dp squircle for grouped sections; theme medium/large elsewhere.
- **Background:** Board Grey on Paper; in grouped lists, white (light) or #1C1C1C (dark) on the grouped page.
- **Shadow Strategy:** none (see Elevation & Depth).
- **Border:** none; rows inside are separated by inset Hairline dividers starting 16 dp in.
- **Internal Padding:** 16 dp; rows are transparent `ListItem`s so the card color shows through.

### Pickers, menus and sheets
- **Anchored menus** (inline rows and overflow): 220–280 dp wide, group card color, medium squircle, 24 dp check on the selected item in the accent.
- **Option sheets** (pickers opened from a top bar or panel header): a modal bottom sheet on the grouped page color, `headlineSmall` SemiBold title indented 32 dp, options in group cards; a pick closes it with a slide-down.
- **Selected item:** check and label in the accent; everything else in Ink.

### Controls
- **Switch:** accent track with a white thumb in both themes.
- **Slider:** accent thumb and active track; the inactive track is the accent at 24% alpha so it stays visible on a dark card.
- **Steppers:** `NumberStepper`, repeating while held.

### Navigation
- **Style:** the stock navigation bar on the page surface, holding four custom tabs (64 dp tall, 4 dp inset). One Board Grey pill slides behind icon and label of the selected tab (spring stiffness 120, damping 0.8, about 600 ms with a hint of overshoot).
- **States:** unselected tabs show an outlined icon and Muted Label text; the selected tab shows a filled icon and label in the accent, and the icon pops to 1.15× and settles on a soft spring.
- **Landscape:** a navigation rail with the same tabs on end, on the phone's physical bottom edge, carrying the Settings button in its header.

### Readout wheels (signature)
`BpmWheel` (vertical, the metronome's tempo, 1–800 BPM) and `NoteWheel` (sideways, looping, the Scales root): compact drums that fade out at their edges, the centre value in bold tabular figures. They are the only custom pickers; reuse them rather than adding another.

### Needle meter (signature)
The tuner's 41-tick canvas meter with 11sp scale labels; the needle and the in-tune note are the accent, out of tune reads Off-Pitch Red.

## Do's and Don'ts

### Do:
- **Do** keep the accent to the musical-meaning list in Colors; everything else is Ink, Paper or Board Grey.
- **Do** use `SmoothShape` for every rounded corner and keep nested shapes concentric (16 dp track, 4 dp inset, 12 dp pill).
- **Do** separate layers by one tone step (Paper / Board Grey), with inset Hairline dividers between rows.
- **Do** build option choices from the shared pickers (`MenuPickerRow`, `PickerRow` + `OptionSheet`, `PullDownMenu`) and segmented choices from `SegmentedRow`.
- **Do** set big readouts in bold with tabular figures, sized to fit their panel.
- **Do** fit landscape layouts to the screen and cap grouped lists at 640 dp.
- **Do** put a tool's adjustments in its options sheet behind the shared tune button, not inline on the page.

### Don't:
- **Don't** spread the accent to headers, borders, backgrounds or decorative chrome.
- **Don't** add a second fill grey or use the `surfaceContainer*` ladder.
- **Don't** use drop shadows or tonal elevation.
- **Don't** use `RoundedCornerShape`, or a circle where a squircle belongs.
- **Don't** bundle custom fonts or hand-pick text sizes outside the type scale.
- **Don't** build custom dropdowns, wheels, spinners or segmented controls, or use a raw `DropdownMenu`.
- **Don't** let a transient indicator push content down.
- **Don't** change colors, borders or sizes beyond what a task asks without the user's OK.
