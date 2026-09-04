## Context

The four suppressed functions exceed detekt's `CyclomaticComplexMethod` threshold (15, default, active since the `adopt-detekt-incrementally` ledger). They were left suppressed because a split is behavior-risky until validated in game, and the previous change's in-game pass could not cover four risky areas at once. This change does the splits one commit at a time; a single in-game pass at the end validates all four (each touches a visually distinct behavior).

Context from `extract-shared-structure` applies unchanged: detekt runs via JavaExec (`./gradlew detekt`), builds via `./gradlew build` (all 4 stonecutter versions), wire formats frozen, no runClient — the user tests in game.

## Goals / Non-Goals

**Goals:**
- Retire all 4 remaining `@Suppress("CyclomaticComplexMethod")` with real decompositions, each under threshold 15.
- Zero behavior change: expressions moved verbatim, same file, same evaluation order.
- One commit per function split (compiler + detekt green each time).

**Non-Goals:**
- No new detekt rules, no threshold changes.
- No restructuring of functions without suppressions (`finishCardReorder`, `mouseScrolled`, etc.).
- No extraction of new classes — helpers live as private functions next to their caller (the Extract Class debt for these areas is already tracked in `jscpd-findings.md` as a separate, larger refactoring).

## Decisions

### D1 — Split shapes (mechanical, by responsibility)

- **SkinLibraryScreen.mouseClicked** → `handleOverlayClick(click, doubled): Boolean?` (detail/add panels, `null` = not consumed) + `handleChromeClick(mx, my, click, doubled): Boolean` (delete-confirm overlay, tab press, band bar/swatch, empty-category add click) + the super/deferred-unregister tail. The priority order overlays → chrome → super is preserved exactly; chrome helpers keep returning early so evaluation order is observable-identical.
- **SkinLibraryScreen.updateCardPositions** → `easeWidgetToSlot(widget, slot, t, display, fresh)` (shared clip-set + ease, used by the card loop and the add-card) + `updateAddCardPosition(dragIndex, t)` (visibility + trailing "+" slide). `lastCardEaseNanos` bookkeeping stays in the caller.
- **SkinLibraryCard.extractWidgetRenderState** → `beginCardClipping(floating, onScreen): Boolean` + `drawCardChrome(...)` (background/handle/child buttons) + `drawCardHeader(...)` (number + scissor-guarded name) + `drawCardPreview(...)` (texture + `renderPlayerRotatable`) + `endCardClipping(clipped)`. The stonecutter `//? if >=26.1` override-signature guard stays at the top of the original function; helpers are plain private functions and keep the `.text(client.font,` literal shape so the ≤1.21.11 rewrite still matches.
- **SkinWheelScreen.extractRenderState** → `drawEmptyState(context, cx, cy, mouseX, mouseY, delta)` (zero-skin path including its own super call + return) + `drawPagination(context, cx, mouseX, mouseY, atRest, activeWheel)` (dots, hover, tooltip, counter). The `font`-based calls move verbatim (already compatible across versions).

### D2 — Suppression retirement is the acceptance test

Each split's commit must show `detekt` clean **without** the corresponding `@Suppress` line. If a helper still trips the rule, that is a signal the split shape is wrong — re-cut the helpers, do not re-add the suppression or raise the threshold.

### D3 — Risk containment

Click-routing order (overlays → confirm → tabs → band → empty category → super) and per-frame easing order (band refresh → drag bookkeeping → cards → add-card → timestamp) are the two invariants the splits must not perturb; they are called out per task and covered by the end-of-change in-game pass (click every chrome zone, drag a card, drag-rotate a preview, paginate a multi-wheel composition).

## Risks / Trade-offs

- [Render-order regressions from helper extraction] → verbatim moves only; no parameter reshuffling beyond what the split requires; all four versions compiled per commit.
- [Stonecutter rewrite misses in new helper bodies] → helpers keep the exact `.text(client.font,` / `font` call shapes already proven in their files; per-commit 4-version build catches any miss.
- [Empty-state/pagination state mutation split across functions] → `hoverDot`, `paginationDots`, `selectedIndex` mutations stay on the same execution paths as today; helpers mutate fields directly like the inline code did.
