# Tasks — card-actions-delete-popup

## 1. ConfirmPopup component

- [x] 1.1 Extract `ConfirmPopup` (title, message lines, 1–3 buttons, dimmed backdrop, centered box) hosting through `SkinLibraryScreen`, drawing reused from `CategoryBand`'s modal; ESC/backdrop click = cancel; renders in the raise-overlays pass above panels
- [x] 1.2 Rebase `CategoryBand`'s delete confirmation onto `ConfirmPopup` (same texts, same placement; the band's own modal widgets retire)

## 2. DeleteDecision button mapping

- [x] 2.1 Add the button-branch mapping to `DeleteDecision` (CATEGORY both levels / collapsed at last location, ALL_SKINS, UNCATEGORIZED) as pure i18n-free data; unit tests for every branch including the collapse

## 3. Card context menu

- [x] 3.1 Add the kebab control to the card's button row (apply shrinks; verify fit at GUI scales; fallback to header-right if the row cannot fit)
- [x] 3.2 Implement the anchored menu (Modifier / Supprimer) with outside-click dismiss and single-open rule; route card right-click to the same menu
- [x] 3.3 Wire "Modifier" to the detail overlay open path; wire "Supprimer" to the delete popup (no panel open path)

## 4. Detail panel single delete

- [x] 4.1 Replace the two-click arm and the remove-card button with a single "Supprimer" control opening the popup over the dimmed panel; confirm executes without committing pending edits (instant close path); cancel restores the panel untouched
- [x] 4.2 Render the popup message and buttons from `DeleteDecision` (message lines reuse the existing context texts)

## 5. Polish and validation

- [x] 5.1 Lang keys en/fr for menu items, popup title/messages/buttons; drop dead keys (two-click arm, remove-card)
- [x] 5.2 Full gate: `build` (4 trees) + `detektAll` + tests green; in-game pass: kebab, right-click, popup variants per view, cancel paths, category delete unchanged, GUI scales
