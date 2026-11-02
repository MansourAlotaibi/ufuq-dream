# Smart Ambient architecture and service constraints

Optional prayer/weather widgets share SmartPanel and SmartLayout with the actual Dream and live Studio. Clock remains the established renderer. WidgetRegistry reserves a bottom safe band without overwriting the chosen Automatic/3x3 clock position. No football, Quran, hadith or ambient audio is implemented in this release.

## Prayer

AlAdhan online and pinned Adhan Java1.2.1(MIT, Batoul Apps) offline are explicit separate sources. New widgets default OFF; existing saved source/method/Asr/city are preserved. Internet failure never silently changes source. Manual city-centre coordinates and IANA timezone; no GPS.

Online calendar range covers 30 consecutive Gregorian dates. Validate exact dates, coordinates, city, timezone/offsets, method/angles, Asr school and five ordered instants before an atomic snapshot write. Umm al-Qura Fajr18.5°, Isha90 minutes or120 in calculated Ramadan; optional MWL and Hanafi Asr remain explicit. No official mosque certification is claimed. Batch cache bounded to three configurations; legacy32-day cache remains readable. Refresh with fewer than seven forward days; failures back off30min/1h/6h. Missing tomorrow never invents a next Fajr. Gross invalid time or detected wall-clock jump suppresses untrusted readings; a plausible wrong clock after reboot cannot be independently proven correct offline.

HTTPS uses default trust, no redirects, 8-second connect/read bounds and512KiB calendar cap. No numeric AlAdhan quota was verified; conservative batching/backoff avoids repeated requests. https://aladhan.com/prayer-times-api ; https://aladhan.com/credits-and-terms

Current-prayer styling means the latest entered time in today's validated table, not a ruling about the end of religious prayer windows. No current highlight before Fajr if only today's table is known. Only the duration in the next-prayer heading is tinted; future prayer row/name/time remain neutral. No additional timer or request is added.

## Weather

Direct Open-Meteo current temperature/WMO condition with manual city, Celsius/Fahrenheit, selected30/60/180min active-only refresh. CC BY4 data and mandatory adjacent linked attribution, detailed URLs/license in credits. Free hosted endpoint is non-commercial and has no uptime guarantee; commercial forks must arrange suitable access. https://open-meteo.com/en/licence ; https://open-meteo.com/en/terms

One process-wide request lease, generation-safe callbacks, 64KiB body cap, 8-second connect/read bounds, redirects disabled, default TLS. DNS/TLS/HTTP/timeout/offline/parse/clock/storage failures classified without sensitive messages. Bounded weather retries1/5/15/60min; Retry-After clamp1min–6h. Cancellation releases its owned admission token, so reopening does not inherit a false30-minute cooldown. This confirmed dead-state was fixed; the initial historical transport error was not recorded and is not retroactively attributed to DNS or TLS.

One validated latest cached reading per fixed city. Reject unsupported code, wrong units, invalid/future/nonfinite/out-of-range data and observations over48h old. At3h mark the reading stale. Unit conversion is local. Neither preview nor Dream fabricates readings. Native I/O may unwind asynchronously within its finite connect/read bounds; no total wall-time deadline against an indefinitely slow trickle is claimed.

## Consent and lifecycle

Save is the only preference commit. Draft language/theme/media/city/toggle changes are isolated; network requests require matching saved provider configuration. Existing valid city cache can appear immediately. Network callbacks/work removed on stop; no alarm, hidden background poll, analytics, ad SDK, account, precise location or wake lock. INTERNET and ACCESS_NETWORK_STATE are normal permissions documented in PRIVACY.md.

## Localization and media

Arabic/English is per-settings snapshot, not global device locale. Numerals follow language; ICU Umm al-Qura remains the calendar provider. Custom greeting text stays unchanged with bidi isolation. The Studio is rebuilt on locale direction changes after releasing its previous renderer; Save/Cancel/recreation are tested.

Photos and independent themes remain unchanged. No bundled video passes public-redistribution approval; local imported clips stay private. A real scoped picker is required for new imports. Framework stubs are rejected; on those TVs only an existing fixed-mode Video Library is offered. No broad storage permission or automatic provider installation.
