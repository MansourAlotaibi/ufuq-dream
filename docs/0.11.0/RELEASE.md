# Ufuq Dream 0.11.0-rc1 — public release candidate

The owner accepted the corresponding 0.11.0-dev2 experience on a first-generation Xiaomi Mi Box S / Android9 and explicitly authorized public source + APK publication on 2026-11-04. Release preparation changes the version/source marker/signature and documentation, not feature behavior. The accepted personal debug installation is not replaced.

Package com.ufuq.dream; versionCode32; minSdk28 / targetSdk34. Production updates reuse the established certificate SHA-256 `de13674be215040bafb029fe637a74e9e981accfa5a763bba62ad99dbcda5169`. This differs from the personal debug certificate. Do not uninstall/clear the accepted installation to bypass Android signature protection.

## What is included
- Every previous public feature: DreamService, three independent themes,13 licensed photos, rotation/fixed selection, personal-video import/playback/static fallback, bilingual Studio, matching numerals/fonts, clock sides/automatic placement, dates/seconds/greetings, optional prayer/weather and team fixtures, staged Save/Cancel.
- Verified photo-specific place captions; imported videos are not assigned a location from their fallback photo.
- One Quran/hadith/remembrance selector, any combination; two small traditional devotional texts with authenticated references, no generated religious wording/translation.
- Nearest/fixed/custom occasion countdown; Ramadan, Eid al-Fitr, Eid al-Adha, National Day, Founding Day. Custom name/date remain local. Hijri occasions visibly remain estimates.
- Occasion line with the dates, independent of team details. Football competition and kickoff countdown use existing response/timestamps without more network calls.

## Source, tests and binary verification
Clean debug/release builds, both lint variants, both unit-test variants, asset/repository gates and host media tests are run for publication. Exact results and artifact hashes accompany the release rather than being assumed from the personal APK. APK verification checks package/version/API, non-debuggable flag, established v2/v3 production signatures, source-commit marker, all13 photo hashes and absence of bundled video/private-key files. Native host screenshots are examples, not a claim of physical-TV screenshots or live match/weather data.

## Compatibility and open stable gates
Owner acceptance is evidence for this development counterpart on the named hardware, not every TV or a fresh production-certificate installation. API28 and34 host framework tests do not establish decoder performance, sleep/wake or endurance.

Independent offline signing-keystore/password recovery remains **unverified**. The existing key, Keychain access, certificate and matching encrypted local backup are verified without disclosing secrets; a same-Mac copy is not independent recovery. Do not create a replacement key. Isolated production-signed physical-TV acceptance, wider Android TV/Google TV compatibility, current-version overnight memory/power tests and vendor document-picker differences remain open. Therefore this release stays RC, not Stable.

## Media, service and religious limitations
13 photographs retain individual CC terms, attribution/modification records and checksums. No personal motion footage or club badge is bundled. User-supplied compatible silent H.264 video works through the existing import engine where a genuine document picker exists. Third-party creator rights and provider-access terms are not replaced by MIT.

TheSportsDB's free eventsnext endpoint is documented to return a home fixture only, so coverage may omit an earlier away game. No live-score/subscription feature is advertised. Open-Meteo free hosted access remains noncommercial; app-store/commercial redistribution requires provider review. See ../0.10.0/PROVIDERS.md and ../../THIRD_PARTY_NOTICES.md.

Occasion dates use device-local civil days and ICU Umm al-Qura. They are not official moon-sighting announcements. Religious content is a small curated set, not an exhaustive prayer/Quran application; devotional display does not count recitations. Sources and original text notices remain accessible in the app.

## Migration and rollback
Upgrading a previous public production APK keeps preferences through the same certificate/higher versionCode. The old separate remembrance control merges into the selected unified combination on read, without persisting a draft. Existing fixed occasion choices remain fixed; OFF stays OFF. Custom editor changes become a draft only after editor confirmation and persist only through Studio Save. Invalid custom dates are rejected; expired custom dates say ended rather than rolling silently to next year.

An older build may not understand new content modes4–7 and may require religious-content reselection if downgraded. Android generally rejects a lower versionCode; no automatic or data-safe downgrade is promised. Keep the prior APK/source, avoid uninstall/clear-data, and prefer a same-key forward-fix. The owner’s private dev2 rollback artifacts stay outside Git. No launcher, debugging, adopted storage, firmware or other application was changed by publication.
