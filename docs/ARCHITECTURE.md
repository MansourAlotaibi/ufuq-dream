# Architecture

`UfuqDreamService` owns the screensaver lifecycle and the session-start cursor. `AmbientDisplay` owns the shared presentation, text-safe layout, tick scheduling, photo loading and optional motion layer. `PreviewStudio` scales that same display; it never consumes the actual Dream session cursor.

`SettingsRepository` loads/migrates saved preferences. `SettingsDraft` stages edits. `SettingsActivity` applies only after explicit Save, restores dialog focus, stops the preview on pause and releases image references on destroy.

`BackgroundRepository` holds 13 photo records and composition regions, independent of `ThemeManager`'s three palettes. `ClockPlacement` fits clock/date/greeting as one group. `DateProvider` uses Android ICU Arabic Gregorian and Umm al-Qura formatting. No network calendar is used; Hijri dates may differ from local sighting.

`BackgroundRenderer` bounds decoding to output size, one current bitmap plus at most one preloaded bitmap; canceled/stale results are generation-guarded. At 1920×1080 ARGB8888, each full bitmap is about 7.91 MiB. Motion posters are limited further; Studio photo decoding is limited to 640px. Decoder/compositor memory is additional.

`HardwareMotionBackend` uses one process-wide hardware AVC decoder with a cancellable worker and an 8-second watchdog. `MotionSession` rejects stale callbacks. The supported technical envelope is 16:9 AVC up to1080p/30fps, 8–180 seconds; this is not a license/quality certification. Playback falls back to a poster on error. Vendor names gate API28 hardware decoding conservatively, which may exclude some otherwise capable devices.

`CinematicRotation` counts active time. A video due for rotation finishes its current cycle before changing; a photo advances at the interval. Transitions use fade-through-black, not two simultaneous video decoders or a guarantee of seamless looping.

`PersonalMotionStore` is optional internal developer infrastructure. No private videos ship publicly, and no consumer import UI exists. General scenery uses category -1 (All only), avoiding false AlUla/Diriyah/Riyadh labels.

## Public candidate media boundary
LocalVideoImport receives only a user-selected content URI, streams at most128MiB on an Activity-owned worker, validates silent AVC/16:9/size/duration, extracts one640×360 poster, and publishes the existing internal catalog with AtomicFile. No persistent URI grant/network/broad storage permission. Existing MediaLibrary/HardwareMotionBackend consumes the same catalog; the theme, renderer, rotation and preference keys are unchanged. Canceling display changes never commits settings; import is a distinct explicit library action. UI teardown interrupts import and stops the shared preview. See LOCAL-VIDEO.md for provider and native validation limitations.

## 0.10 modules
DailyReflection validates/loads a small unchanged licensed offline corpus; ReflectionView renders complete Arabic text. FootballSession owns saved-consent, foreground-only bounded HTTPS/cache/backoff and generation-safe cancellation, without artwork. UfuqFonts keeps a bounded lazy native-typeface cache and weak view restoration; TimePresentation shares wall-clock format policy. AmbientDisplay separates safe layout regions from ContentBackdrop's visible-text-only paint bounds. Contextual SettingsActivity retains the existing SettingsDraft transaction and shared PreviewStudio renderer. No Compose, WebView, new network SDK or background polling service. See docs/0.10.0/*.md for migration and rendering details.
