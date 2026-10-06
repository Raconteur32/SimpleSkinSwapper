## Why

In a category view, the delete popup collapses to a single "delete everywhere" when the card is the skin's last category reference. That removes the user's ability to simply un-categorize a skin from its last category: "remove from category" (skin stays in the library, becomes uncategorized) and "delete everywhere" (skin + texture + every card gone) are not the same thing, even at the last location.

## What Changes

- The category-view delete popup always offers both actions: "remove from category" and "delete everywhere" — including when the card is the skin's last location. Removing the last card keeps the skin in the library (it becomes uncategorized).
- `DeleteDecision.actions()` drops the `otherCategories > 0` condition; the context line ("Not in any other category") and both lang texts stay as-is.
- The unit-test contract flips: the last-location case now expects both actions.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `skin-library`: The add/delete flows requirement no longer collapses the category delete popup at the skin's last location — both levels are always offered from a category view, and removal there leaves the skin in the library (uncategorized).

## Impact

- `library/DeleteDecision.kt`: `actions()` condition + doc comment.
- `src/test/.../DeleteDecisionTest.kt`: last-location test flips to both actions.
- Specs: `openspec/specs/skin-library/spec.md` requirement "Skin add and delete flows are available on the screen" (REMOVE+ADD — a scenario title becomes contradictory, which a MODIFIED block cannot express).
- No persistence, GUI routing, or lang changes: the popup already renders two buttons and `removeCardOf` already behaves correctly regardless of other-category count.
