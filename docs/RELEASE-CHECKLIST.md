# Release gates

## Completed preparation
- [x] Preserve public history and v0.8.2; isolate release branch and backup accepted APK/source.
- [x] Audit actual feature parity and recheck all 13 photo licenses.
- [x] Keep unverified personal footage out; expose scoped local video import instead.
- [x] Establish owner-authorized production key outside Git; password in macOS Keychain, never CI.
- [x] Add release/debug CI, source/media disclosure gates, templates and dependency update configuration.

## Per-release verification
- [ ] Green CI for the exact revision/tag; local build/lint/tests with no failures.
- [ ] APK identity/API/manifest/assets inspected; release is non-debuggable and production-signed.
- [ ] APK source commit marker matches annotated tag; checksum and certificate documented.
- [ ] Release assets downloaded and hashes checked after upload.

## Required before stable
- [ ] Independent offline key/password backup and restore exercise by owner.
- [ ] Physical-TV acceptance of the new import UI and production APK.
- [ ] Broader TV/accessibility/overnight test matrix.

Consult the GitHub Release's verification attachments for the exact per-release outcomes. Unchecked stable gates require prerelease labeling; they must not be silently converted into success claims. Do not upload the original private development tree or its history.
