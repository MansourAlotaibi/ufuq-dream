# Stable release gates

Public0.8.3 and0.9.0 candidates remain prereleases until production signing disaster-recovery, isolated production-APK acceptance and physical-TV release gates pass. Existing published tags are immutable.

The stable production certificate is recorded in docs/SIGNING.md. Never rotate a key to bypass a missing backup. Keep the encrypted keystore on independent owner-controlled storage AND preserve its password separately. Test private restoration/signing using tools/verify_signing_backup.py in a private terminal. Do not send credentials to issues/chats, commit keys or publish recovery reports. The tool checks a copy and public certificate; physical independence is owner-attested.

Production and existing development installations may be differently signed. Use an isolated TV/test environment; never uninstall, clear data or modify the package name to bypass signature protection. An emulator cannot certify hardware decoding, overscan or sleep/wake behavior. See STABILITY-RC.md for the actual measured scope.
