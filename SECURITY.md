# Security

Do not publish credentials, private greetings or unrestricted device logs in issues. Maintainers should enable GitHub Private vulnerability reporting before public launch; until a private channel is configured, avoid disclosing exploit details publicly.

The app has no network listener or Internet permission. DreamService is exported only behind Android's BIND_DREAM_SERVICE permission; the TV launcher settings activity is intentionally exported. The optional developer media catalog is bounded and restricted to app-internal relative filenames. This does not replace adversarial testing of malformed media/decoder behavior.

Never submit signing keys. Production signing and dependency maintenance are release responsibilities, not proof supplied by a successful debug build.
