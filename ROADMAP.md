# Roadmap

## 0.8.2 — accepted baseline
Preserve the working Mi Box experience. Publish only the curated source and licensed photos, with honest limitations. Personal video stays separate.

## Next maintenance milestone
- Maintain the clean Linux CI build and review updates to SHA-pinned Actions dependencies.
- Production signing decision and a documented migration from personal debug installs; never promise a signed-key switch preserves upgrades automatically.
- TV text scaling/TalkBack audit, 720p/1080p/4K checks, remote Save/Cancel/Back and keyboard tests.
- Long-run playback, sleep/wake, rapid Dream start/stop, fallback and resource profiling on API28 and a modern Google TV.
- Cache date formatters carefully and verify timezone/time-change handling before changing the stable renderer.
- Defensive invalid-composition fallback; bounds-first personal thumbnail decoding; preference type/schema validation.

## Later, only after stability
- Validate the new scoped media import across TV document providers; imported files never become public assets.
- Curated additional Saudi photographs and original motion footage with independently established rights.
- Localization resources and a deliberate English experience if supported; Arabic remains the primary product language.

Do not add weather, prayer times, sports, feeds or network accounts just to increase feature count. No timetable is promised.
