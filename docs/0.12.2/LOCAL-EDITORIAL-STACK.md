# Local 0.12.2-dev1 — weather / match / reading stack

Owner-approved layout trial only; no public push, tag or release. Package unchanged, debug versionCode38. Preserve the public0.12.1 source/APK and private previous APK/preferences; no settings schema migration.

The side column shared by Studio and Dream now contains a compact weather row, a separate upcoming-match card with a quiet heading, then the religious text card. Each owns a content-sized backdrop; the outer column draws no combined box. Disabled cards and dependent gaps disappear. The clock/date group remains separate; the footer reserves space for prayers only. Column side follows the existing clock-side/automatic composition logic.

Weather keeps adjacent source attribution. Condition-specific sun/night/cloud/fog/rain/snow/storm colors and simple fixed Canvas glyphs are dimmed with the current night text factor. No animation or image asset is introduced. The main display omits the Earlier reading banner: readings older than3h (or invalid future timestamps) are hidden, with a truthful unavailable/loading/save-consent state. The original cache is retained for diagnostics; no invented value or change to provider/refresh policy. Settings source details explain the3h display limit in both languages.

Tests include294 API28/34 framework executions, all21 texts across both languages and clock sides at1080p/xhdpi and720p/tvdpi, selected-font regressions, separate backdrop visibility for every enabled combination, language/time formats, source credits, saved-weather consent, stale reading/icon removal, semantic colors, lifecycle and prior regressions. Build/lint actual report is stored privately with the APK. Native host screenshots are illustrative, not proof of physical-TV visual acceptance. Compact360dp-height/high-density configurations are not certified by the TV-density matrix.

No deployment changes to USB debugging, adopted storage, launcher/default screensaver, firmware or other apps. The owner authorized installing this local development trial only; public publication awaits subsequent review/approval. Never uninstall or clear data to bypass signatures; prefer a same-key forward fix.
