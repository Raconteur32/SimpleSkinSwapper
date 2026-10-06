## 1. Registry primitive

- [x] 1.1 Add `moveBefore(id, pivotId)` and `moveAfter(id, pivotId)` to `SkinRegistry` (no-op when id/pivot missing or equal), with `save()` left to the caller; verify with new unit tests in the existing plain JVM registry test suite (relative move across categorized skins, missing pivot, empty edge cases)
- [x] 1.2 Add store-level wrapper (save + reload semantics consistent with other mutations) if the codebase pattern expects it, mirroring how `SkinCardStore.moveCategory` persists; verify the skins.json array order reflects the move after save/load round-trip

## 2. Grid drag in derived views

- [x] 2.1 In `GridEngine.updateCardPositions`, remove the `selectedCategory != null` early-out on `dragIndex` and feed the current view's displayed list (category cards or derived entries) so the insertion gap opens in All skins/Uncategorized; verify in-game: gap shows and cards shift in both derived views
- [x] 2.2 Confirm `CardDragEngine.insertionIndexAt` clamp uses the derived view size (dragged card excluded) and position numbers stay 1-based per displayed view; verify in-game: "+"/position badge correct at the last slot of a derived view

## 3. Drop application

- [x] 3.1 In `SkinLibraryScreen.finishCardReorder`, add the derived-view branch: translate insertion index → pivot over displayed-entries-without-dragged, call `SkinRegistry.moveBefore`/`moveAfter`, persist; verify in-game: drop between two uncategorized cards lands exactly there in Uncategorized and All skins
- [x] 3.2 Handle end-of-view drop (relative: right after last displayed card) and no-pivot fallback (missing skin / empty remainder → no-op, no crash); verify in-game: drop past last card keeps categorized skins after the dragged one, drop survives a screen reopen
- [x] 3.3 Keep category drop path untouched (inline `category.cards` mutation, `SkinCategories.save()`); verify in-game: category reorder and drop-on-category-tab still work as before

## 4. Persistence & spec checks

- [x] 4.1 Verify skins.json array order after derived-view reorder, and that order survives a game restart (manual check or existing round-trip test extended)
- [x] 4.2 Run `openspec validate add-registry-view-reorder --strict` and the project's lint/detekt gate (`code-review` skill) before commit; verify both pass
