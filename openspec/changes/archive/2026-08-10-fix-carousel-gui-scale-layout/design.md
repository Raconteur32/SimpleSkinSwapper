# Design: fix-carousel-gui-scale-layout

## Context

`SkinCarouselScreen.init()` laid out the top row with fixed widths (search 200, account 120, buttons at natural text width + 20) and derived card height as a fixed `height / 1.5`. Both assumed a large logical window. See proposal.md - Why for the failure numbers.

## Goals / Non-Goals

**Goals:**

- Layout stays correct down to the smallest logical sizes reachable via GUI scale settings.
- Preferred dimensions win whenever they fit: zero visual change at common GUI scales.
- All layout math stays in `SkinCarouselScreen` (common code, no per-version divergence).

**Non-Goals:**

- No wrapping of the top row onto two lines, no scrolling row, no hiding of widgets at tiny sizes.
- No changes to `SkinCard` internals (its content margins are already uniform).
- No minimum-window enforcement: below ~150px logical width the floor constants take over and the row may still be cramped; that is accepted.

## Decisions

- **Flexible text fields, fixed buttons.** The two text fields absorb all horizontal pressure because their preferred widths (200/120) are arbitrary, while button widths are content-driven (localized labels). Alternative considered: shrinking buttons too — rejected, truncated button labels are worse than narrow text fields.
- **Proportional split 200:120 with a 40px floor.** `flexBudget = width - 5*gap - addFileWidth - addAccountWidth`; search gets `min(200, flexBudget * 200/320)`, account gets `min(120, remainder)`. Simple, deterministic, identical to the old layout whenever `flexBudget >= 320`.
- **Clamp card height, keep bottom anchoring.** Cards stay anchored to the scrollbar area; `getCardHeight()` returns `min(natural, sbTrackY() - CARD_BOTTOM_GAP - topBarBottom)` where `topBarBottom = lineHeight*3 + SEARCH_HEIGHT + 2*MIN_ROW_MARGIN`. This preserves the existing large-screen look and only bites below ~282px logical height. Alternative considered: anchoring cards to the top instead — rejected, it would move cards away from the scrollbar on large screens.
- **Center the top row with a 4px minimum margin.** The existing centering formula `(bandTop + cardTop)/2 - SEARCH_HEIGHT/2` is kept; the clamp above guarantees `cardTop - bandTop >= SEARCH_HEIGHT + 2*MIN_ROW_MARGIN`, so the centered row always has at least `MIN_ROW_MARGIN = 4px` on both sides. 4px matches the bottom buttons' distance to the screen edge and `SkinCard.BUTTON_MARGIN`.
- **Guard floors.** `MIN_CARD_HEIGHT = 40` prevents degenerate/negative card heights on absurdly small windows; `MIN_FIELD_WIDTH = 40` keeps fields typeable.

## Risks / Trade-offs

- [At extreme widths (< ~150px logical), the 40px field floors can still overflow the row by a few px] → Accepted: unreachable via normal GUI scale settings on any real display.
- [Long translations widen buttons and shrink the flex budget] → Mitigated by the proportional split; French (longest current labels) verified to fit at 480px.
- [Clamped cards are shorter, so `getCardWidth()`'s aspect floor shrinks too and more cards fit per screen] → Harmless; culling and scrolling already handle any card count.
