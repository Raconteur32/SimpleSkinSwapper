# Tasks — ui-adjustment

## 1. Tab strip minimum slots

- [x] 1.1 `TabStripController`: visible-slot math (`max(realCategories, 3)`), placeholder-aware `tabAt`/`addEntryY`/`addEntryAt` (placeholders excluded from hit-test), reorder drag ignoring placeholder positions
- [x] 1.2 `SkinLibraryScreen.recomputeLayout`: tile `max(realCategories + 2, 5) + 1` slots; placeholder ghost drawing in the strip under-pass (recessed, no label, no hover)

## 2. Skin wheel minimum slots

- [x] 2.1 `SkinWheelScreen.drawWheel`: `n = max(size, 5)` sector math; filler sectors dimmed, no preview; remove the dead `n == 1` single-disc branch
- [x] 2.2 Hit-test, hover animations and name tooltip confined to real slots (filler resolves to no selection)

## 2b. Additional polish

- [x] 2b.1 Footer buttons vertically centered between the card zone's background edge and the bottom of the screen
- [x] 2b.2 ConfirmPopup restyled to the detail-overlay surface (the mod's card nine-slice sprite instead of flat fills) — for skin deletes and category delete alike

## 3. Validation

- [ ] 3.1 Full gate: `build` (4 trees) + `detektAll` + tests green; in-game pass: empty-library strip (5 slots + "+"), 1–2 category fills, sparse wheel (1–4 skins), full wheel unchanged, empty wheel hidden, drop on placeholder rejected
