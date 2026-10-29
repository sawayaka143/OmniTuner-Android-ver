# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

Guitarists of all levels, from beginners to gigging players, tuning and practicing at home, in rehearsal or on stage. Bass, ukulele, extended-range and custom-tuned players are served too, but guitarists are the primary audience when a decision has to favor one group.

## Product Purpose

OmniTuner is a musician's toolkit in one app: a tuner, a metronome, a chord finder (voicings and key finder) and a scales map. All four share one instrument and tuning library. Success means a guitarist can tune, keep time, look up a chord shape and see a scale on their own instrument and tuning without switching apps.

## Positioning

- **All-in-one toolkit:** tuner, metronome, chords and scales in one app, tied together by a shared instrument and tuning library.
- **Custom tunings are first-class:** any instrument or tuning the user defines (e.g. FACGCE, DADGAD, 8-string) works everywhere: the tuner's strings, chord voicings and the scale fretboard.
- **Accuracy and feel:** a precise, fast, stable pitch engine (onset handling, tracked lock, hold-to-confirm) and a metronome with sample-accurate, look-ahead timing.
- **Clean, ad-free, native:** no ads, no accounts, a quiet native Material 3 app that stays out of the way.

## Operating Context

- Used with an instrument in hand: often one hand free, phone on a stand, desk or music stand, sometimes in a dim or loud room. The tuner keeps the screen on and gives haptic and chime feedback when a string is in tune.
- Works in portrait and landscape; landscape layouts fit everything to the screen rather than scrolling.
- Settings carry per-tool preferences: reference pitch, in-tune feedback, "Pro accuracy" (exact cents offset), theme (system/light/dark), accent color (match wallpaper or a preset), and which tool the app opens in.

## Capabilities and Constraints

- **Tuner:** microphone pitch detection (unprocessed capture, high/low-pass filtering tuned to the instrument), needle meter, string chips, auto and manual string modes, adjustable reference pitch.
- **Metronome:** tempo 1–800 BPM with wheel and tap, meters, subdivisions, polyrhythm, bar muting, voices, tempo ramp, saved presets.
- **Chords:** chord parsing and playable voicing search for any library tuning (6–12 strings), a Voicings | Key Finder toggle; roman numerals are accepted but not advertised.
- **Scales:** fretboard map for any library tuning, a root and scale picker (major, minor, modes, pentatonics, blues, harmonic and melodic minor), playback with bundled guitar samples.
- **Instrument library:** built-ins (Guitar, Ukulele, alternate tunings) plus user-defined instruments and tunings; tunings come only from the library, never typed in place.
- Fully offline; no accounts, no network features, no analytics.
- Android only, minSdk 29. The Android app is the source of truth; the earlier web version is abandoned and not kept in sync.
- English only for now.

## Brand Commitments

- Name: **OmniTuner**.
- Free on Google Play, with no ads and no in-app purchases.
- Native Material 3 identity with a minimal accent, the user's own color or the wallpaper's; detailed visual rules live in AGENTS.md.

## Evidence on Hand

- Bundled guitar note samples for Scales playback (`app/src/main/res/raw`).
- Open-source licenses screen and a privacy policy link in Settings.
- No user reviews, testimonials, download numbers or press exist yet; do not invent any.

## Product Principles

1. **The instrument in hand comes first.** Every tool reads from the same instrument and tuning, and any tuning the user defines must work everywhere.
2. **Trust through precision.** A reading, a beat or a chord shape has to be right and steady; accuracy beats flourish.
3. **Glanceable from a distance, operable with one hand.** Key readouts and controls work with the phone on a stand mid-practice.
4. **Stay out of the way.** No ads, accounts or nagging; the app is a quiet, native tool.
5. **Serve beginners without boring experts.** Sensible defaults up front, depth (custom tunings, polyrhythms, pro accuracy) available but not in the way.
