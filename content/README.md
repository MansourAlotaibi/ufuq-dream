# Reviewed reading channel (local development)

`reviewed-payload.json` is human-readable reviewed text data, not MIT application code. Entries preserve the original text, references, attribution, source license URLs and complete notices. Existing Quran text remains Tanzil Uthmani1.1 / CC BY3.0 plus verbatim-copy conditions; existing hadith transcriptions remain Arabic Wikisource CC BY-SA4.0. Traditional devotional words retain their Bukhari/Muslim references. See ../THIRD_PARTY_NOTICES.md and ../app/src/main/assets/daily_reflection.json.

The first pack intentionally contains exactly the existing nine reviewed texts (4 Quran,3 hadith,2 remembrance). No external API corpus or new religious text was imported. A larger corpus still requires actual text/source/authenticity/display and redistribution review. A cryptographic signature proves maintainer approval, not scholarly certification.

`library.json` is an envelope containing the exact payload bytes in Base64 plus an RSA/SHA256 signature made using the existing production identity. The bundled `app/src/main/assets/content-verifier-cert.der` is a **public X.509 certificate**, not a keystore, password or private key. The app pins its fingerprint. The same signed envelope is bundled for first-run/offline operation.

The runtime channel is the HTTPS raw GitHub main-branch URL for this file, not an upstream hadith provider. This branch and channel have **not been published**. Until approved source publication places this file on main, enabling a development build may receive404; it must retain its bundled texts and back off. No claim of live channel verification is made.

Maintainer procedure:
1. Independently review complete wording, source/reference, licensing and native TV fit of each added text. Do not normalize, shorten or strip Quran text. Keep at least one entry per supported kind and preserve required notices.
2. Increase payload version monotonically; set minimum compatible app code and actual review date. Never modify a published version in place.
3. Run tools/sign_content.py with the reviewed payload and output paths. It retrieves the existing password through Keychain and never creates a signing identity, exports a private key or places a password in arguments. The generated certificate is public only. Never bundle a secret key.
4. Validate the envelope and bundled copy with tools/check_content_library.py, Android regression/layout tests and physical review. Publish only after owner approval.
5. Signatures must authenticate exact bytes. Future forks must deliberately choose their own channel/trust identity; they must not replace this project's release key.
