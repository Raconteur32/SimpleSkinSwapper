## Context

The wheel is category-composed only (`buildWheels()` in `SkinWheelScreen` from `SkinCategories.wheelComposition()`); with no allocation it shows the empty state, and the spec forbade the flat-list fallback. The library just gained card reorder (press → slop → drag, insertion gap, commit): `GridEngine`/`CardDragEngine` drive the grid, commits go through `SkinCardStore` category lists and `SkinRegistry.moveBefore/moveAfter`. The wheel opens with a hold of G and **closes on G release** (`keyReleased`). Its positional state is a float `wheelPos` easing toward an integer `targetPos`; selection and apply only happen at rest; `lastWheelPosition` is `coerceIn`-clamped on open and `floorMod`-ed on close.

## Goals / Non-Goals

**Goals:**

- An all-skins wheel group with a three-state option and a cap, composed before category wheels in Always
- Reordering skins directly on the wheel, persisted through the same order as the matching library view
- A group model that makes cross-wheel, same-group dragging work and cross-group navigation cancel safely

**Non-Goals:**

- No new persistence format (category card lists and registry order stay the only sources of truth)
- No drag across different groups, no drop on filler slots, no Uncategorized wheel
- No change to library screen behavior (its orders merely *receive* wheel commits)

## Decisions

**D1 — Wheel groups as the composition model.** `buildWheels()` already returns wheels plus an owner list; owners become a sealed notion (a category, or the all-skins pseudo-group). A *group* is a maximal run of consecutive wheels with the same owner. The three modes collapse into one composition function: Always = all-skins (registry, truncated to cap × 10) + categories; Fallback = categories, else all-skins (whole registry); Never = categories. Alternative considered: special-case all-skins wheels with an index offset — rejected because groups generalize the existing `wheelCategories` list and give reorder its context rule for free.

**D2 — Cap binds only when category wheels exist.** The cap's purpose is keeping the all-skins group from burying the category wheels, so it applies in Always exactly when at least one category contributes a wheel; without category wheels the whole registry shows, like the fallback. Fallback itself never coexists with categories, so the cap is structurally inert there. Floor of 1: cap 0 would duplicate Fallback/Never semantics through a different door.

**D3 — Reorder is a radial transpose of the card drag pattern.** The dragged skin is removed from its group's flat list (category card window or registry window); the pointer's angle around the wheel center maps to the nearest *filled* slot of the reflowed layout; the remaining sectors ease toward their shifted angular positions (same easing family as `CARD_SLIDE_SPEED`). A dedicated engine object (angle-based API) keeps `SkinWheelScreen` — already carrying composition, rendering and input — from growing further, and stays version-free so stonecutter guards remain confined to rendering. Full reuse of `GridEngine` was rejected: cell geometry and angle geometry share the *pattern*, not the code; keep them structurally distinct to stay clear of the jscpd gate.

**D4 — Commits reuse existing store primitives.** Category group: remove+insert inside `category.cards`, `SkinCategories.save()`. All-skins group: `moveBefore`/`moveAfter` with the pivot taken from the target slot's current occupant — the same relative semantics as the library's `applyDerivedReorder`. No schema change, no migration.

**D5 — Input state machine in the screen.** `IDLE → PENDING` on a press over a filled sector of the centered wheel at rest. In `PENDING` the sector stretches elastically: the offset follows only the outward component of the drag (projection of the press-to-cursor vector on the sector's radial axis), dampened below 1:1 and capped, never rotating to follow the cursor; an inward pull leaves it at 0. Crossing the outward pull distance engages `REORDER` — the sector settles at the cut-out offset, the mouse angle retargets the slot, and scroll (and dot clicks) slide wheels with the group rule. Release under the pull distance applies the skin (today's click behavior is preserved, the stretch springs back as it applies); a click at rest commits and returns to `IDLE`; a different group, G release, right-click or escape cancels and restores the pre-drag layout. Commit is accepted only at rest, mirroring `apply()`. The gate is deliberately radial rather than Manhattan-slop: the elastic stretch itself teaches the outward gesture, so a tangent drag (zero radial projection) simply never engages — intended.

**D6 — Position memory is left alone.** Changing the mode reshapes the sequence and a persisted index may land on a different wheel; the existing clamp makes that safe, and the semantic drift is an accepted trade-off.

## Risks / Trade-offs

- [Sparse wheels give huge angular sectors, making "nearest by angle" coarse] → still unambiguous (one sector per filled slot), and filler exclusion keeps targets meaningful.
- [Reflow animates every sector's mesh while previews already render live each frame] → only sector meshes rebuild on angle change; preview culling rules unchanged; matches the cost profile the wheel already accepts.
- [Structural similarity with `GridEngine`/`CardDragEngine` could trip the jscpd gate] → angle-based API and distinct geometry; run the code-review skill before structural commits.
- [Four stonecutter targets] → engine, config and input logic are version-free; rendering changes sit behind the existing `extractRenderState` guards; build all targets.
- [Mode switch reshuffles the remembered wheel position] → clamp on open; accepted.

## Migration Plan

Pure addition. Defaults (Fallback + 2) reproduce today's behavior for anyone with categories, and repair the empty-wheel case for anyone without. Rollback is a revert; no data migration anywhere.

## Open Questions

(none)
