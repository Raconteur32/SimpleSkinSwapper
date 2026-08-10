# Proposal: fix-carousel-gui-scale-layout

## Why

The carousel screen layout breaks at large GUI scales (small logical window sizes): the top row of buttons/inputs overflows the right edge (~110px at 480 logical px in French), cards overlap the top row as soon as logical height drops below 282px, and the margins around the top row shrink to almost nothing. The layout must degrade gracefully instead of overflowing.

## What Changes

- Make the two top-row text fields (search, account name) flexible: they share the horizontal space left by the fixed-size buttons, proportionally to their preferred widths (200/120), with a 40px floor. Preferred widths are preserved whenever there is room, so normal GUI scales are visually unchanged.
- Cap card height so cards never grow into the top row: cards are bottom-anchored, so `getCardHeight()` is clamped to keep `cardTop` below the top bar.
- Center the top row between the dark band top and the cards with a guaranteed minimum margin of 4px on both sides (consistent with the bottom buttons sitting 4px from the screen edge and the cards' `BUTTON_MARGIN = 4`).
- Add guard constants: `MIN_FIELD_WIDTH = 40`, `MIN_ROW_MARGIN = 4`, `MIN_CARD_HEIGHT = 40`.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `skin-carousel`: adds a layout requirement — the screen must adapt to any GUI scale without horizontal overflow or vertical overlap, with minimum margins around the top row.

## Impact

- **Code**: `src/main/kotlin/fr/raconteur/simpleskinswapper/gui/SkinCarouselScreen.kt` only (common code, all three MC versions 1.21.11 / 26.1.2 / 26.2).
- **Specs**: `openspec/specs/skin-carousel/spec.md` gains one requirement.
- **Compatibility**: no API or behavior change at normal GUI scales; only small logical sizes change layout.
- **Note**: the flexible-fields and card-height-clamp parts were already implemented in the working tree before this change was proposed (hotfix during investigation). This change formalizes them and adds the minimum-margin refinement. Tasks 1.1 and 1.2 are therefore already complete.
