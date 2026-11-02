# Production signing

`com.ufuq.dream`, first public production identity, RSA-4096 / SHA-384 certificate, long-lived encrypted PKCS#12. The public certificate fingerprint is published in the GitHub Release; the private key is never uploaded.

The local maintainer helper `tools/sign_release.py` uses macOS Keychain APIs for the password, passes secrets to keytool/apksigner through child-process environment only, and never logs them or places them in command arguments. The encrypted keystore is stored outside the repository in the owner's Application Support directory with mode 0600 and a mode-0700 parent. A second encrypted local copy is only protection against accidental file loss, **not an independent disaster-recovery backup**.

Before declaring stable, the owner must copy the encrypted keystore to independently controlled offline storage and securely preserve the Keychain password separately (e.g. owner-managed password manager). Keychain Access can reveal it after owner authentication. Do not send the password in an issue/chat. Test restoration privately. Losing the key or password prevents compatible updates; generating another is not recovery.

Local workflow (macOS, JDK17/SDK34 configured):

```sh
export UFUQ_SOURCE_COMMIT="$(git rev-parse HEAD)"
./gradlew clean assembleRelease assembleDebug lintDebug lintRelease testDebugUnitTest
python3 tools/sign_release.py --input app/build/outputs/apk/release/app-release-unsigned.apk --output /tmp/Ufuq-Dream-release.apk
apksigner verify --verbose --print-certs /tmp/Ufuq-Dream-release.apk
```

`--initialize` is one-time owner-authorized setup, never a routine build step. The script refuses to replace an existing identity. macOS may ask the owner to unlock/authorize Keychain access. No signing secrets are configured in GitHub Actions; Actions builds unsigned candidates with read-only repository permissions. Manual signing and release asset upload are separate maintainer gates. Do not enable automated privileged signing for arbitrary pull requests.

The same production key must sign all future updates, with monotonically increasing versionCode. Debug keys and third-party builders' keys are incompatible; see UPGRADE-ROLLBACK.md. No key rotation or v3 proof-of-rotation from the personal debug identity is claimed.
