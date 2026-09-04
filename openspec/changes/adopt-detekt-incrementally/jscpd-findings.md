# jscpd findings — input for the future Extract Class change

Run: `npx jscpd src/main/kotlin --min-tokens 60` (2026-09-04, after the detekt adoption waves).
Overall: 25 exact clones, 307 duplicated lines (4.74% of 40 files).

## Clone clusters, by size

| Lines | Files | Nature |
|---|---|---|
| 264 | SkinAddPanel <-> SkinDetailPanel | THE hotspot: shared overlay skeleton — rect()/layout math, field wiring, click-away blur/commit, ESC/Enter handling, button-row geometry, switch rendering |
| 19 | SkinDetailPanel <-> SkinLibraryCard | preview/switch geometry overlap |
| 16 | SkinNameStore <-> SkinTypeStore | JSON map store load/save (known) — SkinCategoriesStore shares the shape |
| 16 | SkinAddPanel <-> SkinLibraryCard | card/preview geometry |
| 10 | SkinLibraryScreen <-> itself | duplicated positioning blocks (cards vs add card) |
| 7 | SkinLibraryScreen <-> SkinWheelScreen | scroll/clamp helpers |

## Refactoring leads (for the future change, in order)

1. **`AbstractSkinOverlayPanel`** base (or composition): absorb the 264-line Add/Detail clone — layout, focus/commit, close handling. Biggest single win.
2. **`JsonMapStore<T>`** generic store: kills the store trio duplication and unlocks the first unit tests.
3. **`SkinLibraryScreen` Extract Class**: TabStripController, CardDragController, CategoryBand, LibraryFileWatcher (the screen's commented sections are the table of contents). InRect-style geometry helpers already extracted during detekt adoption are the seed.
4. After 1–3: re-run jscpd, then adopt the structuring detekt rules (`LongMethod`, `LargeClass`, `LongParameterList`, `MagicNumber`) to lock the gains.

## Complexity suppressions to retire in the same change

`@Suppress("CyclomaticComplexMethod")` currently marks 7 deferred functions (grep it):
SkinLibraryScreen.mouseClicked / updateCardPositions, SkinLibraryCard.mouseClicked /
extractWidgetRenderState, SkinDetailPanel.mouseClicked, SkinWheelScreen.extractRenderState,
SkinChangeManager.sendServerCommandIfNeeded.
