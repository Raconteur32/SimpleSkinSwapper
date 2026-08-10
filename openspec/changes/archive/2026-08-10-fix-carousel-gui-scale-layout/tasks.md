# Tasks: fix-carousel-gui-scale-layout

## 1. Implementation (SkinCarouselScreen.kt)

- [x] 1.1 Make search/account fields flexible: compute `flexBudget = width - 5*gap - button widths`, split proportionally to preferred widths (200/120), floor `MIN_FIELD_WIDTH = 40` (done during the investigation hotfix).
- [x] 1.2 Clamp `getCardHeight()` so `cardTop` never rises above `bandTop + SEARCH_HEIGHT + margin`, with `MIN_CARD_HEIGHT = 40` floor (done during the investigation hotfix).
- [x] 1.3 Add `MIN_ROW_MARGIN = 4` constant and use it in the card-height clamp (`topBarBottom = lineHeight*3 + SEARCH_HEIGHT + 2*MIN_ROW_MARGIN`) so the centered top row keeps at least 4px above and below.

## 2. Verification

- [x] 2.1 `./gradlew build` passes on 26.2.
- [x] 2.2 Dev client 26.2, GUI scale 4 (1080p → 480x270 logical), French: top row fits between margins, cards start below the row, row has >= 4px margin above and below.
- [x] 2.3 Cycle GUI scales 1 → 4: no horizontal overflow, no overlap at any scale; at scales 1-2 the layout is pixel-identical to before (search 200px, account 120px, natural card height).
- [x] 2.4 Repeat 2.2 in English (shorter labels).
- [x] 2.5 Sanity-check 1.21.11 and 26.1.2 clients: identical behavior (common code, regression check).
