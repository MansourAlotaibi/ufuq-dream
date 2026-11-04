# 0.12.0-dev1 — offline-first reviewed text updates

Local development branch only. No GitHub push, public release, TV installation, ADB, debugging/storage/launcher changes in this task.

## Behavior
The existing religious-content choice exposes one dependent Library updates toggle. Default OFF for existing/new users. Only a user-confirmed saved toggle plus visible/enabled reading admits requests; staged choices never silently grant consent. Hidden reading stops requests while retaining the preference and texts.

After the last successful check, wait15 days. At the next visible Studio/Dream minute tick, check validated internet capabilities and then make at most one conditional HTTPS GET. Wi-Fi/captive-portal presence alone is insufficient. Offline/capability-unavailable devices make zero requests and do not advance the schedule. No JobScheduler, WorkManager, alarm, wake lock, hidden polling or extra permission. No global font/theme changes.

A single small envelope contains the complete curated collection, avoiding one API call per item. ETag/304 avoids retransmission when unchanged. A304 is accepted only if the local authenticated version/digest matches its saved ETag. Lost/corrupt higher-version caches clear conditional request eligibility and must fetch valid content again. Requests have connect/read8s timeouts, a20s monotonic body deadline, no redirects and a512KiB body cap. Failures retain current texts and retry after1/3/7/15 days; bounded Retry-After can extend the delay. Invalid clocks pause polling. Checks occur on demand while visible, not at an exact wall-clock alarm time.

## Text integrity and storage
Pin the existing production certificate and verify SHA256withRSA over exact UTF-8 payload bytes. Reject malformed encoding/JSON, wrong/tampered signatures, incompatible schemas/app versions, oversized bodies, unsupported sources/licenses, empty kinds, version rollback and same-version/different-content replay. No downloaded executable code, HTML renderer, scripts, images or arbitrary remote URL.

AtomicFile preserves the previous complete library across interruption. Only authenticated, validated payloads replace it. A bounded immutable in-memory snapshot avoids repeat signature/parsing work during clock ticks and holds no Activity. Cache failure falls back to the signed bundled pack (or original library if a bundled resource is unavailable). A saved high-water version prevents accepting an older network update after cache loss; the trusted compiled fallback may still be used offline.

Network work runs on one finite executor; process-wide admission permits one request at a time across Preview/Dream. Generation checks and cancellation prevent a stopped view from applying a late result/callback. The controller releases worker/connection/callback resources on exit; no new redraw timer is introduced. Schedule checks precede network capability queries; seconds mode does not issue per-second capability checks.

## Sources, privacy and current limitation
The first signed collection is exactly the current4 verses,3 hadith selections and2 remembrance texts. It is not a new larger corpus. Legacy text and notices remain unchanged. Newly considered API sources (including HadeethEnc) still require completed storage/redistribution and scholarly/reference review before additions; no third-party API text is auto-approved by being online.

GitHub receives ordinary client IP/request headers and an optional content ETag. No city, account, greeting, user media, custom occasion, favorite team or precise location is sent. Sources/credits use the actual cached collection. No new permission, third-party SDK, credentials in APK, analytics or account requirement.

The prepared channel file remains local until explicit publication approval. Live production download and physical-TV validated-network behavior have not been tested; local mocked transports and cryptographic fixtures exercise the complete success/failure path. Do not claim online distribution is active yet.

## Review checklist
- Toggle is visible only for enabled reading and remains staged through Save/Cancel/Back.
- Offline/captive portal never polls; reconnect during visible use permits a due check.
- Successful check suppresses requests for15 days;304 keeps the same valid library.
- Failed/time-out/tampered/incompatible update keeps complete existing texts and required notices.
- Preview/Dream rapid start/stop, no late callback, no repeated admission leak.
- Review expanded texts at720p/1080p with Arabic fonts before releasing them.
- Review current channel reachability and permission for every added text before publication.

## Actual local validation
- assembleDebug, lintDebug, testDebugUnitTest: SUCCESS.
- 284 executed framework test cases across API28/34, zero failures/errors/skips.
- New coverage includes offline/no-consent zero requests, real production signature verification, tampering, invalid schemas/sources/versions, rollback rejection, corrupt/interrupted cache recovery, ETag/304 cadence, timeout/rate-limit backoff, rapid start/stop and staged Studio settings.
- Native layout checks include all nine reviewed texts/citations, three themes, both languages and every selectable font, alongside existing regression cases.
- Lint:0 errors/9 existing warnings. Deprecated Gradle/Kotlin DSL and Android test API warnings remain.
- Offline source/media/content-signature gates passed. No live public-channel, physical-TV or endurance test performed; no deployment or publication.
