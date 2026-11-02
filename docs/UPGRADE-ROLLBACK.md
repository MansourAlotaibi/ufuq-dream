# Upgrade and rollback / الترقية والرجوع

- Public package remains `com.ufuq.dream`. 0.8.2 is versionCode14; 0.8.3-rc1 is versionCode15. Preference schema/keys are preserved. Night dimming remains opt-in. No old display modes were reintroduced.
- Accepted personal APK uses Android Debug certificate SHA-256 `4410e47d17424072462e54aaea741ed865afb474367f027c1c45f9e7e794e3d3`. This is a public certificate, not a secret.
- The public production release has a different certificate. Android correctly rejects an in-place update from the personal debug install. **Do not uninstall or clear data to bypass this on an existing TV.** The existing installation is untouched by publication. There is no automatic cross-certificate preference migration/export in this RC.
- A fresh user installs the production APK normally. Subsequent higher-versionCode releases signed with this same production key update in place and preserve app data/preferences (subject to Android/vendor behavior).
- Lower-version APK installation may be blocked by Android. A production-signed previous APK is required for any compatible rollback. The private 0.8.2 debug APK cannot roll back a production install in place. Never promise data-preserving downgrade across signatures or schema changes.
- Safest recovery is a forward fix with the same production key and a higher versionCode. Keep the prior source, APK, checksums and public certificate metadata. No downgrade command, uninstall or data clear is performed by this project.

لا تحتاج إلى استبدال نسختك المقبولة على Mi Box لاختبار نشر GitHub. لا تغيّر USB Debugging أو التخزين المتبنّى أو المشغّل. نسخة RC الجديدة لم تُثبّت على جهاز المالك أثناء تجهيز الإصدار.
