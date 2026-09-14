# Design — ui-adjustment

## Context

Two sparse-layout eyesores: the tab strip shrinks to two tabs with no categories, and a skin wheel with 1–2 skins renders as a 1–2 sector pie. Both fixes are pure presentation over existing geometry; no store, lifecycle, or registry changes.

## Goals / Non-Goals

- Goals: guaranteed minimum density (5 strip slots, 5 wheel slots) with clearly inert filler; the add-category entry sits right after the last category slot; empty wheels stay hidden (already true, now spec'd).
- Non-Goals: re-texturing, changing the 10-per-wheel allocation, changing tab scroll/overflow behavior, wheel navigation changes.

## Decisions

### D1. Visible-slot math lives in the tab strip controller

`TabStripController` gains the single source of truth: `visibleCategorySlots = max(realCategories, 3)`. The strip is All skins (0), Uncategorized (1), category slots (2 .. 2+visibleCategorySlots-1), add-entry at 2+visibleCategorySlots. `tabAt` only returns indices of REAL tabs (a placeholder index is not returned at all — placeholders must not be selectable, draggable, tooltip-able, or drop targets); `addEntryY`/`addEntryAt` use the same offset; the screen's `recomputeLayout` tiles `max(realCategories + 2, 5) + 1` slots so tab height accounts for placeholders. The reorder drag treats placeholder positions as nothing (no insertion index beyond real categories).

### D2. Ghost placeholder styling

Placeholders reuse the add-category entry's recessed dressing language: the tab panel sprite without a label, dimmed (the same pre-darkened idle look as tabs, without hover brightening). No new textures; if the result reads poorly in-game, a dedicated ghost texture is a follow-up to the popup re-texture pass.

### D3. Wheel padding at render time, not data time

`SkinWheelScreen.drawWheel` computes `n = max(wheel.size, MIN_WHEEL_SLOTS)` (5) and runs the existing sector math with that n — the pie geometry (gap insetting, preview placement, hit-test angles) already generalizes. Slots `i >= wheel.size` are filler: drawn with a dimmed sector color, no preview submission, excluded from `getSelectedIndex` results (an index ≥ size resolves to -1), excluded from hover animation updates and the name tooltip. `MIN_WHEEL_SLOTS = 5`; a full allocation (10) renders unchanged. The `n == 1` single-disc special case becomes dead code and is removed (n ≥ 5 always).

### D4. Empty wheels keep their existing skip

`buildWheels` already drops empty chunks (`resolved.isNotEmpty()`); the spec now locks it as a scenario. No code change.

## Risks / Trade-offs

- [Ghost tabs may read as broken tabs] → they are inert and dimmed; validated in-game, fallback is a dashed outline variant in the re-texture pass.
- [Filler sectors narrow real sectors on sparse wheels (wider pie visual)] → accepted: that wider spread is exactly the point — a 1-skin wheel no longer looks like a bug.
- [Placeholders below scrolled-out positions] → the strip scroll already clips whole tabs only; placeholders follow the same rules as real tabs.

## Migration Plan

No data changes. Pure presentation.

## Open Questions

- None blocking.
