# Local bilingual studio candidate

Introduced in the bilingual development candidate; included in public0.9.0-rc1 (22).

## Product decisions

- Keep the existing Automatic / 3x3 group-position selector. Physical remote navigation and safe-area composition are already understood and tested. Independent element positioning would add collisions and remote interactions; three more display modes would reintroduce previously removed complexity.
- Add Arabic / English in Appearance. Arabic is retained for existing installations and is the default. The app does not alter device language, timezone, or system settings.
- Numerals follow the saved or staged application language. Remove the independent digit-format row. Keep the legacy `latin_digits` preference as an update/rollback mirror, not an end-user option.
- A TV without a real scoped file picker must not show an Import action that always fails. If previously imported clips exist, show the working Video Library action instead. It stages a chosen clip and requires Save before playback preferences persist. This is not a new file picker: importing new files still needs a trusted compatible external picker. No broad storage permission, automatic third-party installation, or adopted-storage access is added.

## Implementation

- `DisplaySettings.language` is part of the serializable draft; SettingsRepository writes it in the existing settings transaction. Arabic/English digits are normalized on save/load. Existing greetings, scenes, prayer parameters, cities, units, and media references are preserved.
- AppLanguage owns deterministic, context-independent UI copy/direction. It does not modify the global locale, so unsaved preview cannot change DreamService output. Longest-match one-pass substitution supports existing dynamic labels without translating user-written greeting content. Credits preserve original author/source metadata rather than translating proper names.
- Language changes rebuild the native Studio with a coherent layout direction, stopping the old renderer before starting the new one. Cancel rebuilds the saved language. Back retains the existing Save / Discard / Keep Editing flow.
- DateProvider keeps Android ICU Umm al-Qura, using English month/day names and AM/PM in English. Prayer instants, calculations, request configuration, timezones, and caches are unchanged; only presentation is localized. Custom greetings remain exactly as entered, wrapped with language-aware bidi handling.
- Dialogs mirror direction; the 3x3 grid remains physically left-to-right so positions retain their actual screen meaning. Clock placement dialog sits beside the controls in each language.
- No new framework, network service, dangerous permission, production key, or bundled media.

## Verification scope

Run assembleDebug, assembleRelease, lintDebug, lintRelease and test. Bilingual API28/API34 Robolectric coverage includes staged language, Save/Cancel/restart, saved-instance recreation, digits/date names, custom greeting preservation, untranslated UI detection, weather/prayer rendering, and native 1080p Studio captures. Existing full regression tests remain in place; the obsolete digit-control interaction test now exercises language selection.

Host captures are not physical-TV acceptance. The previous RC1 endurance run does not certify this new binary. Mi Box checks still needed: language toggle, both layouts, D-pad traversal and Back/Save/Discard, modal bounds, 12/24h plus seconds, widget readability, existing personal video selection, and repeated DreamService entry/exit.

## Upgrade / rollback

Use only the update-compatible debug artifact on the existing debug test installation, after owner approval and target/signature verification. Retain the tested RC2 APK and pre-language source archive. No uninstall or data clearing is needed or permitted. An Android downgrade may be refused; stop rather than bypassing signature or version protection. Older builds ignore the new language preference and see the mirrored digit setting. Never install a production-signed APK over the debug package.

Publication remains blocked pending the previously recorded signing backup, physical acceptance and release gates. Public candidate publication is owner-authorized; Stable gates remain open.
