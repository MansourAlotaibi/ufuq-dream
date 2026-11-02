# Prayer, reflection and weather provider review

Reviewed 2026-11-02. This is a source audit and provider decision record, not a claim of newly implemented API or reflection widgets.

## Prayer source reconciliation

The available public Ufuq Launcher checkout uses `PrayerCalculator` from `SmartHomeController.renderOfflinePrayer`. Its imported `source-import-2.8.5` source and available Git history did not reveal an `api.aladhan.com` integration. A reference to AlAdhan in its Hijri test is a reference fixture, not a runtime prayer API. The private launcher is not a redistributable source for this project.

Historical reconciliation: the original launcher development chat, `Port CleanYourTV launcher to API 19`, does identify **AlAdhan**. The MansourTV 2.0 work tested `https://api.aladhan.com/v1/timings/26-10-2026?latitude=24.7136&longitude=46.6753&method=4&calendarMethod=UAQ&iso8601=false` from the host. The same development record reports `SSLHandshakeException` on the Nobel Android 4.4.4/API19 TV for both AlAdhan and Open-Meteo, even with TLS1.2 enabled using default trust. The implementation therefore switched prayers to offline calculation, without disabling certificate/hostname validation. This explains the owner's correct recollection and the absence of a runtime prayer API in the final checkout. It is historical evidence, not a new test of service availability or Mi Box Android9 TLS compatibility.

Dream currently uses pinned MIT Adhan Java, not newly written solar mathematics. Offline operation motivated this choice. The historical provider is now identified; no further owner identification is needed. Retain the offline fallback and validate a new online provider on Android9 independently of the old API19 failure. Never ask for a secret in chat.

Proposed online integration, once the service is identified: provider interface; bounded validated city/date/method cache; foreground-only asynchronous synchronization; clearly label API, cached API and locally calculated fallback. Staged settings must not authorize network requests. Match next-prayer calculation to the same provider and local date, including tomorrow. An API response is not automatically an official mosque timetable. AlAdhan documents calculation methods rather than certifying official Saudi schedules: https://aladhan.com/prayer-times-api and https://aladhan.com/calculation-methods .

## Reusable Quran and Hadith corpus

The public launcher contains seven attributed records: four complete Tanzil Uthmani 1.1 verses and three Sahih al-Bukhari matn excerpts selected from revision-pinned Arabic Wikisource. Its offline content verifier was actually run successfully during this review: source hashes, exact text, attribution and copyright notices passed. No corpus files or private launcher material have been copied into Dream yet.

Tanzil requires verbatim text, attribution/link and retained copyright notices: https://tanzil.net/docs/text_license . Preserve the actual downloaded notice, including its date, instead of substituting the website's older notice. Do not reshape, normalize or replace Quran digits inside the stored text. Hadith selections retain their pinned page links, stated omissions and CC BY-SA 4.0 obligations; MIT applies to independent code only.

Integration requirements: optional, off by default; independently selectable Quran/Hadith/both; shared Studio/Dream rendering; Save/Cancel; source access in credits; full unclipped text and reference; no generated religious text; safe-area tests together with prayer/weather and every clock position. Do not append another fixed-height row without proving the combined layout fits.

## Weather recommendation

Retain Open-Meteo as the initial provider behind the existing transport abstraction. Its free service expressly permits nonprofit apps without ads/subscriptions, and data attribution is CC BY 4.0. Commercial forks need separate service terms. It provides no uninterrupted-service guarantee. Sources: https://open-meteo.com/en/terms and https://open-meteo.com/en/docs .

MET Norway is a credible alternative researched, not implemented. Its terms require identifying User-Agent/contact link, attribution, caching/traffic discipline, and explicitly provide no SLA: https://api.met.no/doc/TermsOfService . Do not claim comparative uptime percentages: no independent uptime measurement was completed. Do not silently mix providers or introduce automatic failover without normalization, source attribution and failure tests.

Current Dream safeguards already implemented: app-private latest-success cache, visible freshness/stale state, 30/60/180-minute foreground refresh, process-wide request limiting, finite timeouts, bounded response size, validation and cancellation/late-callback isolation. These reduce disruption; they cannot guarantee upstream availability or forecast accuracy. Current conditions are model data, not a certified reading at the viewer's exact location.

## Status

This provider audit initially changed documentation only. Subsequent owner-authorized local dev2 implements AlAdhan and has been installed as a same-debug-signature update for review; see DEV2-LOCAL-REVIEW.md for actual evidence and unresolved acceptance. Quran/Hadith UI remains unimplemented. No dev2 changes were published. Independent production-key restore verification and isolated physical production-APK acceptance remain open public release gates. Keep v0.8.3-rc1 as RC.
