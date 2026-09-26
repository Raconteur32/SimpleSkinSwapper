# Layering cleanup — resolve the atlas back-edges

## Why

`docs/atlas.md` open questions #3 and #4: `SkinType`, a domain value type, lives in `gui` and drags four of the five `changeskin → gui` back-edges; the production wiring (`LibraryServices`, `SkinRecords`, `SkinLifecycle` in `gui/library/LibraryFacades.kt`) is core consumed by `changeskin` but lives under the GUI package. The Konsist layering map carries four GUI exceptions that exist by history, not by design.

## What Changes

- Move `SkinType` from `gui` to the root package — pure package rename, 9 importing files.
- Move `gui/library/LibraryFacades.kt` (`LibraryServices`, `SkinRecords`, `SkinLifecycle`) to `library` — pre-verified 2026-09-26: its imports are 100 % `data`/`library`; the single same-package reference (`SkinCategoryPalette.dyeNameForColor`) is a pure hex→dye-name table (MC-common `DyeColor`, no rendering) which moves to `library` too, with `SkinCategoryPalette` delegating.
- Tighten the Konsist layering map: the `changeskin` allowlist drops `gui.SkinType`, `gui.SkinUtils`? — no: drops `gui.SkinType`, `gui.library.SkinLifecycle`, `gui.library.SkinRecords`; **one GUI exception remains by design**: `gui.SkinUtils` (StartupSkinSync) — GPU texture upload (`Minecraft`, `DynamicTexture`, `NativeImage`), its domain/rendering split is a separate future change, recorded in the atlas.
- Refresh `docs/atlas.md` (package graph edges + open questions #3/#4 resolved, SkinUtils noted).

Non-goal: splitting `SkinUtils` (domain vs GPU rendering) — its own change if wanted.

## Capabilities

### New Capabilities

(none — pure refactor, no runtime behavior change)

### Modified Capabilities

(none — `skip_specs: true`)

## Impact

- ~12 files touched, all import/package lines except the small `dyeNameForColor` move; zero behavior change.
- Gates validate everything: `build` ×4 (compile), Konsist layering rules ×4 (the tightened map), atlas consistency by review.
- Cost: ~2 h. Gain: the `gui → library → data` spine becomes true without GUI exceptions (minus the one documented `SkinUtils` case); future Konsist violations in `changeskin` become meaningful instead of allowlisted.
