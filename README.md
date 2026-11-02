# أُفُق · Ufuq Dream

شاشة توقف سعودية هادئة لـ Android TV، مصممة للبيت والريموت. مجانية، بلا إعلانات، وتعمل دون إنترنت.

A Saudi cinematic screensaver for Android TV. Native Kotlin Views, Arabic-first, offline, no ads or analytics.

## الحالة / Status

**0.8.2 — accepted personal baseline; community preview source.** Tested on Xiaomi Mi Box S (MIBOX4 / oneday), Android 9 / API 28. Broader Android TV / Google TV compatibility remains a testing goal, not a certification. The accepted installed APK is preserved separately; repository preparation does not update anyone's TV.

## ما تتضمنه النسخة العامة

- 13 Saudi photographs: AlUla (5), Diriyah (3), Riyadh (5), with individual source/license records and in-app credits.
- Three independent visual palettes: Saudi Green, Desert Earth, Riyadh Night.
- Large live customization preview, explicit Save/Cancel, D-pad navigation and nine clock positions plus Automatic.
- Arabic or Latin digits, 12/24-hour clock, optional seconds, Gregorian and Umm al-Qura Hijri dates.
- Automatic, predefined, custom or hidden greeting; optional night dimming, off by default.
- Subtle text shifts and 1/3/5-minute photo rotation or a fixed image. Movement does not guarantee prevention of display burn-in.

**No videos are included in the public source/APK.** Personal testing used a separately obtained local video; it is excluded from public distribution. Without eligible local media the app uses photos and hides motion-only controls. The app has no end-user video import UI yet. Do not expect a fresh public install to contain the personal video shown in private testing.

## Build / البناء

JDK 17, Android SDK platform 34 and build tools 34.0.0. Android Studio can provision the SDK. The Gradle wrapper downloads build dependencies; normal app use is offline.

```sh
./gradlew assembleDebug lintDebug testDebugUnitTest
python3 tools/check_public_assets.py
# Optional media-pipeline tests, requires ffmpeg and ffprobe:
python3 -m unittest discover -s tools -p 'test_*.py'
```

Output: `app/build/outputs/apk/debug/app-debug.apk`. This is a **debug build**, not a production-signed release. Independent builders have different signing keys. Never uninstall or clear data merely to bypass a certificate mismatch.

Install your build using your TV's normal APK installer, or an explicitly authorized developer workflow. Select أُفُق in the TV's native Settings → Screensaver / Daydream; menu names differ by manufacturer. There is no automatic default/launcher replacement. Save customization explicitly before leaving settings.

## Privacy and safety

No app-requested permissions, Internet access, account, telemetry, microphone, location or wake-lock permission. Preferences and optional developer-provisioned personal media stay in app-internal storage. No root, firmware operation, launcher change or adopted-storage operation is performed. Uninstalling removes app-local settings/media. See [privacy](PRIVACY.md).

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Photo rights and credits](THIRD_PARTY_NOTICES.md)
- [TV acceptance checklist](docs/TV-ACCEPTANCE.md)
- [Roadmap](ROADMAP.md)
- [Contribution guide](CONTRIBUTING.md)
- [Release checklist](docs/RELEASE-CHECKLIST.md)

## License

Application code and project-authored documentation: [MIT](LICENSE). Photographs and their thumbnails retain their individual **CC BY / CC BY-SA** licenses; they are not relicensed under MIT. Preserve creator/title/source/license/change notices when redistributing. Third-party build tools and dependencies retain their own licenses. No endorsement by Saudi tourism bodies, photo creators or device manufacturers is implied.
