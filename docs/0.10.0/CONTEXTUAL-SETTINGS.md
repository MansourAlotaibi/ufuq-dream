# 0.10.0-dev4 — contextual settings polish

Local review build only; no device installation or public publication in this task. Package unchanged, versionCode 26. The owner-tested dev3 APK/source are preserved as rollback artifacts.

## Audit and final behavior

| Parent choice | Relevant child options |
|---|---|
| Arabic / English language | Only the matching font selector. Both saved font choices remain independent. |
| Prayer times enabled | Source, city, calculation method and Asr preference; visually indented under the toggle. |
| Weather enabled | City, temperature unit and refresh interval; visually indented under the toggle. |
| Automatic backgrounds | Rotation interval: 1, 3 or 5 minutes. |
| Fixed background | One eligible background-library picker, covering photos/video according to selected media type and category. |
| Preset greeting | Preset phrase selector. |
| Custom greeting | Custom text editor. |
| Automatic / hidden greeting | No irrelevant editor. |

Prayer/weather/greeting visibility gates already existed; this change preserves those gates and makes the hierarchy visible. Smart information now presents prayers and weather before optional reading/fixture choices. No new section or feature is added.

## Background cleanup

The old interface had two fixed-background paths: imageMode=1 and rotationMinutes=0. Old zero-interval preferences still display as Fixed, without an implicit rewrite. The interval dialog no longer repeats a Fixed option. Explicitly selecting Automatic from the legacy zero state restores the recommended three-minute interval in the staged draft. Other nonzero saved intervals are retained when switching modes.

The duplicate all-category video-library action is removed. The existing unified fixed-background picker already includes eligible imported clips when Motion or Mixed is chosen; it respects category/type and no longer offers a second route that silently resets the category to All. Personal files, imported media records and playback engine are untouched. Import remains available when a file picker exists and either motion is selected or the library has not yet been initialized with a clip. No non-working file picker is introduced.

## Persistence and focus

Hiding a control does not clear its value. Prayer city/method/Asr/source, weather city/unit/refresh, both font choices, greeting text and media selections remain in the saved snapshot. Save and Cancel retain their existing transaction semantics. No requests are authorized by merely staging a disabled feature. No migration alters provider, religious calculation method, city or network cache.

Value badges use a bounded width and natural text wrapping, avoiding oversized values squeezing labels/descriptions. Main toggles use concise user-facing summaries; provider names, consent details and prayer calculation cautions remain in source choices/help/credits.

Dependent rows are indented on logical Start (RTL/LTR aware), using the existing native focus surface and readable type scale. They are removed from navigation when irrelevant, not left as disabled focus stops. Toggle focus restores to its main row after rebuilding. Both horizontal remote directions retain the existing shortcut to the tab bar. The persistent live preview remains unchanged.

## Validation scope

Regression tests cover Arabic/English font gating and preserved inactive choices; prayer/weather independent gates and preserved values after toggle/save/reopen; staged consent; legacy fixed-background behavior; exclusive fixed/rotation controls; greeting editors and retained custom text. Full host build/lint/test results and APK metadata are recorded beside the development artifact.

Physical owner checks remain: natural D-pad navigation through expanded sections, language switches, on/off/re-enable with preserved values, fixed imported video selection, Save/Cancel and ordinary Dream behavior. Host tests are not physical-TV acceptance. Do not publish until owner approval.
