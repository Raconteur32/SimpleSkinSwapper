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

### D3. Kebab menu is a lightweight anchored list on the card

The kebab is a small icon control in the card's button row, right of the apply button (apply shrinks to fit; row stays 16px). The menu itself is drawn by the card (or the screen) as a short anchored list near the card's bottom-right, above other content. Card right-click (`mouseClicked` button 1) routes to the same open path. Rules: clicking the kebab or right-clicking while another menu is open moves the menu; any click outside closes it first and the click flows through; opening a menu closes nothing else (no panel interplay — the menu works with the panel closed or open). The kebab zone wins over the card's drag zones (left rotate / frame reorder).

### D4. Deletion sequencing: popup over the panel

Delete from the panel opens the popup over the dimmed panel; the panel stays mounted underneath. Confirm → the deletion executes, the panel closes instantly, and pending edits (name fields, pending model switch) are NOT committed (the panel's close path skips commit when the close is delete-driven — the existing `onCloseRequested(instant = true)` route already fits). Cancel → popup closes, panel state untouched (fields keep values, armed state gone by construction). Delete from the card menu (no panel open) runs the same popup directly on the screen.

### D5. Category deletion rebases onto ConfirmPopup

`CategoryBand`'s modal ("delete this category?" + confirm/cancel) is rebuilt as a `ConfirmPopup` instance; the band only triggers it through the screen. Same texts, same placement — no behavior change. This is what makes D1's single drawing location real rather than aspirational.

### D6. Lang keys

New keys for the menu (modifier, supprimer), the popup title/messages/buttons per view (reuse existing `delete_context_*` texts for the message lines where they fit), and the popup-level strings. Both `en_us` and `fr_fr`.

## Risks / Trade-offs

- [Kebab + apply in a 32px row is tight] → apply shrinks by the kebab width; verify at GUI scale Auto/1/2 that labels still fit or apply keeps its icon-only look; if the row cannot fit both, the kebab moves to the header's right edge instead (decision point during implementation, spec is placement-agnostic enough).
- [Right-click conflict with existing input handling] → cards currently ignore button 1; MC screens deliver it fine. Risk is low but verify rotate-drag does not start on right-press.
- [Popup over panel z-order] → the popup must render above the panel's raise-overlays pass; verify with the existing raise pass order rather than adding a new layer.
- [Retiring the instant remove-card button removes a shortcut] → the same effect stays reachable in two clicks (Supprimer → Retirer d'ici); accepted for safety consistency.

## Migration Plan

No data changes. Purely presentation-layer plus DeleteDecision extension; stores and lifecycle untouched.

## Open Questions

- None blocking. The kebab placement (button row vs header edge) resolves during implementation at D3's fallback.
