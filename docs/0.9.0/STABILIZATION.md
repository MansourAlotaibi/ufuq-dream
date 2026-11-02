# 0.8.3 stabilization decision — 2026-11-02

**Keep v0.8.3-rc1 as prerelease. No Stable tag/APK was created.**

- Public main and local starting tree: cd9f82363112c9710f8eaacb80334069bbe59c5e. Local tree was clean and public branch matched before feature work.
- Public release APK SHA256 1fc9f6282f4096539e7a3cb954c94f691d103d43b528454baf5e0133ea95eb83; production certificate de13674be215040bafb029fe637a74e9e981accfa5a763bba62ad99dbcda5169. Rechecked package/API/manifest/signatures/source marker/photo hashes. Tag CI41431619952 is successful.
- Clean build,74 baseline Android tests and5 media tests were already executed for this exact RC and are documented in its public attachments; the full suite is run again for0.9 development. This is not a new physical-TV run of RC.
- The primary encrypted keystore and the second encrypted copy on the same Mac exist and match byte-for-byte. Password was not read for this audit. This is not an independent backup or a restore test.
- Owner explicitly confirmed that independent key/password backup and recovery test are not completed. **Signing recovery gate remains open.** Never generate a replacement key to bypass it.
- Owner reports no second TV. No Android system image/AVD is installed locally. **Isolated production-APK physical acceptance remains open.** The existing Mi Box debug package cannot receive the differently signed production APK. Do not uninstall,clear,rename,overwrite or change debugging/storage to test it.
- Safe future method: dedicated Android TV test device with no com.ufuq.dream installation, or an isolated new AVD/system image for installer/settings checks. An emulator cannot certify Amlogic decoding,D-pad hardware,sleep/wake,overscan or overnight memory. No emulator/device was provisioned or contacted by this task.
- Physical checklist remains pending: installed RC certificate/version,all palettes/13photos/rotation,3x3/automatic positions,numerals/dates/greetings,Save/Cancel,Dream start-stop-restart,sleep/wake,long-run PSS and native video import/playback/fallback. Prior accepted debug experience is not a substitute for testing the production APK.
- All13 photo records/source hashes/licenses remain unchanged by0.9. Adhan and weather licenses are audited separately on the feature branch. No unverified video is added.

## Owner recovery action
Copy the existing encrypted production PKCS#12 to independently controlled offline storage. Recover its password from a separately preserved private record, not merely the live Keychain. Run `tools/verify_signing_backup.py BACKUP --confirm-independent --report PRIVATE_REPORT` in a private terminal with JDK17. It prompts without echo,tests a temporary copy and compares the public production certificate. It does not prove physical independence; the owner must attest that. Never send secrets to a chat/issue or commit reports containing local paths. A real sign/verify restore drill on an isolated machine is recommended before stable; do not run --initialize.
