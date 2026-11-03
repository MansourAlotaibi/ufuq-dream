# Security

Do not publish credentials, private greetings or unrestricted device logs in issues. Private vulnerability reporting is enabled: use the repository Security → Report a vulnerability flow. Avoid disclosing exploit details in public issues.

The app has no network listener. Normal INTERNET and ACCESS_NETWORK_STATE permissions support explicitly saved optional prayer/weather/fixture requests; no location permission or background service is added. DreamService is exported only behind Android's BIND_DREAM_SERVICE permission; the TV launcher settings activity is intentionally exported. The user-selected local media catalog is bounded and restricted to app-internal relative filenames. This does not replace adversarial testing of malformed media/decoder behavior.

Never submit signing keys. Production signing and dependency maintenance are release responsibilities, not proof supplied by a successful debug build.

GitHub Secret Protection/push protection and dependency graph/Dependabot alerts are enabled. This is not a claim of zero vulnerabilities. The offline tracked-file gate checks limited patterns and binaries; signing secrets stay off GitHub.
