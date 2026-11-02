# أُفُق · Ufuq Dream

شاشة توقف سعودية هادئة لـ Android TV، مصممة للبيت والريموت. مجانية، بلا إعلانات، وتعمل دون إنترنت.

A Saudi cinematic screensaver for Android TV. Native Kotlin Views, Arabic-first, offline, no ads or analytics.

## الحالة / Status

**0.8.3-rc1 — installable production-signed release candidate.** Tested on Xiaomi Mi Box S (MIBOX4 / oneday), Android 9 / API 28. Broader Android TV / Google TV compatibility remains a testing goal, not a certification. The accepted 0.8.2 installation is preserved; publication does not update anyone's TV. The new local-video picker and production APK await physical-TV acceptance.

## ما تتضمنه النسخة العامة

- 13 Saudi photographs: AlUla (5), Diriyah (3), Riyadh (5), with individual source/license records and in-app credits.
- Three independent visual palettes: Saudi Green, Desert Earth, Riyadh Night.
- Large live customization preview, explicit Save/Cancel, D-pad navigation and nine clock positions plus Automatic.
- Arabic or Latin digits, 12/24-hour clock, optional seconds, Gregorian and Umm al-Qura Hijri dates.
- Automatic, predefined, custom or hidden greeting; optional night dimming, off by default.
- Subtle text shifts and 1/3/5-minute photo rotation or a fixed image. Movement does not guarantee prevention of display burn-in.

**No videos are included in the public source/APK.** Personal testing used a separately obtained local video; it is excluded from public distribution. Without eligible local media the app uses photos and hides motion-only controls. Use **إضافة فيديو من ملفاتك** to import your own compatible silent MP4 through Android's document picker; no ADB is needed. See [Local video](docs/LOCAL-VIDEO.md). Do not expect a fresh public install to contain the personal video shown in private testing.

## معرض / Gallery

Host-rendered examples using the actual app renderer; not photographs of a physical TV. Clock and dates are live in the app, not baked into the backgrounds. [Screenshot credits and licenses](docs/images/CREDITS.md).

![الدرعية](docs/images/diriyah-host.jpg)
![العلا](docs/images/alula-host.jpg)
![ليل الرياض](docs/images/riyadh-host.jpg)

## Download and install / التحميل والتثبيت

Get the production-signed APK and SHA256SUMS from [Releases](https://github.com/MansourAlotaibi/ufuq-dream/releases). [Installation guide / دليل التثبيت](docs/INSTALL.md). Android 9+ TV, landscape, D-pad. Existing differently signed debug installs cannot be updated in place; [upgrade and rollback](docs/UPGRADE-ROLLBACK.md). The APK includes photos and supports personal video import, but contains no bundled video.

## Build / البناء

JDK 17, Android SDK platform 34 and build tools 34.0.0. Android Studio can provision the SDK. The Gradle wrapper downloads build dependencies; normal app use is offline.

```sh
./gradlew assembleDebug assembleRelease lintDebug lintRelease testDebugUnitTest
python3 tools/check_public_assets.py
# Optional media-pipeline tests, requires ffmpeg and ffprobe:
python3 -m unittest discover -s tools -p 'test_*.py'
```

Output: `app/build/outputs/apk/debug/app-debug.apk`. This is a **debug build**, not a production-signed release. Independent builders have different signing keys. Never uninstall or clear data merely to bypass a certificate mismatch.

Install your build using your TV's normal APK installer, or an explicitly authorized developer workflow. Select أُفُق in the TV's native Settings → Screensaver / Daydream; menu names differ by manufacturer. There is no automatic default/launcher replacement. Save customization explicitly before leaving settings.

## Privacy and safety

No app-requested permissions, Internet access, account, telemetry, microphone, location or wake-lock permission. Preferences and user-selected local media stay in app-internal storage. No root, firmware operation, launcher change or adopted-storage operation is performed. Uninstalling removes app-local settings/media. See [privacy](PRIVACY.md).

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Local video](docs/LOCAL-VIDEO.md)
- [Signing](docs/SIGNING.md)
- [Release readiness and feature parity](docs/release/READINESS.md)
- [Photo rights and credits](THIRD_PARTY_NOTICES.md)
- [TV acceptance checklist](docs/TV-ACCEPTANCE.md)
- [Roadmap](ROADMAP.md)
- [Contribution guide](CONTRIBUTING.md)
- [Release checklist](docs/RELEASE-CHECKLIST.md)

## License

Application code and project-authored documentation: [MIT](LICENSE). Photographs and their thumbnails retain their individual **CC BY / CC BY-SA** licenses; they are not relicensed under MIT. Preserve creator/title/source/license/change notices when redistributing. Third-party build tools and dependencies retain their own licenses. No endorsement by Saudi tourism bodies, photo creators or device manufacturers is implied.
