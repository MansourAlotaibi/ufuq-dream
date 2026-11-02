# 0.9.0-rc1 — public validation summary

This is a prerelease, not Stable. Publication is owner-authorized. No private device reports, preference backups, personal videos or signing secrets are distributed.

## Tested scope

- Clean Debug and Release builds, both lint variants, both unit-test variants and host media pipeline are required; final counts/results appear in the release QA attachment.
- API28 and API34 framework simulations cover formatting, bidi, calendar/year boundaries, error handling, settings Save/Cancel/recreation, shared preview, safe areas and lifecycle. These are not a second physical TV or emulator certification.
- Prior debug-candidate Mi Box endurance: 3600.8 seconds, 61 active samples, 30.1–46.3 MiB process PSS, process CPU average 8.25% of one core, one PID, no observed application crash/ANR. Hardware AVC decoding and four first-frame events observed. This result belongs to the earlier stability build, not proof of a fresh one-hour run for this release binary.
- Real Mi Box weather HTTP200 and actual temperature/condition were verified; the saved refresh interval was restored after testing. Production-key APK cannot overwrite the differently signed accepted debug app; test/debug and public artifacts have separate roles.
- Full physical sleep/wake, every new bilingual/color interaction, wider hardware behavior and isolated production-signed acceptance are not certified. No claim of zero leaks or official prayer accuracy.

## Accuracy and providers

Riyadh November 2, 2026 sample compared to IslamicFinder: application/reference Fajr04:41/04:42, Dhuhr11:37/11:38, Asr14:49/14:49, Maghrib17:13/17:14, Isha18:43/18:43. This is an independent calculated schedule, not an official mosque certificate; coordinates/rounding are possible explanations, not verified causes. https://www.islamicfinder.org/world/saudi-arabia/108410/riyadh-prayer-times/

AlAdhan calendar batches cover exactly 30 Gregorian dates across month/year boundaries. Unexpected dates, timezone, method, school, offsets or ordering reject the batch atomically. Retry/backoff and resource limits are documented in SMART-AMBIENT.md.

Open-Meteo CC BY4 data attribution remains next to displayed readings. Free hosted API access is non-commercial; commercial forks need an appropriate agreement. No sample is presented as live data.

## Remaining Stable gates

- Independently stored production keystore AND separately preserved password, with a successful private restore/signing drill. Same-Mac copies and live Keychain access are not proof.
- Isolated acceptance of the production-signed APK, physical sleep/wake and broader TV coverage. Never uninstall/clear a working debug installation to bypass signatures.
- New-binary owner visual acceptance and longer runtime evidence. CI does not substitute physical-device reliability.

## Resources and safety

13 public licensed photos, no bundled video; at most current/one next image and one hardware decoder. 1080p ARGB maximum pair15.82MiB, Studio640px target. Video buffers are driver-dependent and not included in process-only measurements. All private artifacts and device-specific paths are excluded. Same production certificate retained; versionCode22. Existing preferences/data are preserved on same-signature updates; app-language choice normalizes the legacy digit preference intentionally. No launcher, firmware, storage or default screensaver changes.
