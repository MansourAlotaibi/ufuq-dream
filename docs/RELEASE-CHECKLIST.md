# Public release gate

Source/community preview and a production binary are different gates.

- [x] Owner chose MIT for application code; individual image licenses retained.
- [x] Public source curated without private media, keys, logs, SDK paths or old private Git history.
- [x] Photo metadata/source/license/hash records and in-app credits retained.
- [ ] Maintainer verifies copyright/attribution details of every bundled asset against its linked source immediately before publication.
- [ ] Select and securely back up a production signing identity; review debug-to-production upgrade implications.
- [x] Pin Actions to reviewed official commit SHAs; the workflow runs for pushes and pull requests.
- [ ] Confirm a green clean-Linux CI run for the exact revision being released.
- [ ] Finish physical-TV matrix including a modern Google TV, overnight lifecycle and accessibility.
- [x] Publish the curated source at https://github.com/MansourAlotaibi/ufuq-dream.
- [ ] Review issue triage and private vulnerability reporting configuration.
- [ ] Publish a release with checksum, exact signing status, known limitations and rollback guidance.

The curated source is public. Consult GitHub Actions for the status of a specific commit; source publication does not certify a production binary. Do not upload the original development folder or its history: those contain private local/device diagnostics. The accepted personal APK is not a production-signed public release.
