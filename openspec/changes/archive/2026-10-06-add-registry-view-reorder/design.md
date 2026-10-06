## Context

Ordering lives in exactly two places: `category.cards` (per-category, reorderable today) and the implicit array order of `SkinRegistry` (skins.json, append-only so far). The drag chain is `SkinLibraryCard` → `GridEngine.beginPendingReorder` → `CardDragEngine.dragTo` → `SkinLibraryScreen.finishCardReorder`. The insertion gap is currently gated on `selectedCategory != null` (`GridEngine.updateCardPositions`), which is why derived views snap back. `finishCardReorder` applies category reorders by directly mutating `category.cards` inline. The insertion index contract is "index in displayed-list-without-dragged space, [0, count]" (see archived change `2026-09-26-card-drag-insertion-fix`).

The new wrinkle: in All skins/Uncategorized the displayed list is a **subset/projection** of the source list, so an insertion index is meaningless in absolute terms (see proposal — "index 2 of Uncategorized" is not "index 2 of the registry").

## Goals / Non-Goals

**Goals:**
- Same drag UX in derived views as in category views (follow, gap, persist, position numbers).
- One ordering source of truth for derived views: the registry array order.
- Reorder semantics expressed relative to displayed cards, immune to view filtering.

**Non-Goals:**
- Independent per-view orders (no separate "uncategorized order" state).
- Any change to categories.json format or category reorder behavior.
- Sorting controls (alphabetical, by date) — this is manual drag only.

## Decisions

### D1 — Order the registry, not a parallel view-order store (option A)

All skins/Uncategorized reordering mutates the `skins` array order of `SkinRegistry`. Uncategorized is always the registry order filtered to zero-reference skins, so it inherits for free. Alternatives rejected: a persisted `allOrder`/`uncategorizedOrder` id list needs append/prune/sync on every lifecycle event for no observable benefit (see proposal discussion). No JSON format change: skins.json already persists array order as-is; only the UI could never mutate it before.

### D2 — Relative pivot moves as the registry primitive

`SkinRegistry` gains `moveBefore(id, pivotId)` and `moveAfter(id, pivotId)` (registry-level, plain JVM testable, symmetrical to `SkinCardStore.moveCategory`). The screen translates the `CardDragEngine` insertion index to a pivot over the **displayed list without the dragged card** (the existing index contract):

- `insertionIndex < viewWithout.size` → pivot = `viewWithout[insertionIndex]`, call `moveBefore`.
- `insertionIndex == viewWithout.size` → pivot = `viewWithout.last()`, call `moveAfter`; empty `viewWithout` cannot occur (the dragged card is itself in the view, so a non-empty view minus the dragged card may be empty — fall back to leaving the card in place, i.e. drop is a no-op).

Alternative rejected: absolute-index `move(from, to)` on the registry — wrong in filtered views (the motivating bug) and would still need pivot translation in the screen anyway, so the primitive might as well speak the screen's language.

### D3 — End-of-view drops use relative, not absolute, semantics

"Past the last displayed card" inserts right after the last displayed card, not at the registry end. Visually identical inside the view; the difference only shows in All skins, where relative keeps the drag minimally disruptive. Consistent with "derived views are a window on the global order".

### D4 — Untangle the gap gating from `selectedCategory`

`GridEngine.updateCardPositions` currently keys the whole insertion-gap computation on `selectedCategory != null`. Replace with a notion of "current view is reorderable" (always true today — all three views become reorderable), with the reorder target list supplied by the screen: `category.cards` or the derived entries list. `insertionIndexAt` clamp must use the displayed view size (already does — it works on the list it is given; the fix is passing the right list and removing the null-category early-out for `dragIndex`).

### D5 — Reuse `finishCardReorder` with a per-view-type branch

Keep the single drop path: category view → existing inline `category.cards` mutation (unchanged); derived view → resolve displayed skin ids from `entries`, compute pivot, `SkinRegistry.moveBefore/After` + `save()`. `SkinCategories.save()` is NOT called for derived moves (different store). Drop-on-tab behavior stays as-is (category tabs copy; view tabs no-op).

## Risks / Trade-offs

- [Relative move mutates global order → a categorized skin's absolute position shifts in All skins after an Uncategorized drag] → Accepted: it is the definition of option A (one shared order). The dragged card is the only skin that jumps; others only shift by one slot.
- [Dragging a card while its registry entry is mid-lifecycle (external file deletion)] → the reload path (`reloadView`) rebuilds entries; pivot lookup by skinId must tolerate a missing pivot (no-op drop) instead of crashing.
- [Position numbers/wheel markers in derived views] → cards already render 1-based position from list position; derived views get them for free. Wheel markers are category-only, unaffected.
- [skins.json order now user-controlled] → future imports still append at the end; no migration needed (old files already read in array order).

## Migration Plan

No data migration (format unchanged, order already persisted). Rollback = revert code; worst case a user's hand-arranged registry order stays in skins.json and is simply not editable while reverted.

## Open Questions

None — semantics (option A, relative pivots, relative end-of-view) were decided during exploration.
