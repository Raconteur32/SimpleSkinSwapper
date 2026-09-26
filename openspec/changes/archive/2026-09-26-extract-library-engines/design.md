# Design: extract-library-engines

## Context

Member map of `SkinLibraryScreen` (2026-09-26 read): overlay state and lifecycle (lines ~116-127, 172-260, 390-400, 513-613, 989-1003), grid layout/placement state (~83-143, 401-467, 868-973), shared with existing engines `TabStripController` and `CardDragEngine`. jscpd baseline: 8 clones / 65 lines, none ≥15 — nothing to *deduplicate*; this is a responsibility split. See proposal.md — Why.

## Goals / Non-Goals

**Goals:**

- The screen keeps orchestration; the engines own state and mechanics.
- Each extraction lands green on all four trees and never raises the duplication baseline.

**Non-Goals:**

- No behavior change, no rendering change, no popup-flow redesign (delete/switch/transfer bodies stay on the screen).
- Not touching the wheel screen, the config screens, or the SDL dialog path.

## Decisions

### D1 — `OverlayManager` is a screen-scoped collaborator, not a widget hierarchy change

It holds and manipulates `detail`/`addPanel`/`confirmPopup` and exposes the lifecycle operations the screen already calls; panels keep `parent: SkinLibraryScreen` so their internal API calls (delete popups, model switch, entry access) are untouched. Alternative (manager as the panels' new parent) rejected: it would re-plumb every panel↔screen call and multiply the in-game test surface for zero structural gain.

### D2 — `GridEngine` owns layout math, the screen owns the widget tree

The engine holds the grid geometry state and the placement/easing/reorder bookkeeping, with the screen (or cards) reading geometry through it. Cards are still screen children; `rebuildCards`/`reloadView` stay on the screen and delegate placement. Alternative (engine owns the cards list) rejected: the card list doubles as render order and widget registration, both screen concerns (`init()` rebuilds, overlay raise order).

*Amended during step-2 scoping (2026-09-26):* `finishCardReorder` stays on the screen — its drop handling is category **business logic** (tab drop = card copy, in-category reorder + save, then `reloadView`/`rebuildCards` orchestration), not placement. The engine takes the reorder *bookkeeping* (`reorderDraggingCard`, `pendingReorderStart`, `requestCardReorder`, `beginCardReorder`) and the screen keeps the drop semantics. `recomputeLayout` splits along the same line: strip-zone math (tab height, band centering) stays on the screen, grid math moves.

### D3 — Two extraction steps, two commits, gate green at each

Step 1 `OverlayManager` (self-contained, lower risk, proves the pattern on the risky lifecycle); step 2 `GridEngine` (wider surface). A third commit refreshes the atlas and records the jscpd post-check. Any red gate stops the step where it is.

### D4 — Naming and package follow the existing engines

`OverlayManager` and `GridEngine` in `gui/library`, next to `TabStripController` and `CardDragEngine` — internal visibility where the engines already use it. No new package, no interface extraction: three concrete collaborators of one screen do not need abstraction yet (no duplication to unify — D-jscpd).

## Risks / Trade-offs

- [Click/key forwarding order changes subtly (checklist: "the screen's click/key forwarding order")] → `handleOverlayClick` keeps its exact position in the screen's `mouseClicked` chain; the engine returns the same tri-state (`Boolean?`) the screen logic already branches on. In-game pass required before archive.
- [Resize path: `init()` rebuild + `reattachOverlays` + bounds sync (`onScreenResized`)] → moved as one unit, not partially; the x4 compile and the in-game resize test cover it.
- [Guards `//? if` inside moved code split across engine/screen] → the 2026-09-26 member map shows overlay/grid guards live in the dispatch lines that stay on the screen; verified per-step by `detektAll` (which sees all four processed trees).
- [The refactor hides future churn instead of reducing it] → the atlas §5 #1 stays open until the next UI-heavy change demonstrates smaller diff surfaces; hotspot re-run is part of the closing task.

## Migration Plan

Step commits: (1) OverlayManager, (2) GridEngine, (3) atlas + jscpd post-check. Rollback = revert the step commit. In-game validation by the maintainer happens once, after step 2, before archive.

## Open Questions

(none — scope fixed by the 2026-09-26 member map; borderline members (`addCard` placement state) stay with the grid engine, its *opening* stays on the screen.)
