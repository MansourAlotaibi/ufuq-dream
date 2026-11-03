> Historical local-development record. The owner subsequently accepted dev2 on the TV and authorized public publication. See RELEASE.md for the current public candidate and remaining release gates.

# Free ambient extras — local development candidate

No subscription, private API key, additional permission, account, analytics, installation or publication introduced.

## Implemented
- Optional offline remembrance (default OFF), alternating with the existing verse/hadith slot every five minutes. With remembrance alone, the selected text changes every ten minutes. Device-local morning/evening changes the ordering of two texts valid for both periods. This is display scheduling, **not a religious ruling about the precise devotional time window**.
- Small deliberately limited set: «سبحان الله وبحمده» (Muslim 2692, 100 repetitions) and the complete Sayyid al-Istighfar supplication (Bukhari 6306). The display does not count recitations or substitute for reciting.
- Optional countdown to Ramadan, Eid al-Fitr, Eid al-Adha or Saudi National Day; OFF by default. Gregorian civil-day differences, local device timezone, Android ICU Umm al-Qura calendar. Hijri occasions visibly say **estimated / تقديري**. These are not official sighting announcements; do not use as a legal/religious determination of the beginning of fasting/Eid.
- Football competition name from existing `strLeague`, validated and cached with the fixture. Backwards-compatible cache reads when this field is absent.
- Local kickoff countdown using the existing validated timestamp. Match date, countdown and competition alternate in the accepted card's detail slot, once per minute. No new polling timer or API request. If occasion countdown and a fixture are both enabled, the occasion joins that detail rotation.
- Native preview and DreamService use the same rendering. All new settings use the existing staged snapshot and explicit Save/Cancel.

## Religious text verification and provenance
Verification date: 2026-11-03.

Only the ancient Arabic prayer text is included, not a publisher's translation, explanatory commentary, website design or a scraped database. It is separately identified from application code and from the licensed Quran/Wikisource collection. Editorial punctuation/Unicode glyph choices are not asserted to be a facsimile of a printed edition.

1. Muslim 2692: https://dorar.net/h/Cfuml05Y?osoul=1
   Cross-reference: https://binwahaf.com/ar/audio-book-lesson/1131/
   Wording «سبحان الله وبحمده» and repetition count 100 verified against the authenticated source.
2. Bukhari 6306: https://sunnah.com/bukhari:6306
   Complete supplication from «اللهم أنت ربي» through «فإنه لا يغفر الذنوب إلا أنت»; no fabricated reward, machine-generated prayer or auto-translation.

Source details are included in the existing Sources & credits screen. Human scholarly review is welcome before expanding the corpus; no claim of certification by a religious authority.

## Actual upstream verification
2026-11-03: one HTTPS request to existing TheSportsDB public endpoint returned HTTP 200, one fixture, `strLeague = AFC Champions League Elite`, timestamp present. This validates field availability, **not complete Saudi coverage or a physical-TV network test**.

Important remaining provider limitations:
- Free `eventsnext` is documented as one HOME fixture only. No claim that this is always the team's nearest match (away fixtures may be omitted).
- Live scores are NOT implemented; the provider puts livescore access in a paid plan.
- Club badges are NOT bundled or fetched: possession of an API artwork URL is not evidence of redistribution permission. No specific badge license has been cleared in this change. User desire to show a logo does not establish its copyright/trademark status.
- App-store distribution restrictions remain documented. No new representation that public API access grants unlimited use.

Primary references:
https://www.thesportsdb.com/documentation
https://www.thesportsdb.com/docs_pricing.php
https://www.thesportsdb.com/docs_terms_of_use.php

Google Photos is not implemented. Ambient API requires partner access; this task introduces no Google account or OAuth flow.

## Performance and rollback
- No new bitmaps, decoders, services, executors or HTTP endpoints.
- Tiny immutable text set, minute-level UI refresh; calendar conversion is limited to visible occasions and is not a network operation.
- New preference keys `smart_adhkar` (false) and `smart_occasion` (0) leave existing choices unchanged. No destructive migration. Older builds ignore these keys; existing values survive a compatible downgrade.
- Full pre-change source archive, diff and debug APK preserved locally under the release workspace's `stability-0.9/free-extras-0.11.0` directory. These private machine backups are not publication assets.
- This candidate retains com.ufuq.dream, API 28 minimum and the existing local debug identity. It is not a production-signed release.

## TV acceptance (not yet performed)
- Inspect both devotional texts completely at viewing distance, with every bundled Arabic font.
- Enable each option, verify immediate preview, Cancel, reopen, Save, restart.
- Inspect clock-only, devotional-only, match-only and all-on layouts at 720p/1080p, both languages and all themes.
- Observe detail alternation and countdown rollover without clock shrinkage.
- Test midnight, occasion day/next day, device timezone change and offline playback.
- Confirm no extra requests when all optional features are off.
- Do not publish or install before review/authorization.

## Actual validation result
- `assembleDebug lintDebug testDebugUnitTest`: SUCCESS.
- 238 executed cases (API 28 and 34), 0 failures/errors/skips; 16 executions added in FreeExtrasTest.
- Native layout assertions, devotional full-text/citation fit, existing font/layout regressions, new option Save/Cancel, cache compatibility, invalid preference fallback and occasion date rollover passed.
- Lint: 0 errors, 9 existing warnings. Gradle/Kotlin DSL and deprecated Android test API warnings remain.
- APK identity com.ufuq.dream, version 0.11.0-dev1/code 30, minSdk 28/target 34 verified. Signature matches the preserved local baseline debug certificate.
- No device test, deployment, public signing, GitHub push or release. Host-side tests do not establish physical-TV performance.
