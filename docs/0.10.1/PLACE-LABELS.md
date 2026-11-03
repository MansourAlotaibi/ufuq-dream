> Historical local-development record. The owner subsequently accepted this correction; publication is included in 0.11.0-rc1. See ../0.11.0/RELEASE.md for current status.

# Local place-label correction — 0.10.1-dev1

The published 0.10.0-rc1 and accepted TV installation remain untouched. Work is on local branch fix/verified-place-labels; no publication or deployment in this task.

The old clock eyebrow used the photo category, so every Riyadh photo was labeled Riyadh Night regardless of daylight. The actual selected MediaItem now supplies a bilingual verified location from photo-provenance.json/displayLocation. The same shared renderer is used in Studio and Dream, including category/type fallback and image rotation. Artistic picker titles, source URLs, licenses, photo hashes and image composition are unchanged.

Examples: Diriyah / At-Turaif; AlUla / Elephant Rock; Riyadh / Kingdom Centre; Riyadh / King Abdullah Financial District (compact English KAFD). Caption lengths are restrained to preserve clock prominence; a bounded 14–17sp, at-most-two-line eyebrow supports both languages. A regression caught a long English caption shrinking the clock, so the verified English landmark label was shortened instead of weakening the clock-size assertion.

The photo-category UI is now Riyadh, not Riyadh Night. The independent color theme remains Riyadh Night. No preference IDs or saved values change.

Personal imported videos have no independently verified location field. Their stock poster/category/filename is not proof of location; the caption says Personal video rather than inventing an AlUla/Riyadh attribution. Other unsupported motion metadata uses Motion background. Unknown/missing photo metadata uses Saudi scenes. No new permission, network request or user setting.

Metadata parsing is bounded to the small bundled catalog, cached once as immutable strings, holds no Context, and validates bilingual labels. Build/API/signing compatibility remain unchanged; versionCode29 supports a future same-key debug update.

Regression coverage: every photo/metadata ID and both languages; all three themes; actual filtered-background fallback; identical Preview/Dream caption; private-video poster isolation; longest Arabic labels with all bundled fonts at native 720p; no clipping/ellipsis and safe clock size. Actual final build/lint/test results accompany the local APK. Physical TV review remains pending.
