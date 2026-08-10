# Tasks

## 1. Implementation

- [x] 1.1 In `SkinCarouselScreen.extractRenderState`, in the existing card-positioning loop, set `card.setVisible(cardX + cardW - CARD_CONTENT_MARGIN > 0 && cardX + CARD_CONTENT_MARGIN < this.width)` (SpruceUI accessor; content-inset bounds per design.md) next to each `overridePosition` call.
- [x] 1.2 In `SkinCard`, add `updateEdgeVisibility()` (called every frame from `extractRenderState`) gating each child button's `isVisible` on the delete-confirmation state and `button.x >= 0 && button.x < parent.width`; make `confirmingDelete` the single source of truth for button visibility. *(superseded by 1.3 after UX review: buttons popped at the left edge)*
- [x] 1.3 Add `EdgeSafeButtonWidget : SpruceButtonWidget` overriding the label rendering (`extractText`/`renderText` per SpruceUI version) to clip through the clamped `ScissorStack` with a vanilla-style marquee; use it for all 6 card buttons; revert `SkinCard` button choreography to its pre-gating form.

## 2. Verification

- [x] 2.1 Build all Stonecutter versions (`1.21.11`, `26.1.2`, `26.2`) successfully.
- [x] 2.2 On 26.2 dev client with 15 skins: open the carousel from the title screen — no crash; cards beyond the right edge are not rendered.
- [x] 2.3 Scroll fully right and back: cards entering/leaving the screen appear/disappear in the same frame, no crash, no rendering artifacts on partially visible cards.
- [x] 2.4 Confirm off-screen cards are not clickable/focusable while hidden, and reorder/apply/delete still work on visible cards.
- [x] 2.5 Sanity-check 1.21.11 and 26.1.2 clients: carousel behaves identically (regression check on versions that never crashed).
