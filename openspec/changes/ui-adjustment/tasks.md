# Tasks — ui-adjustment

## 1. Tab strip minimum slots

- [ ] 1.1 `TabStripController`: visible-slot math (`max(realCategories, 3)`), placeholder-aware `tabAt`/`addEntryY`/`addEntryAt` (placeholders excluded from hit-test), reorder drag ignoring placeholder positions
- [ ] 1.2 `SkinLibraryScreen.recomputeLayout`: tile `max(realCategories + 2, 5) + 1` slots; placeholder ghost drawing in the strip under-pass (recessed, no label, no hover)

## 2. Skin wheel minimum slots

- [ ] 2.1 `SkinWheelScreen.drawWheel`: `n = max(size, 5)` sector math; filler sectors dimmed, no preview; remove the dead `n == 1` single-disc branch
- [ ] 2.2 Hit-test, hover animations and name tooltip confined to real slots (filler resolves to no selection)

## 3. Validation

- [ ] 3.1 Full gate: `build` (4 trees) + `detektAll` + tests green; in-game pass: empty-library strip (5 slots + "+"), 1–2 category fills, sparse wheel (1–4 skins), full wheel unchanged, empty wheel hidden, drop on placeholder rejected
