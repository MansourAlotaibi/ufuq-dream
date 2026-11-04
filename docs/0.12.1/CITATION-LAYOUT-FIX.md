# 0.12.1 — quotation/citation fix — separate quotation and citation flow

The owner reported the final line of Quran16:90 overlapping its citation on a physical Android9 TV. ReflectionView laid out a weighted body normally, then manually moved the citation inside that body using body.layout.height. Those rectangles could overlap; the earlier tests checked text length/height before drawing, not separation after drawing and text replacement.

The fix removes manual citation repositioning and platform body autosizing. The body uses its actual TextView natural measured height, fitting only when needed to the available space after reserving the citation and a4dp gap. Body and citation follow normal vertical flow and remain centered together. A bounded21-to12sp fit uses real elegant/fallback font metrics and is cached by text, dimensions, font scale and typeface. No text is abbreviated, rewritten or ellipsized; library signatures/content are unchanged.

A native regression checks all21 entries, narrow/wide columns, normal/enlarged font scale on API28/34, repeated measure/layout/draw and replacement. It checks distinct body/citation rectangles, complete final character, unclipped body and contained citation. The test fails against the previous published layout and passes against the fixed layout. Existing full display/font/theme tests remain.

The owner explicitly authorized installing the matching development APK and publishing this hotfix. Runtime confirmation on the original physical TV is still required. Previous source/tag/APK and preferences are preserved. Package/debug signature remain compatible; private dev1 versionCode36 / public RC1 versionCode37. Prefer same-key forward updates; no app-data clearing or forced downgrade.
