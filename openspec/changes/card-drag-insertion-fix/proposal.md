# Card drag insertion — fix forward-move and pin the add card

## Why

Two bugs reported in game (2026-09-26) with two cards [A, B]: dragging B before A works, but dragging A after B silently does nothing unless the cursor goes past the trailing "+" card. Both have one root cause: `insertionIndex` is computed in the *list-without-the-dragged-card* space (clamp at `count = cardCount - 1`), but `finishCardReorder` converts it with `if (to > from) to--` — a decrement only meaningful in the *with*-space. Forward moves land one position early; the only escape is the half-right `idx++` that overflows the clamp (`count + 1`), a target zone that sits under the add card. Second symptom of the same confusion: the "+" card is placed through `slotFor(cards.size, dragIndex)`, so it inherits the dragged-card shift and the insertion gap — it dances during every drag.

## What Changes

- `finishCardReorder` (SkinLibraryScreen): drop the `if (to > from) to--` decrement — the index already refers to the list without the dragged card.
- `insertionIndexAt` (CardDragEngine): the half-right refinement may no longer overflow the clamp — insertion index stays in `[0, count]`.
- `updateAddCardPosition` (GridEngine): place the "+" card with a neutral drag index (`slotFor(size, -1)`) — it no longer moves during drags.
- No change to the existing contracts: derived views (All skins, Uncategorized) snap back, tab drop = card copy, off-grid = cancel, gap visuals driven by the same `insertionIndex`.

## Capabilities

### New Capabilities

(none — bug fix, no behavior surface beyond the corrected one)

### Modified Capabilities

(none — `skip_specs: true`)

## Impact

- Three small edits, one commit; gates ×4 validate. In-game validation required (review checklist: GUI dispatch) — two-card forward move, three-plus-card multi-row moves, "+" card pinned.
- Verified reasoning on paper: [A,B] drag A → half of B's cell = insertion 1 = A in second position; drag B → unchanged behavior; drop over the "+" clamps to "after everything".
