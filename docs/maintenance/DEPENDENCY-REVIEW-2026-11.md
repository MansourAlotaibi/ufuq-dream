# Dependency review — November 2026

The accepted public application remains v0.9.0-rc1 (versionCode22). Existing release assets and tags are not replaced. This maintenance changes build/test infrastructure only; production sources, assets, package, SDK levels, signing configuration and runtime dependency versions are unchanged.

| Original PR | Proposal | Decision / evidence |
|---|---|---|
| #3 | checkout7.0.1 | Accept in combined maintenance. SHA-pinned; `persist-credentials:false`, read-only token and normal pull_request events retained. Node24 requires runner2.327.1+; GitHub-hosted Ubuntu24 supports it. No unsafe checkout override. |
| #4 | setup-java6.1.0 | Accept in combined maintenance. SHA-pinned, Temurin17 retained. Current action verifies authoritative download checksums when available. No new key, server credential or GPG signing input. |
| #5 | upload-artifact7.0.2 | Accept in combined maintenance. SHA-pinned. Default zipped archive behavior preserved for multiple outputs; single-file direct-upload mode is not enabled. Hidden files remain excluded. |
| #6 | Gradle9.8.2 | Accept with AGP8.7.3/KGP2.0.21 after actual clean validation. Wrapper and distribution SHA256 checked, not trusted only because Dependabot proposed them. |
| #7 | Robolectric4.17 | Accept, test-only. API28/API34 regression suite passed; not a new Android runtime dependency. |
| #2 | AGP9.5.0 | Defer standalone bump. Existing CI with Gradle8.9 fails minimum9.6.0. Local9.5+Gradle9.8.2 probe then fails the external Kotlin plugin with duplicate `kotlin` extension. Requires coordinated built-in Kotlin/DSL migration, tracked in [#10](https://github.com/MansourAlotaibi/ufuq-dream/issues/10). No compatibility opt-out is silently added. |

## Actual validation

- Local JVM: JetBrains Runtime21.0.9; Java/Kotlin bytecode target remains17. CI uses Temurin17. These are separate from Android minimum API28.
- `clean assembleDebug assembleRelease lintDebug lintRelease test --warning-mode all`: success.
- Debug146 tests and Release146 tests, no failures/errors/skips. Five host media tests pass. Lint0errors/9warnings per variant.
- `releaseRuntimeClasspath`: Kotlin stdlib2.0.21, JetBrains annotations13.0, Adhan1.2.1. No new APK runtime library. Robolectric stays test-only.
- `app/src/main` has no diff. Manifest remains com.ufuq.dream/API28/target34 with only INTERNET and ACCESS_NETWORK_STATE. No device command, app install, new release APK upload or signing-key operation is performed for this maintenance.
- Combined CI on the current application must pass before merge. Individual older PR checks are useful evidence but do not replace testing the combined current tree.

## Wrapper provenance

Gradle9.8.2 wrapper JAR SHA256: `3b8a25775a69158b5ad2b1d17a80a88dc7b40a352a73eb27d06c612a7ce68e98`.
Verified against https://services.gradle.org/distributions/gradle-9.8.2-wrapper.jar.sha256 .
Distribution SHA256: `21bd5da2de49b642904077f38c0efdc7778e92b3ed0889392dca61b97e389607`, enforced by wrapper properties.

Actions upstream documentation at the selected commits:
- https://github.com/actions/checkout/tree/3d3c42e5aac5ba805825da76410c181273ba90b1
- https://github.com/actions/setup-java/tree/b843ebf9b913146b38da2eaa7aeddf76074ae1ad
- https://github.com/actions/upload-artifact/tree/cf430e030ddbb5b0abf93d22962f4752f3646cd9

## Reviewed warnings and follow-up

Existing source/compiler Android deprecations remain. Gradle9 reports upstream AGP/KGP multi-string dependency notation, boolean bean naming, enum attribute, project notation and Configuration.setVisible deprecations. Some become errors under Gradle10/11. They do not fail9.8.2; no suppression flag or silent third-party code patch is used. Plan the coordinated migration before raising Gradle again. Native-TV performance is not inferred from host tests.

Dependabot and security monitoring remain enabled. Failed AGP-only proposal is closed/deferred with the migration issue, not blindly merged or permanently hidden. Other proposals are resolved by the combined maintenance PR after its CI succeeds. Future updates continue to receive review.
