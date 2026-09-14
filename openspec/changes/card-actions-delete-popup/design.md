# Design — card-actions-delete-popup

## Context

The library screen has three destructive paths with three different protections: the detail panel's "remove from this category" button is instant and unconfirmed, its "delete" button arms in two clicks then deletes everywhere, and category deletion opens an older centered modal with a dimmed backdrop (in `CategoryBand`). The view-dependent delete logic already lives in a pure function (`DeleteDecision.of(source, categoriesOf)`) but only feeds a passive info line under the model switch. This change unifies all of them behind one shared confirmation popup and adds a per-card action menu.

Constraints from the codebase: chisel convention (shared files must compile on 4 stonecutter trees — no multi-catch `|`, guarded newer API calls), edge-safe widgets, scissor guards, existing dimmed-modal rendering in `CategoryBand`, DeleteDecision already unit-tested, detekt gate before commit.

## Goals / Non-Goals

- Goals: one visual and behavioral home for destructive confirmations; per-card actions reachable without the panel; no destructive action without an explicit confirm; pure logic stays pure and tested.
- Non-Goals: multi-card selection and bulk delete (next change — DeleteDecision generalization to N skins is only designed here); card duplication; clipboard; popup re-texture (visuals deliberately unchanged).

## Decisions

### D1. ConfirmPopup is a screen-hosted widget, not a panel

One `ConfirmPopup` widget owned by `SkinLibraryScreen`, mirroring how the detail/add overlays are hosted. Constructor takes a title, a list of message lines, and one to three buttons (label + action); the backdrop dims the whole screen; clicking the backdrop, the cancel button, or ESC resolves as cancel. Centered box reuses `CategoryBand`'s current modal drawing (box + buttons) so the visuals are byte-identical today; the drawing lives in one place for the later re-texture. The popup sits above everything (raise-overlays pass) and suppresses card/menu input while open.

### D2. DeleteDecision grows a button branch — still pure

`DeleteDecision` keeps its counts and gains a mapping to the popup's button set: CATEGORY → `[RemoveHere, DeleteEverywhere]` collapsing to `[DeleteEverywhere]` when `otherCategories == 0`; ALL_SKINS → `[DeleteEverywhere]`; UNCATEGORIZED → `[DeleteEverywhere]` (labeled final). The message lines stay derived from the same counts. The popup labels are i18n keys resolved at display time; the decision itself stays free of Minecraft types so the JUnit tests keep running headless. Bulk generalization (`of(source, List<SkinId>)`) is intentionally NOT implemented — only the shape is kept in mind so D2's mapping does not hardcode single-skin assumptions.

### D3. The whole card is the grab; no menu, no card rotation

The card carries no per-card chrome beyond the apply button: the grab handle, the kebab menu and the right-click context menu are all retired (their delete entry was unreachable anyway once the popup became the single destructive path, and the detail overlay already opens with a plain click). The whole card body is the reorder grab: a press records the candidate; once the press moves beyond a small slop (6 Manhattan px) the card's drag converts to a reorder — started by the screen AFTER its `mouseDragged` children iteration ends (the same deferred pattern as the existing `removeWidget`, avoiding mid-iteration child removal); a release without real movement opens the detail overlay. Preview rotation is removed from the card and stays only in the detail overlay; the hover walk animation and its eased settle remain.

### D4. Deletion sequencing: popup over the panel

Delete from the panel opens the popup over the dimmed panel; the panel stays mounted underneath. Confirm → the deletion executes, the panel closes instantly, and pending edits (name fields, pending model switch) are NOT committed (the panel's close path skips commit when the close is delete-driven — the existing `onCloseRequested(instant = true)` route already fits). Cancel → popup closes, panel state untouched (fields keep values, armed state gone by construction). Delete from the card menu (no panel open) runs the same popup directly on the screen.

### D5. Category deletion rebases onto ConfirmPopup

`CategoryBand`'s modal ("delete this category?" + confirm/cancel) is rebuilt as a `ConfirmPopup` instance; the band only triggers it through the screen. Same texts, same placement — no behavior change. This is what makes D1's single drawing location real rather than aspirational.

### D6. Lang keys

New keys for the menu (modifier, supprimer), the popup title/messages/buttons per view (reuse existing `delete_context_*` texts for the message lines where they fit), and the popup-level strings. Both `en_us` and `fr_fr`.

## Risks / Trade-offs

- [Click-vs-drag discrimination on the whole card] → a small movement slop (6 Manhattan px) separates the two; a tremor-release still opens the detail overlay, and any real movement becomes a reorder. Verify the slop feels right at GUI scales Auto/1/2.
- [Retiring the instant remove-card button removes a shortcut] → the same effect stays reachable in two clicks (detail overlay → Supprimer → Retirer d'ici); accepted for safety consistency.
- [Popup over panel z-order] → the popup must render above the panel's raise-overlays pass; verify with the existing raise pass order rather than adding a new layer.
- [Retiring the instant remove-card button removes a shortcut] → the same effect stays reachable in two clicks (Supprimer → Retirer d'ici); accepted for safety consistency.

## Migration Plan

No data changes. Purely presentation-layer plus DeleteDecision extension; stores and lifecycle untouched.

## Open Questions

- None blocking.
