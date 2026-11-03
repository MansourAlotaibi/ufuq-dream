> Historical local-development record. The owner subsequently accepted dev2 on the TV and authorized public publication. See RELEASE.md for the current public candidate and remaining release gates.

# 0.11.0-dev2 — physical-review feedback

Supersedes the dev1 settings/placement descriptions in FREE-EXTRAS.md. No public deployment or device changes in this task.

## One religious-content selector
The existing selector now offers Hidden, Verse, Hadith, Verse + Hadith, Remembrance, Verse + Remembrance, Hadith + Remembrance, or all three. There is no separate remembrance row. New combinations rotate categories in the same content region every five minutes; legacy verse/hadith-only behavior remains unchanged. Texts and religious references are unchanged.

Migration is read-only until the user confirms Save: old reflection mode 0/1/2/3 combined with the old remembrance toggle maps to 4/5/6/7. Saving serializes the unified mode and clears the legacy boolean. No old content is silently switched off. The dev1 source/APK was preserved before changes.

## Occasion selection
- Hidden remains default for users who had it off.
- Nearest automatically selects the smallest nonnegative civil-day distance among Ramadan, Eid al-Fitr, Eid al-Adha, Saudi National Day and Founding Day.
- Founding Day is 22 February each Gregorian year, including today through local midnight, then next year.
- Primary reference: https://www.uqn.gov.sa/details?p=18903 (royal decree; checked 2026-11-03).
- A custom occasion has a name (up to 40 characters) and a Gregorian date in YYYY-MM-DD. Arabic-Indic and Persian date digits normalize to canonical ISO. Invalid dates and leap-day errors are rejected. Past custom dates display “ended” rather than inventing next year's occurrence.
- A custom editor confirms a draft only; the studio's Save still controls persistence. Back/cancel from the editor changes nothing. Studio Cancel restores the saved snapshot.
- Previously selected fixed built-in occasions are preserved and remain selectable; existing users are not silently moved to Automatic.
- Religious occasion dates remain ICU Umm al-Qura estimates, visibly marked as estimates, not official sighting announcements.

## Placement
Occasion text belongs to the clock/date presentation group, immediately after the dates and before the greeting. It has its own two-line bounded TextView, theme-muted ink and restrained shadow, follows the clock position/movement, and disappears completely when hidden. It is shared by Preview and DreamService.

No occasion text is inserted into the favorite-team card, whether a fixture exists or fails to load. Football kickoff/date/competition retain their own existing detail rotation. Occasion-only settings no longer reserve an empty editorial-side region or a fake footer.

## Validation and remaining TV review
Automated checks cover legacy settings migration without implicit writes, selected category combinations, nearest occasion calculation, Founding Day/year boundaries, custom date validation, editor cancel and save, independent placement, and large-clock layouts at 720p/1080p across both languages, three themes and all selectable fonts.

Physical-TV checks still required: remote keyboard/date entry and return focus; whether the chosen date-adjacent placement is aesthetically right at normal viewing distance; Save/Cancel/restart with previously saved dev1 settings; all-on media playback and long-run stability. Host tests do not establish hardware acceptance.

## Rollback
Pre-change dev1 source and APK are privately preserved in the local release workspace. New keys for custom name/date are ignored by the old build. Unified modes 4–7 are not understood by dev1, so a downgrade may require reselecting religious content in dev1. Avoid downgrade/uninstall/clear-data as routine testing; do not claim full backward behavioral compatibility for new modes. Existing fonts, media, themes and prayer/weather configuration are untouched.

## Recorded build result
- assembleDebug, lintDebug and testDebugUnitTest: SUCCESS.
- 254 executed cases, zero failures/errors/skips; API 28 and 34 host environments.
- Lint: zero errors, nine existing warnings; deprecated Gradle/Kotlin DSL and Android test API warnings remain.
- Native 1080p host screenshots reviewed; not physical-TV screenshots.
- APK: com.ufuq.dream, 0.11.0-dev2/code 31, minSdk 28, unchanged debug signing identity.
- No device access, install or GitHub publication in this change.
