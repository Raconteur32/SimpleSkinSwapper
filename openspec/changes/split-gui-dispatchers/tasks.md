## Tasks

- [ ] 1. Split `SkinLibraryScreen.mouseClicked` into `handleOverlayClick` + `handleChromeClick`; retire the `@Suppress("CyclomaticComplexMethod")`; build + detekt; commit
- [ ] 2. Split `SkinLibraryScreen.updateCardPositions` into `easeWidgetToSlot` + `updateAddCardPosition`; retire the suppression; build + detekt; commit
- [ ] 3. Split `SkinLibraryCard.extractWidgetRenderState` into clip/chrome/header/preview helpers (keep `.text(client.font,` shapes); retire the suppression; build + detekt; commit
- [ ] 4. Split `SkinWheelScreen.extractRenderState` into `drawEmptyState` + `drawPagination`; retire the suppression; build + detekt; commit
- [ ] 5. Verify: no `@Suppress("CyclomaticComplexMethod")` remains (`rg` count = 0); full 4-version build; quick jscpd spot-check; flag the in-game pass (chrome clicks, card reorder drag, preview rotate, wheel pagination)
