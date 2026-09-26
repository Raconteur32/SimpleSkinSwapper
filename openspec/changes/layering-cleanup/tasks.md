# Tasks: layering-cleanup

## 1. SkinType to the root package

- [ ] 1.1 Move `gui/SkinType.kt` to the root package; update the 9 importing files (4 `changeskin`, 5 `gui.library`). Verify: `./gradlew build test` green ×4 trees, zero diff beyond package/import lines.

## 2. Library facades to the core

- [ ] 2.1 Move `gui/library/LibraryFacades.kt` to `library` (design D2); update imports in `StartupSkinSync`, `SkinWheelScreen`, `SkinDetailPanel`, `SkinCategories`, `SkinLibraryScreen`. Verify: `./gradlew build test` green ×4.
- [ ] 2.2 Move `dyeNameForColor` to `library` per D3 (resolver object next to the migration code; `SkinCategoryPalette` delegates). Verify: build ×4 green; the migration legacy-color path still resolves (code review of the lambda wiring).

## 3. Enforcement & docs

- [ ] 3.1 Tighten the Konsist layering map (design D4): allowlist for `changeskin` = `config.`, `data.`, `library.`, `networking.`, `gui.SkinUtils` — nothing else. Verify: `./gradlew test` ×4 green; a temporarily reintroduced `gui.SkinType` import in `changeskin` fails the suite, then revert.
- [ ] 3.2 Refresh `docs/atlas.md`: package graph edges (back-edges changeskin→gui drop to the single `SkinUtils` case), open questions #3/#4 closed, `SkinUtils` split noted as a candidate. Verify: "as of" header updated to the final commit.
- [ ] 3.3 Full pass and commit(s). Verify: `./gradlew build detektAll test` green; commits follow the migration plan (one per step).
