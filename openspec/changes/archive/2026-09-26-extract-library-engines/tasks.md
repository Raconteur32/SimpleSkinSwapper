# Tasks: extract-library-engines

## 1. OverlayManager

- [x] 1.1 Extract the overlay lifecycle into `gui/library/OverlayManager` (design D1): `detail`/`addPanel`/`confirmPopup` state, `openDetail`/`openAddPanel`, `reattachOverlays`, `raiseOverlays`, `pruned`, `rebindDetail`, `handleOverlayClick` (same tri-state contract), `openConfirmPopup`/`closeConfirmPopup`. Panels keep `parent: SkinLibraryScreen`. Verify: `./gradlew build detektAll test` ×4 green; zero diff beyond the move (call-sites updated, logic untouched).
- [x] 2. Commit step 1. Verify: gate green, duplication baseline not raised (jscpd spot-check: 8 clones / 65 lines, unchanged).

## 2. GridEngine

- [x] 3. Extract grid geometry/placement into `gui/library/GridEngine` (design D2): layout state (`cols`/`cellW`/`cellH`/`gridOffsetX`/`gridTop`/`gridBottom`/`scrollY`/`maxScroll`), `recomputeLayout`, `updateMaxScroll`, `gridLeft`/`gridRight`/`contentStartY`/`contentTop`, `updateCardPositions`/`easeWidgetToSlot`/`updateAddCardPosition`, reorder bookkeeping. Screen keeps `rebuildCards`/`reloadView` and delegates placement. Verify: `./gradlew build detektAll test` ×4 green.
- [x] 4. Commit step 2. Verify: gate green.

## 3. Closing

- [x] 5. jscpd post-check ≤ baseline (65 duplicated lines, no new clone pair ≥15 lines); refresh `docs/atlas.md` (§3 structures, §5 #1 outcome + hotspot re-run). Verify: atlas "as of" updated, hotspot table rerun.
- [x] 6. **In-game validation by the maintainer on 26.3** (required before archive): open/close/rebind overlays, window resize with overlay open, popup over overlay, drag reorder across pages, scroll + culling, add-skin flow. Verify: maintainer confirmation recorded in this file.
- [x] 7. Final commit and archive readiness. Verify: `./gradlew build detektAll test` green, commits one per step.
