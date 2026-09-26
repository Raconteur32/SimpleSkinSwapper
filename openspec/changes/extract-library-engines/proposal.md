# Extract the library screen's overlay and grid engines

## Why

`SkinLibraryScreen` is the codebase's top hotspot (atlas §5 #1): 1 306 lines today, 4 008 churn lines historically (14,7 % of all churn ever), 11 of 38 version guards — it still owns tab wiring, grid layout, card reorder, overlay orchestration, popups, category business flows and SDL file dialogs. The `TabStripController`/`CardDragEngine` extractions already proved the pattern; two coherent responsibilities remain extractable. jscpd baseline (2026-09-26): 8 clones / 65 lines, none ≥15 lines — the case rests on concentration and churn, so the change carries a zero-regression duplication contract.

## What Changes

- Extract an **`OverlayManager`** (gui/library): the full-screen overlay lifecycle — `detail`/`addPanel`/`confirmPopup` state, `openDetail`/`openAddPanel`, `init()` re-attach (`reattachOverlays`), `raiseOverlays`, `pruned()` purge, `rebindDetail`, overlay click interception (`handleOverlayClick`). The screens' panels keep their `parent: SkinLibraryScreen` back-reference; the manager is the screen's collaborator, not a replacement.
- Extract a **`GridEngine`** (gui/library): grid geometry and card placement — `cols`/`cellW`/`cellH`/`gridOffsetX`/`gridTop`/`gridBottom`/`scrollY`/`maxScroll`, `recomputeLayout`, `updateMaxScroll`, `gridLeft`/`gridRight`/`contentStartY`/`contentTop`, `updateCardPositions`/`easeWidgetToSlot`/`updateAddCardPosition`, reorder bookkeeping (`reorderDraggingCard`, `pendingReorderStart`, `beginCardReorder`/`finishCardReorder`, `requestCardReorder`).
- What deliberately stays on the screen: category business flows (delete/switch/transfer popups' bodies), render dispatch (`extractRenderState`), input routing entry points, SDL file dialog — they orchestrate the two engines.
- Refresh `docs/atlas.md` (§3 + §5 #1: churn is history, structure is the outcome).

## Capabilities

### New Capabilities

(none — pure refactor, no runtime behavior change)

### Modified Capabilities

(none — `skip_specs: true`)

## Impact

- `SkinLibraryScreen.kt` shrinks by roughly a third (target ≤ ~900 lines); two new engine classes (~150-250 lines each).
- Verification per step: `./gradlew build detektAll test` ×4, jscpd post-check ≤ baseline (65 lines), and — required by the review checklist for GUI dispatch changes — **in-game testing by the maintainer** before archive (open/resize/close overlays, drag reorder, scroll, popups over overlays, on 26.3).
- Risk concentrated in click/key forwarding order and the resize re-attach path; mitigated by the ×4 gates and the engine unit boundaries.
- Cost: ½–1 day.
