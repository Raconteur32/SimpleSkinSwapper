# Card actions and unified delete popup

## Why

Destructive actions on library cards are inconsistent and partially unprotected: the detail panel exposes two delete-flavored buttons with different semantics (an instant "remove from this category" with no confirmation at all, and a "delete" guarded only by a two-click arm that then deletes everywhere), and the view-dependent delete logic (DeleteDecision) only feeds a passive info line instead of the actual choice. There is also no way to act on a card without opening the full detail panel. The category deletion confirmation uses an older modal that should become the shared visual for every destructive confirmation.

## What Changes

- Card button row gains a kebab (three dots) button at the bottom-right, next to the apply button; right-clicking a card opens the same menu.
- The kebab menu offers per-card actions: "Modifier" (opens the detail overlay — same as a left-click) and "Supprimer" (opens the delete confirmation popup).
- A shared `ConfirmPopup` widget is extracted (title, message lines, one to three buttons, dimmed backdrop, centered) from the category-delete modal; the category deletion flow is rebased onto it so the future re-texture happens in one place. Visuals are unchanged for now.
- The detail panel keeps a single "Supprimer" button: it opens the shared confirmation popup over the (dimmed) panel instead of arming in two clicks. The "remove this card" vs "delete everywhere" choice moves inside the popup, presented with the consequence text and counters computed by DeleteDecision.
- DeleteDecision now drives the popup's button set per view: category view with the skin elsewhere offers both levels (with the other-category count), category view at the last location collapses to a single definitive delete, All skins deletes everywhere with the occurrence count, Uncategorized deletes outright.
- Confirming the popup executes the deletion without committing pending panel edits (renames, model switch); canceling closes the popup and returns to the panel with its state intact.
- The two-click arm pattern is retired.

Out of scope, deferred to a later change: multi-card selection and bulk delete (DeleteDecision generalization to N skins is designed but not implemented here), card duplication, clipboard actions, popup re-texture.

## Capabilities

### New Capabilities

### Modified Capabilities

- `skin-library`: per-card context menu (kebab + right-click) with Modifier/Supprimer; deletion always goes through the shared confirmation popup with view-dependent choices; the detail panel exposes a single delete entry and retires the two-click arm and the instant remove-card button.

## Impact

- **Code**: new `ConfirmPopup` widget and per-card kebab menu (gui/library); `SkinLibraryCard` (kebab button, right-click routing); `SkinDetailPanel` (single delete button, popup sequencing); `SkinLibraryScreen` (popup host, category modal rebase); `CategoryBand` (delete modal rebased onto ConfirmPopup); `DeleteDecision` (button-branch mapping, stays pure); lang files (menu + popup texts, en/fr).
- **Specs**: `specs/skin-library/spec.md` — MODIFIED "Skin add and delete flows are available on the screen"; ADDED per-card context menu requirement.
- **Tests**: DeleteDecision button-branch mapping (pure, JUnit); existing delete tests keep passing.
