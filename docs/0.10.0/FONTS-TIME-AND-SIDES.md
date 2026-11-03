# 0.10.0-dev3 — clock side, time policy and Launcher fonts

Local owner-review build only. Package com.ufuq.dream, versionCode 25, minSdk 28. No publication or device changes in this task. dev1 and dev2 source archives/APKs remain rollback baselines outside the repository.

## Clock side

The visible selector now contains exactly Automatic / Right / Left. It retains live focus preview, Back reversion, explicit choice confirmation and outer Save/Cancel. No nine-position grid is displayed. Existing renderer support for old grid inputs remains internal for compatibility.

Legacy positions 0/3/6 map to Left (stored code 3), 2/5/8 to Right (5), and 1/4/7 to Automatic (-1). Reading preferences only resolves the effective choice; it does not rewrite the original key. On a confirmed save, the old raw value is retained once as legacy_clock_position if migration changed it. Other preferences are preserved. No hidden save from preview.

Automatic follows each photo's composition; fixed left/right centers the coherent clock/date/greeting group vertically in the available upper area. When editorial content is enabled it occupies the opposite column. With it disabled the free photograph remains visible. The footer is measured using actual typeface metrics before clock layout, so Arabic font ascent is accommodated without falsifying line metrics or clipping diacritics.

## One clock-format policy

TimePresentation.pattern is shared by DateProvider and the prayer/fixture renderer. Prayer times use the selected city's timezone; fixture and main clock use the device timezone as before. Both Arabic/English and 12/24-hour formats are tested. A 12-hour display includes ص/م or AM/PM for the clock, five prayer times and fixture kickoff. Next-prayer countdown is a duration, not a wall-clock time, and remains hours/minutes. Provider cache data, instants and prayer calculations are unchanged.

## Fonts

Reused the exact current Ufuq Launcher collection, not arbitrary downloads:

- Arabic: Default, Cairo, Tajawal, IBM Plex Sans Arabic, Readex Pro, Almarai.
- English: Default, Lato.

Two clear rows under Style retain independent Arabic/English choices. The active app language selects its matching family. Choosing a font refreshes the native UI and shared live preview, with no persistence until Save. Discard and restart behavior is covered. Missing or invalid font IDs fall back to Default. Default on existing installs preserves the accepted system typography.

As in the Launcher, font selection changes interface and informational text; clock digits and the original religious body deliberately retain their dedicated typography. No artificial line-height spans, Quran transformation, custom font fallback substitution, network font loading or changes to source text. Fonts remain unmodified OFL assets, separate from MIT code. Full notices are available in the app credits; source pins and SHA-256 verification are in assets/font-provenance.json.

The lazy shared cache holds at most three loaded base typefaces. View-to-original-typeface restoration uses weak view keys, without retaining Activity contexts. File footprint is bounded; fonts add no runtime dependency, decoding surface, animation, network request or permission. Native text layout is measured again after a font change.

## Acceptance

Host tests cover exact source/license hashes, all bundled fonts, complete long verse, real captured fixture, prayer and weather, safe areas, 720p/1080p, API 28/34, language independence, staged Save/Cancel, invalid IDs, legacy positions and all displayed time formats. Captures under app/artwork are host-rendered dated reference data, not live TV evidence.

Physical review still needed: Arabic diacritics at sofa distance, all six Arabic font choices, Lato in English, both side choices and Automatic, 12-hour prayer/fixture labels, remote focus, sleep/wake and endurance. Do not publish or call this a stable release on host tests alone.
