# 0.10.0-dev1 — local owner-review build

Not a public release. No feature branch, tag or APK has been published for this development version. Do not replace the accepted production certificate or remove an existing installation.

## Changes

- Optional daily Quran / Hadith / alternating selection, independently hidden by default.
- Favorite-team selection: None, Saudi teams, international teams. Upcoming fixture only; no scores, logos or tracking.
- Existing Smart Information group, shared Dream/preview rendering, explicit Save/Cancel. No new main navigation category.
- Religious text stays in its original Arabic in both interface languages. Complete text and citation, without ellipsis or machine translation.
- 720p/1080p layout accommodates the complete long verse (2:286). A bounded 148dp reflection region shares a supplemental row with the fixture; the clock-safe region is recalculated. This still requires sofa-distance TV review.

## Sources and rights

Reused Ufuq Launcher commit `4e3e0b970fb39d102a8258e618041e2ae42148ab`. Original MIT code attribution is retained in assets/licenses/UFUQ-LAUNCHER-MIT.txt. The seven-entry daily corpus is copied byte-for-byte; see assets/reflection-provenance.json. Quran: Tanzil CC BY 3.0 plus unchanged-copy conditions. Hadith transcription selections: Arabic Wikisource CC BY-SA 4.0; the record-level revision URLs, alternative numbering and notices are retained. These content rights are separate from the app's MIT code license.

TheSportsDB existing development endpoint uses public test identifier 123, not a private API key. This does not establish permission for unlimited public distribution or commercial/app-store use. Recheck provider terms before publishing this feature. No new provider or content research was substituted for the requested Launcher implementation.

## Runtime and privacy

Daily text is offline, date-selected and bounded (seven records); no remote religious-content calls. Football requires a saved, enabled known-team selection. Staged selections do not authorize network requests. Only visible preview/Dream sessions refresh. HTTP is off the main thread, HTTPS, 8-second connection/read timeouts, bounded response parsing and cancellation/generation checks. Cache is private, capped at eight team records; fresh data is reused for an hour, fallback for up to 24 hours. Failed requests back off; no background service, analytics, precise location or new permissions.

No new runtime dependency, image or decoder. Supplemental views are native text; existing photo/video resource budget is unchanged. Host layout tests are not physical memory/endurance evidence. Fixture data is provider-supplied and can change; no invented fallback match is shown.

## Owner TV acceptance

1. Confirm the exact target, installed package/certificate and update compatibility; preserve current APK before `install -r`. Never uninstall or clear data.
2. Check both languages, all three themes, automatic and manual positions at normal viewing distance.
3. Test Hidden / Quran / Hadith / alternating; check complete long verse, source reference, glyphs and no overlap.
4. Select a team, cancel and confirm it was not saved or fetched; then save explicitly and check a real upcoming fixture, city/device timezone and date.
5. Check no internet, cached fixture, no upcoming fixture, settings reopen, Dream entry/exit and sleep/wake. Leave photos/video, prayer and weather unchanged.
6. Observe 60-minute memory/endurance behavior before readiness claims.

## Upgrade / rollback

Package remains com.ufuq.dream, minSdk 28; development versionCode 23. Debug APK must match the accepted debug signing identity. Production builds have a separate certificate and cannot replace debug installations. Never bypass Android signature checks.

Save/Cancel remains atomic and new options default off. Existing settings are not reset. Older versions ignore additional preference keys. Prefer a forward-versioned rebuild of the accepted source signed with the same debug key for rollback; do not uninstall to downgrade. `install -r -d` is only appropriate for a debuggable same-key rollback after separate owner approval and backup. Imported personal videos and USB storage must remain untouched.

Public release requires owner TV acceptance, provider terms review, religious-text visual review and normal signing/backup gates. Current development build must not be labeled Stable.
