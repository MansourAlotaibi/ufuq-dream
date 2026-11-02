# Public release gate

Source/community preview and a production binary are different gates.

- [x] Owner chose MIT for application code; individual image licenses retained.
- [x] Public source curated without private media, keys, logs, SDK paths or old private Git history.
- [x] Photo metadata/source/license/hash records and in-app credits retained.
- [ ] Maintainer verifies copyright/attribution details of every bundled asset against its linked source immediately before publication.
- [ ] Select and securely back up a production signing identity; review debug-to-production upgrade implications.
- [ ] Run the committed GitHub workflow on a clean Linux runner and pin Actions to reviewed SHAs.
- [ ] Finish physical-TV matrix including a modern Google TV, overnight lifecycle and accessibility.
- [ ] Choose repository owner/name, review the final upload, enable issues and private vulnerability reporting.
- [ ] Publish a release with checksum, exact signing status, known limitations and rollback guidance.

The source export is prepared locally; no repository or release has been published by this audit. Do not upload the original development folder or its history: those contain private local/device diagnostics. The accepted personal APK is not a production-signed public release.
