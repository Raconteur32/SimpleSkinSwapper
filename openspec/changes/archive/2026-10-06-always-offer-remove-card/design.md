## Context

`DeleteDecision` is the pure, tested contract behind the delete popup. Its `actions()` currently requires `otherCategories > 0` to offer `REMOVE_CARD_HERE`, on the rationale that removing the last card "would be the same thing" as deleting. It is not: removal keeps the skin in the registry (uncategorized), deletion removes skin + texture + all cards. All mechanics (`removeCardOf`, two-button popup, context lines) already support both actions at the last location — only the decision collapses them.

## Goals / Non-Goals

**Goals:**
- Always offer "remove from category" from a category view, last location included.

**Non-Goals:**
- No text/lang changes (existing keys stay accurate); no changes to the derived-view popups (All skins/Uncategorized keep a single definitive delete — there is no card there to remove).

## Decisions

### D1 — Change only the `actions()` branch

`if (offerRemoveCard && otherCategories > 0)` → `if (offerRemoveCard)`. `otherCategories` keeps driving the context message ("Also in %s other categories" vs "Not in any other category"). Alternative considered: a new `DeleteAction` or a third button — rejected, no new outcome exists.

### D2 — REMOVE+ADD delta, not MODIFIED

The scenario "Delete at the last location collapses to one choice" inverts its behavior, and the strict validator requires MODIFIED blocks to preserve scenario names — a contradictory title. Same pattern as `add-registry-view-reorder`: remove the requirement with reason/migration, re-add the full block with the updated clause and a renamed scenario.

## Risks / Trade-offs

- [User searching for the old one-click full-delete at the last location] → Mitigated: "Delete" remains a single confirm away (select it, confirm); the popup is unchanged in shape, only the extra button appears.

## Migration Plan

None (pure behavior, no data).

## Open Questions

None.
