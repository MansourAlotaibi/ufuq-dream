# Current release readiness

For v0.9.0-rc1 see [public QA and limitations](../0.9.0/STABILITY-RC.md), [Smart Ambient](../0.9.0/SMART-AMBIENT.md) and the release QA attachment. This is a prerelease. The historical0.8.3 preparation notes follow, not claims about fresh0.9 runtime testing.

# 0.8.3-rc1 release readiness

Release candidate, not a stable certification. Package com.ufuq.dream, code15, min28, target/compile34. Source derived from public commit 9a64eea; no history/tag rewrite. The owner's accepted 0.8.2 APK/source are backed up separately and unchanged. No device access occurred during release preparation.

## Reconciliation and feature parity
A byte comparison of `app/src/main` in the latest accepted local 0.8.2 source and public baseline found no differences. All older local project variants were inventoried by Gradle version. The latest accepted APK was code14/0.8.2 with the recorded debug certificate. Its checksum remains in the private acceptance record. This is evidence from saved diagnostics and user acceptance, not a fresh inspection of the installed TV signature.

| Supported feature | Actual implementation | Verification / limits |
|---|---|---|
| TV DreamService | UfuqDreamService, BIND_DREAM_SERVICE, native dream metadata | lifecycle tests, APK manifest inspection; accepted 0.8.2 physical baseline |
| 3 independent Saudi palettes | ThemeManager, SettingsActivity | palette and preview tests |
| 13 photos / categories / fixed and 1/3/5 rotation | BackgroundRepository, MediaLibrary, BackgroundRotation | image hashes, category and scheduling tests |
| Arabic/Latin digits, seconds, 12/24 | DateProvider, SceneRenderer | API28 ICU tests with isolated test time zones |
| Gregorian / Umm al-Qura Hijri | DateProvider / UmmAlQuraProvider | calendar tests; sighting differences disclosed |
| Saudi/custom greeting | GreetingText, GreetingProvider | sanitization, 40-codepoint and persistence tests |
| Studio, RTL, D-pad, Save/Cancel | PreviewStudio, SettingsDraft, SettingsActivity | shared renderer, dialog/focus, draft tests; new import row awaits TV review |
| Manual 3x3 / Automatic placement | ClockPlacement, PositionTile | safe-area and settings tests |
| Transition/startup fade / optional night dimming | AmbientDisplay, CinematicRotation, NightMode | transition/lifecycle/night tests; dimming remains off by default |
| Low-memory images | BackgroundRenderer | at most current plus one preloaded image; resource cleanup tests |
| Video playback / static fallback | HardwareMotionBackend, MotionLayer, DecoderLease | fake-backend/error/lease tests; actual accepted 0.8.2 video on Mi Box, not a new hardware test |
| User local video import | LocalVideoImport + Android scoped picker | copy bound/hash, permission/intent, cancel/prefs tests; native extraction and hardware admission still require physical TV |
| Compatible settings | SettingsRepository (same schema/keys) | migration/persistence/preview-isolation tests; no cross-signature export |

No obsolete mode controls were restored. The only runtime addition is explicitly requested non-ADB access to the existing personal-video engine. Import is an explicit library action, separate from staged display preferences.

## Licensing
Thirteen original source records were rechecked on 2026-11-02: ten via Commons API, three through the original Commons licensing sections after API rate limits. `media-license-verification.json` records method and evidence. All matched the existing license labels. Photo and thumbnail derivatives remain CC BY-SA4.0 / CC BY3.0 / CC BY3.0PL, with author/source/license/changes in APK and source. No MIT relicense of assets, DRM, or additional asset restrictions.

No bundled video cleared redistribution review. The personally approved Pexels footage remains excluded; explicit permission for public ambient/screensaver redistribution is missing. User-imported footage stays private to their own installation. This release does not claim to supply the privately tested cinematic clip. Pending stock candidates are never packaged. Runtime licenses (Kotlin2.0.21/annotations13.0 Apache2.0) are included; system Arabic fonts are not redistributed.

## Validation and resource budget
Local clean release/debug builds, both lint variants, Android unit tests and synthetic media-pipeline tests are executed during preparation. Exact final counts/results, source SHA, signing fingerprint and APK SHA256 are recorded in the published release attachments. CI validates the exact branch/merge/tag revisions; it does not contain signing credentials. Do not infer physical performance from host tests.

Runtime image budget: 1920×1080×4 = 7.91 MiB per maximum ARGB image; at most one next image (15.82 MiB for that pair). Studio images are capped at 640 pixels wide; user video poster extraction is 640×360 (~0.88 MiB). Only one hardware decoder process-wide, no software decoder fallback, audio, wake lock, video filter chain or full-resolution video crossfade. Surface/codec buffer memory is driver-specific and unmeasured for this RC. Imports stream in 64 KiB chunks with a 128 MiB disk cap; no whole-video memory load. APK remains about 8 MiB, exact size in release metadata.

## Open gates / limitations
- New production-signed APK and SAF import are not physically installed/tested on the owner's TV by this task.
- Some Android TVs lack a system document picker. Imports fail gracefully with instructions; device/provider compatibility needs validation.
- Full successful native MediaExtractor/poster/decode import is not proven by host mocks. Test real silent MP4, malformed streams, repeated start/stop and sleep/wake.
- Independent offline signing backup/password recovery must be completed by the owner; two encrypted copies on one Mac are not disaster recovery.
- Wider Google TV, accessibility, 720p/4K, overnight power/memory and decoder matrix remain open.
- Known lint warnings include ignored Rect intersection result, deliberate API28 landscape/immersive behavior, Arabic string resources and ICU formatter choice. No zero-warning claim.
- No claim of independent legal counsel, exhaustive secret scan or dependency vulnerability certification. Dependency updates and GitHub alerts require maintenance.

## TV acceptance (before stable)
Install only on an explicitly approved compatible/fresh test device. Confirm signature first. Test all palettes/photos, clock positions/overscan, Arabic+Latin digits, date changes/timezone, greeting keyboard/length, Save/Cancel/Back/restart. Import a rights-owned 1080p silent AVC MP4 through the picker; cancel once, reject audio/HEVC/oversize/invalid once, then test successful copy/playback/poster, fixed/mixed rotation and fallback. Repeat Dream entry/exit, sleep/wake and overnight memory observation. Confirm no launcher/Aerial Dream/default/storage change. The existing accepted owner install must not be uninstalled or cleared to bypass a certificate mismatch.
