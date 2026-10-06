# Proposal: All-skins wheel option + wheel reorder

## Why

Since the categorized library replaced the carousel, the wheel lost its flat-list fallback: a player whose categories hold no wheel slots gets an empty wheel, and the wheel order can only be changed indirectly through the library screen. The registry-view reorder just shipped gives the exact interaction vocabulary (press-drag-slop, relative pivot commits) to bring reordering onto the wheel itself and to reintroduce an all-skins wheel as a configurable behavior rather than a silent fallback.

## What Changes

- New config option **All skins wheel** with three values: `Never`, `Fallback` (default), `Always`; plus **max all-skins wheels when categories exist** (default 2, bounded 1..5). Both persisted in the existing JSON config and editable in the YACL screen's skin-wheel group.
- Wheel composition gains an **all-skins group** built from registry order in wheels of 10: `Always` prepends it before the category wheels, capped to the max-wheels setting; `Fallback` substitutes it, uncapped, when no category contributes any wheel; `Never` keeps today's behavior. The empty state remains only when nothing contributes at all.
- Pagination dots support the all-skins group: neutral color and an "All skins" tooltip.
- **Wheel reorder mode**: pressing a real sector and dragging beyond the slop threshold pulls it radially outward (a cut slice of cake); the other sectors reflow dynamically; the mouse angle around the center selects the nearest filled slot on the centered wheel; a click commits. A press released without real movement still applies the skin, unchanged.
- The reorder mode persists while scrolling across wheels of the **same group** (one category, or the all-skins group) and cancels — the sector easing back to its origin slot — when the view slides into a different group, when G is released, or on right-click.
- Committing a wheel reorder mutates the same order as the matching library view: the category's card list for a category group, the registry order for the all-skins group. **No new persistence.**

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `skin-wheel`: the composition requirement is amended (all-skins wheel modes and cap, pagination identity for the new group) and new requirements cover the reorder mode (drag-out mechanics and commit, persistence across same-group scrolling, cancellation rules).
- `config-screen`: a new requirement for the two all-skins wheel options (mode cycle + max wheels, defaults and persistence).

## Impact

- `SimpleSkinSwapperConfig` (new enum + two fields), `YaclConfigScreen` (enum cycle + int slider in the existing skin-wheel group), `en_us.json` / `fr_fr.json`.
- `SkinWheelScreen`: composition (wheel groups), pagination, input state machine, radial rendering of the dragged sector.
- A new radial drag engine alongside `GridEngine` / `CardDragEngine`, reusing their pattern (dragged element removed from the group's flat list, insertion index from pointer, neighbors eased).
- Store primitives reused as-is: category card list (`SkinCardStore`) and `SkinRegistry.moveBefore` / `moveAfter`.
- Spec delta on the skin-wheel no-fallback rule (`openspec/specs/skin-wheel/spec.md:49`).
- `rememberWheelPosition`: changing the mode reshapes the wheel sequence; the persisted index is already clamped on open, so it may land on a different wheel — accepted trade-off.
