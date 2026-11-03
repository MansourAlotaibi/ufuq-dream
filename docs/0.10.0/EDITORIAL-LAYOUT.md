# dev2 — balanced ambient composition

## Physical-TV finding

Owner photograph showed the clock reduced to a small cluster above a large footer. The cause was shared vertical budgeting: reflection and fixture increased the footer reserve to 52% of screen height, and ClockPlacement scaled the entire clock/date group to the remaining vertical space. Changing only clock font size would not fix that constraint.

## Composition

- Preserve the clock's original typography and the existing clock-size preference. Only prayer/weather contribute to its footer reservation now.
- Move reflection and fixture into a separate, opposite editorial column. Arabic text remains RTL; mixed team names keep bidi isolation.
- Reading order within the editorial column: full original quotation, its citation, then upcoming fixture. Short citations stay close to their quotation. No religious text is shortened.
- Keep prayer times and weather in a shallow full-width footer. Existing current-prayer accent and next-prayer countdown styling are unchanged.
- No new setting, display mode, permission, media asset, timer or runtime dependency.
- Automatic placement follows the photo's existing clock-side metadata. Manual left/right choices keep their side and vertical anchor. When optional editorial content is enabled, a center-column clock choice retains its vertical anchor but uses the photo's preferred clock side to avoid collision. With editorial content disabled, the original 3x3 behavior is unchanged. The saved position value is never rewritten.
- The preview and DreamService share AmbientDisplay and the same geometry. No screenshot-based preview.

## Implementation

SmartLayout budgets only the bottom information strip. SmartPanel retains data/session ownership, but its supplemental view is a separately positioned child of AmbientDisplay. The supplemental view has no timers, bitmap allocations or focusable descendants. Both areas stop refreshing through the existing SmartPanel lifecycle. Clock and editorial columns have a 6%-width gap and overscan margins; only the clock group receives the existing micro-shifts.

The editorial column uses a restrained transparent-to-dark local gradient, not an opaque full-screen panel. It does not move the background photograph. Existing low-memory image/video code is untouched.

## Validation and review limits

Regression coverage measures every complete corpus entry; both interface languages and three palettes; all automatic/manual clock positions; 720p and 1080p; API 28 and 34; footer bounds, full text, clock width, column separation and resource cleanup. Native Robolectric screenshots are host renders, not TV acceptance. Review at three metres, actual TV contrast and a long-running session remain physical checks.

No settings migration is required. New version is 0.10.0-dev2, versionCode 24, package unchanged. dev1 source archive and APK remain preserved outside the source tree. Do not publish until owner review; do not uninstall to roll back. Use a same-key forward-versioned baseline build if rollback is required.
