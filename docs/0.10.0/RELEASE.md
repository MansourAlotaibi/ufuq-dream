# 0.10.0-rc1 — public review release

## Accepted feature baseline

The owner tested and explicitly accepted 0.10.0-dev5 on first-generation Xiaomi Mi Box S, MIBOX4/oneday, API28, then authorized public publication. The public app retains that runtime implementation, contextual settings, side-based clock composition, fonts, all smart widgets and content-sized backdrops. Differences are release version/build metadata, public-facing provider/source wording, production certificate and a CI-discovered fix explicitly binding ICU clock/date formatting to the current device timezone. No prayer calculation method or media behavior changed. Personal videos are not bundled; the playback engine/import remain. No installed app, USB setting or device data is changed by this publication workflow.

## Verification

Local clean Debug/Release compilation, lint and complete unit suites are rerun for the public candidate. The accepted development baseline passed 210 tests per variant (420 executions), five host media-pipeline tests, and lint with zero errors/nine warnings per variant. Actual public build/CI/signing results accompany the GitHub Release as QA-REPORT.json and release-verification.json; do not infer a CI or signing pass merely from this document.

Existing regression coverage: API28/34 native layouts; Arabic/English; all three themes; shared Studio/Dream presentation; prayer/cache boundaries; weather failures and consent; next fixtures; complete religious text; all bundled fonts; legacy position conversion; Save/Cancel; contextual option visibility; 720p/1080p; 16 widget combinations and transparent empty backdrops; bitmap/player/session cleanup. Host tests are not a physical hardware-decoder, D-pad press, sleep/wake or endurance certification.

## Remaining Stable gates

- Keychain password access and a temporary-copy recovery/signing proof for the matching local encrypted backup passed on 2026-11-03 without exposing secrets. Both copies are on the same Mac: independent offline production-key AND separately recovered password verification remain unverified.
- The owner accepted the debug-signed counterpart. Production signature/package are verified on host; a production APK cannot overwrite that debug installation. It has not been installed there to bypass signature protection.
- Newer physical Android TV/Google TV hardware, production-key isolated installation, sleep/wake, long-running current-build memory/decoder behavior and document-picker coverage remain limited/unverified. Earlier-version endurance results are not new-version evidence.
- Provider outages/rate limits, non-commercial free weather terms and paid app-store sports requirement remain applicable. No official-mosque or provider-uptime claims.

Therefore publish as **RC/prerelease**, not Stable. It is installable for ordinary supported-TV users with documented limits; no developer tools are required for normal use.

## Upgrade / rollback

Public production certificate remains SHA-256 de13674be215040bafb029fe637a74e9e981accfa5a763bba62ad99dbcda5169. VersionCode28 is above prior public22 and accepted private27. Existing production users can install the update without uninstalling. Existing differently signed debug/independent builds cannot update across keys; do not uninstall/clear data simply to bypass this.

Existing legacy grid positions map to left/right/automatic without resetting other preferences; hidden controls retain values. On confirmed migration the old raw position is retained locally. For production rollback prefer a same-production-key, higher-versionCode rebuild of a known accepted tag. Do not promise a non-debug Android downgrade or preservation through uninstall. User-imported media stays private and must not be published with a source or APK backup.

## CI-discovered timezone regression
The first Linux UTC run failed two hour-format assertions although local Riyadh tests passed. ICU retained a process-default timezone while Java time followed the changed device zone. Formatting now explicitly uses the current device timezone for clock and both dates; a regression crosses UTC/Riyadh midnight without mutating the global ICU timezone. Full local suites passed in UTC (212 tests per variant, 424 executions). The fix must pass CI before publication; no failure was suppressed.
