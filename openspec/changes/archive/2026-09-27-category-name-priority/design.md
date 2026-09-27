# Design — category-name-priority

## Context

`SkinEntry` carries a single `displayNameOverride` that serves two masters: `SkinEntry.fromRecord` seeds it with the registry's global name, and `SkinLibraryScreen.reloadView` overwrites it with the card's category name in category views. `SkinDetailPanel` seeds its global-name field from that same property (SkinDetailPanel.kt:118) and commits the field's text to the registry on close — so a panel opened in a category view commits the category name as the global name. See proposal.md for the confirmed bug; the behavior contract is the delta spec (view-resolved names, independent storage, no-op open/close).

The wheel screen builds its entries with `SkinEntry.fromRecord` only (SkinWheelScreen.kt:31) and reads `displayName` — it never sees category names today and must stay that way.

## Goals / Non-Goals

**Goals**
- One explicit name model: what a card shows is a resolution over two independent stored names.
- Detail panel seeds and previews each field from its own name only.
- No-op open/close of the detail overlay commits nothing.

**Non-Goals**
- No store/JSON format change; no migration.
- No change to the add-panel's staging name field (it stages a new skin, unrelated to the two stored names).
- No rework of the derived-view ("All skins"/"Uncategorized") ordering or card model.

## Decisions

### D1 — Two named slots on the entry, one resolution chain

Replace `displayNameOverride` with `globalName: String?` and `categoryName: String?` on `SkinEntry`; `displayName` becomes `categoryName ?: globalName ?: baseName`.

Why one chain instead of a view-aware `displayNameFor(view)`: in derived views and the wheel, `categoryName` is simply never set (null), so the same formula is view-aware by construction — no view flag threaded through render code, no chance of a call site passing the wrong view.

Alternatives considered:
- *Minimal seed fix only* (panel reads the registry) — kills the bug but keeps the overloaded property; the next writer re-introduces the bug. Rejected per the structural scope.
- *Resolution at call sites* (each renderer picks card name vs record name) — scatters the priority rule; cards, panels and wheel would each re-implement it.

`reloadView` sets `globalName` from the record in every view and `categoryName` from the card in category views; `fromRecord` sets `globalName` only.

### D2 — Panel seeds each field from its own slot

`refreshFields`/`rebind` fill the display field from `entry.globalName` and the category field from `entry.categoryName`. The two preview responders each write back only their own slot (the category field's responder drops its `ifEmpty { registry name }` fallback — with separate slots there is nothing to fall back into). No registry lookup inside the panel; the entry remains its single data source.

### D3 — Commit path unchanged

`commitEntryNames(entry, display, category)` keeps its signature and semantics (rename registry + `setCardName`). It becomes safe because the field values it reads are seeded from the right slots; no commit happens unless close/apply runs, so an open/close cycle without edits still lands two identical values — the no-op guarantee of the delta spec.

## Risks / Trade-offs

- [Preview state lives on entry instances that `rebuildCards` replaces] → same lifecycle as today; `OverlayManager.rebindDetail` already re-points the panel at fresh entries after rebuilds. Covered by existing rebind flow.
- [Fallback chain changes visible text in edge cases (blank record name falls to file baseName)] → chain order preserved relative to today's derived views; the delta scenarios pin the expected text per view.
- [Rename sweep of `displayNameOverride` touches several files] → mechanical; `rg displayNameOverride` is the checklist (5 sites, all listed in the proposal impact).

## Migration Plan

Pure GUI-layer refactor, no persisted data touched. Rollback = revert the commit.

## Open Questions

None.
