## Tasks

- [x] 1. Split `SkinLibraryScreen.mouseClicked` into `handleOverlayClick` + `handleChromeClick`; retire the `@Suppress("CyclomaticComplexMethod")`; build + detekt; commit
- [x] 2. Split `SkinLibraryScreen.updateCardPositions` into `easeWidgetToSlot` + `updateAddCardPosition`; retire the suppression; build + detekt; commit (via a small `GridSlottedWidget` interface — Kotlin forbids shared helper state on unrelated classes)
- [x] 3. Split `SkinLibraryCard.extractWidgetRenderState` into clip/chrome/header/preview helpers (keep `.text(client.font,` shapes); retire the suppression; build + detekt; commit
- [x] 4. Split `SkinWheelScreen.extractRenderState` into `drawEmptyState` + `drawPagination`; retire the suppression; build + detekt; commit
- [x] 5. Verify: no `@Suppress("CyclomaticComplexMethod")` remains (`rg` count = 0); full 4-version build; quick jscpd spot-check; flag the in-game pass (chrome clicks, card reorder drag, preview rotate, wheel pagination)
      jscpd: 6 clones / 0.66% (up from 3 / 0.37% — all 7-11-line prologs around stonecutter guards and trivial helpers, none structural; baseline 25 / 4.74%).
      IN-GAME PASS REQUIRED: chrome clicks (band/tabs/confirm), card reorder drag, drag-rotate preview, wheel pagination dots.
