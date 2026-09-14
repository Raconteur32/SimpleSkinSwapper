# Tasks — card-actions-delete-popup

## 1. ConfirmPopup component

- [x] 1.1 Extract `ConfirmPopup` (title, message lines, 1–3 buttons, dimmed backdrop, centered box) hosting through `SkinLibraryScreen`, drawing reused from `CategoryBand`'s modal; ESC/backdrop click = cancel; renders in the raise-overlays pass above panels
- [x] 1.2 Rebase `CategoryBand`'s delete confirmation onto `ConfirmPopup` (same texts, same placement; the band's own modal widgets retire)

## 2. DeleteDecision button mapping

- [x] 2.1 Add the button-branch mapping to `DeleteDecision` (CATEGORY both levels / collapsed at last location, ALL_SKINS, UNCATEGORIZED) as pure i18n-free data; unit tests for every branch including the collapse

## 3. Card simplification

- [x] 3.1 Retire the grab handle, the kebab control and the right-click context menu (`CardMenu` deleted; apply button alone on the bottom row)
- [x] 3.2 Make the whole card body the reorder grab: press + move beyond a small slop converts to a reorder drag, started deferred after the screen's `mouseDragged` children iteration (card unregisters there)
- [x] 3.3 A press released without real movement opens the detail overlay; remove card preview rotation (rotation stays in the detail overlay; hover walk animation and eased settle remain)

## 4. Detail panel single delete

- [x] 4.1 Replace the two-click arm and the remove-card button with a single "Supprimer" control opening the popup over the dimmed panel; confirm executes without committing pending edits (instant close path); cancel restores the panel untouched
- [x] 4.2 Render the popup message and buttons from `DeleteDecision` (message lines reuse the existing context texts)

## 5. Polish and validation

- [x] 5.1 Lang keys en/fr for the popup title/messages/buttons; drop dead keys (two-click arm, remove-card, card menu entries)
- [ ] 5.2 Full gate: `build` (4 trees) + `detektAll` + tests green; in-game pass: whole-card reorder, click-to-open, popup variants per view, cancel paths, category delete unchanged, GUI scales
