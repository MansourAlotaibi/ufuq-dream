# 0.10.0-dev5 — content-sized widget backdrops

Local-only owner review artifact. Package and signing identity unchanged, versionCode 27. Accepted dev4 APK/source remain preserved; this task does not install or publish.

## Verified cause

The editorial column and information footer intentionally reserve bounded layout regions to keep long religious text, clock placement and font metrics safe. Their old GradientDrawable backgrounds covered the entire region. GONE children did not resize the reserved column, so a single fixture inherited a tall mostly empty card. SmartPanel can also remain active for editorial content when prayer/weather are both off; painting its container is conceptually different from painting visible footer content.

## Fix

ContentBackdrop measures the rendered text bounds of visible descendants, then paints the same restrained gradient and rounded corners around that union with modest padding. It honors ancestor visibility, vertical text gravity, real wrapped lines, reference placement and the weather compound icon. Bounds are clamped to the owner view. When there is no visible text, draw paints nothing—even if the owning layout is retained for lifecycle or placement purposes.

- Fixture only: compact card around the matchup and kickoff.
- Reflection only: card follows complete quote and citation; a short quote occupies less background than a long verse.
- Both: backdrop covers the visible combined content, not unused column edges.
- Weather only: compact centered reading/icon/required credit backdrop.
- Prayers only: backdrop follows the prayer strip without an empty weather area.
- Prayer/weather both disabled: no footer backdrop.
- All optional widgets disabled: neither information backdrop is painted.

Clock/text layout regions, typography, safe areas, religious text, preview/Dream shared renderer, Save/Cancel, network/cache policy, media engine and user preferences are unchanged. This separates placement budget from painted decoration rather than shrinking a safety region and accidentally shrinking the clock again.

## Resource behavior

Native Drawable, gradients and small rectangles only. No bitmap, player, network request, new framework, timer or continuous animation. Visible text is inspected during existing draw passes; the content tree is small and bounded. It stores no Activity in static state. No new permission or asset.

## Verification

Automated tests cover all 16 prayer/weather/reflection/fixture combinations across three themes and both languages on APIs 28/34; content bounds, compact fixture and weather-only cards, zero painted pixels when empty, transitions back to all-off, long/short reflection resizing without clock geometry changes, 720p and all bundled Arabic fonts. Generated gallery uses native host rendering and dated provider reference fixtures, not physical-TV runtime evidence.

Owner review still required: all-off, each widget alone, both in each group, long verse, missing-data states and normal video/photo backgrounds on the TV. No installation or publication until requested.
