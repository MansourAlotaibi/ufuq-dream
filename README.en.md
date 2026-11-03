# Ufuq Dream — Saudi cinematic ambient display

[العربية](README.md) · **[Download 0.11.0-rc1 APK](https://github.com/MansourAlotaibi/ufuq-dream/releases/tag/v0.11.0-rc1)** · [Install](docs/INSTALL.md)

Free Android TV / Google TV screensaver, Android 9 / API 28+, landscape and D-pad. No ads, accounts or analytics. Its underlying development build was accepted on the owner's first-generation Mi Box S. This remains a public release candidate: independent signing-key recovery and isolated production-signed TV/wider-hardware acceptance are not yet verified.

## Included

- 13 licensed Saudi photos (AlUla 5 / Diriyah 3 / Riyadh 5), three independent visual themes.
- Live Preview Studio with explicit Save/Discard and contextual settings. Hidden preferences are retained.
- Automatic / Left / Right clock placement; editorial information on the opposite side. Content-sized translucent backdrops disappear when empty.
- Arabic/English, automatic matching numerals, Gregorian/Umm al-Qura dates, optional seconds. One 12/24-hour policy for clock, prayers and fixture kickoff.
- Separate saved language fonts: Cairo, Tajawal, IBM Plex Sans Arabic, Readex Pro, Almarai; English Default/Lato. The clock digits and original religious body retain dedicated typography.
- Unified offline Quran, hadith and remembrance selector. Pick any combination; selected types share one text area. Small referenced corpus: four verses, three hadith-text selections and two authentic devotional texts from Bukhari/Muslim. No machine translation.
- Optional nearest-occasion countdown: Ramadan, the two Eids, National Day and Founding Day; fixed selection or a custom name/Gregorian date. The occasion has an independent line beside the dates, never replacing match details. Hijri occasions are provisional calendar estimates.
- Verified bilingual location captions from the actual photograph, independent of visual theme; imported personal videos do not inherit an invented place from their fallback poster.
- Optional prayer times from AlAdhan with approximately 30 days of validated offline coverage, or an explicitly chosen offline calculation. Never silently switches methods. Calculated times are not official mosque certification.
- Optional weather, selected city, C/F, cache/offline fallback and source attribution.
- Optional fixture date, competition and locally calculated kickoff countdown share a compact detail slot. The free provider may return a home fixture only, not the nearest match including away fixtures. No club artwork or live-score subscription.
- Greetings, optional night dimming (off by default), subtle text shifts and 1/3/5-minute photo rotation or fixed background. Movement is not guaranteed burn-in prevention.

## Media and previews

No personal video is bundled: redistribution permission for privately tested footage remains unverified. The hardware H.264 playback engine, loops, static fallback and local import remain functional. Import requires a genuine installed Android document picker; some TVs only have a stub. Compatible existing clips are available in the category/type-aware Fixed Background picker. [Formats, resource limits and picker limitations](docs/LOCAL-VIDEO.md).

[Visual gallery](README.md#صور-من-التطبيق) uses real host rendering, not physical-TV captures or a promise of live fixture/weather data. Preserve [screenshot credits](docs/0.10.0/images/CREDITS.md).

## Install / build / privacy

Download the signed APK and SHA256SUMS, verify integrity, install using your TV's trusted installer, open Ufuq, customize and Save. Select it in native Screensaver/Daydream settings. Manufacturers can restrict selection; no ADB or launcher replacement is required. Updates from prior production releases keep the same production certificate. Debug installations use a different key: never uninstall or clear data simply to bypass a mismatch. [Upgrade/rollback](docs/UPGRADE-ROLLBACK.md).

Build with JDK 17/21 and SDK/build-tools 34: `./gradlew assembleDebug assembleRelease lintDebug lintRelease test`. CI runs Temurin 17, limited offline source/media gates and unit tests. Host tests do not certify TV decoder performance or endurance. [Release results/limits](docs/0.11.0/RELEASE.md).

Network widgets default off and require explicit Save. Only selected city-centre coordinates/calculation parameters or team IDs are sent while visible. No precise location permission, tracking, background listener or media upload. [Privacy](PRIVACY.md).

Code: MIT. Photos, font files, Quran text and hadith transcriptions retain their own notices/licenses. This nonprofit GitHub edition uses free provider endpoints; commercial forks and app-store distribution must separately review provider terms/subscriptions. [Credits](THIRD_PARTY_NOTICES.md) · [Providers](docs/0.10.0/PROVIDERS.md) · [Contributing](CONTRIBUTING.md) · [Roadmap](ROADMAP.md).
