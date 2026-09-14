# UI adjustment — minimum tabs and wheel slots

## Why

Both radial and strip layouts look broken when sparse: the tab strip collapses to two tabs (All skins, Uncategorized) when no category exists, and a skin wheel allocated to a category holding one or two skins renders as a 1–2 slot pie that reads as a rendering bug rather than a menu. Both surfaces need a guaranteed minimum density, with clearly inert filler, so the UI keeps its shape whatever the content.

## What Changes

- Tab strip: the strip always shows at least five slots — "All skins" and "Uncategorized" in the first two positions, then at least three category slots. Category slots without a real category render as ghost placeholders: visually recessed, not clickable, not selectable, not hover-highlighted, without tooltip, and never drop targets. The add-category entry ("+") sits directly after the last category slot (sixth position when the three placeholder slots are shown), moving down as real categories fill the slots.
- Skin wheel: every wheel always renders at least five slots. Wheels holding fewer than five skins show empty filler sectors: dimmed, not clickable, not selectable, no hover animation, no tooltip, no preview. A wheel is never a 1–4 slot pie anymore. Empty wheels — a category with a wheel allocation but no skins — still do not appear at all.
- Selection on the wheel continues to work by mouse angle at rest, mapping only to real (non-filler) slots; scrolling between wheels is unchanged.

Out of scope: any re-texture or visual redesign beyond the ghost/dimmed filler styling, wheel slot count beyond the existing 10-per-wheel allocation.

## Capabilities

### New Capabilities

### Modified Capabilities

- `skin-library`: the tab strip always lays out at least five slots (two views + three category slots, ghost placeholders when empty) with the add-category entry after the last slot; the skin wheel always renders at least five slots with inert filler sectors, and empty wheels stay hidden.

## Impact

- **Code**: `TabStripController` (visible-slot math, placeholder hit-test exclusion, ghost drawing) and `SkinLibraryScreen.recomputeLayout` (slot count for tab height); `SkinWheelScreen` (sector count padding, filler sector rendering, hit-test/hover confined to real slots); lang keys for nothing new (placeholders have no label).
- **Specs**: `specs/skin-library/spec.md` — MODIFIED "The library screen shows a vertical category tab strip with an All skins tab"; ADDED wheel minimum-slots requirement.
- **Tests**: none — both changes are pure presentation over already-tested store/geometry data; covered by the in-game pass.
