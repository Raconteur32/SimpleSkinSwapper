## Why

The library grid is only reorderable inside category views. All skins and Uncategorized show skins in the fixed registry order (skins.json insertion order) with no way to arrange them, so users cannot control how their skins read in the two most-used views — even though the drag & drop machinery already exists.

## What Changes

- Drag & drop reordering is enabled in the All skins and Uncategorized views (previously explicitly non-reorderable: cards snapped back, no insertion gap).
- Reordering in derived views mutates the registry order (the persisted order of `skins.json`), which becomes the single ordering source of truth for both views; Uncategorized keeps showing the registry order filtered to unreferenced skins (option A: a window on the global order, not an independent per-view order).
- A new relative move primitive is added to the registry (`moveBefore` / `moveAfter` on a pivot skin) because in derived views the displayed list is a subset of the source list: an insertion index is only meaningful relative to the displayed cards. The insertion index computed by the existing `CardDragEngine` (index in displayed-list-without-dragged space) is translated to a pivot: the displayed card currently at that index.
- Drop past the last card of a derived view (and empty-view fallback) uses relative semantics: the card lands right after the previous displayed card, not at the absolute registry end.
- Category views keep their existing index-based reorder (unchanged).

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `skin-library`: The card-grab reorder requirement (which explicitly exempts All skins and Uncategorized) is replaced by a view-agnostic requirement — dragging reorders the registry order in derived views, with an insertion gap. A new requirement pins the ordering semantics of the derived views (registry order is the ordering source of truth, reorder via relative pivot moves, Uncategorized inherits the filtered registry order).

## Impact

- `SkinRegistry` (`library/SkinRegistry.kt`): new relative move API (`moveBefore`/`moveAfter`) + save; the JSON format itself does not change (array order is already persisted as-is).
- `SkinLibraryScreen.finishCardReorder` (`gui/library/SkinLibraryScreen.kt` ~771-806): new branch for derived views translating insertion index → pivot → registry move.
- `GridEngine.updateCardPositions` (`gui/library/GridEngine.kt` ~140-163): insertion gap no longer gated on `selectedCategory != null`; must use the derived view's list size.
- Specs: `openspec/specs/skin-library/spec.md` — requirement "The whole card body is the reorder grab" (lines 437-459, scenario 456-459 currently forbids derived-view reorder) plus a new derived-view ordering requirement.
- Persistence: `skins/skins.json` array order now user-controlled; `skins/categories.json` untouched.
