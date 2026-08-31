## 1. Data model

- [x] 1.1 Remove the `MAX_ENTRIES` cap in `SkinWheelScreen` and partition entries into wheels of ten (list of lists, in `loadOrderedEntries()` order); add `wheelPos`/`targetPos` floats and a `restWheel` predicate (`|wheelPos - round(wheelPos)| < 0.05`). Verify: `./gradlew :26.3:compileKotlin` succeeds.

## 2. Multi-wheel layout and rendering

- [x] 2.1 Render per-slot wheels: wrapped offset `wrap(k - wheelPos)` into `[-1.5, 1.5]`, linear interpolation of center x between slots (center slot at `width/2`, side slots at screen edges) and scale (1.0 → 0.7); skip slots with `|offset| ≥ 1.5`. Verify: with >10 skins, center wheel + two peeking half-off-screen scaled side wheels render; with ≤10, a single wheel, no side wheels; with 2, the same neighbor appears on both edges.
- [x] 2.2 Thread the wheel transform through sector/background/name rendering; apply viewport culling — skip preview rects fully outside the screen, clamp partial rects into `[0,width]×[0,height]` before submitting (26.2 scissor safety). Verify: no crash on 26.2 and 26.3 with side wheels visible; off-screen previews are not drawn.
- [x] 2.3 Gate interactivity to the rest state: hover picking, click-apply, and hover-animation easing only when at rest and only on the center slot (targets forced to 0 otherwise); hover factors become per-wheel arrays. Verify: hovering side wheels does nothing; during a slide the outgoing wheel's animation settles smoothly; click-apply works only on the centered wheel at rest.
- [x] 2.4 Feedback fix: sector gaps become constant-width lines — `SectorFillRenderState` gains an `innerRadius` fan apex and `drawSector` insets straight edges by `GAP_WIDTH/2` (arc endpoints rotated by `asin(halfGap/radius)`, apex at `halfGap/sin(halfSpan)`); spec mesh requirement updated to MODIFIED. Verify: separator reads as a hairline of constant width from center to rim; N=1 renders a full disc; disc fills unchanged.

## 3. Scroll navigation

- [x] 3.1 Implement `mouseScrolled`: notch → `newTarget = targetPos ± 1`, accepted only if `|newTarget - wheelPos| ≤ 1` (wrap modulo wheel count); ease `wheelPos` toward `targetPos` with nano-time delta. Verify: one notch slides one wheel; reversing mid-slide is smooth; rapid notches are absorbed beyond one pending wheel; circular wrap first ↔ last works.
- [x] 3.2 Feedback fix: replace the ±1 clamp with a lead clamp of 2 — `|newTarget - wheelPos| ≤ WHEEL_MAX_LEAD` — so chained scrolling glides continuously (one active slide + one queued wheel) without unbounded flinging; spec delta and design decision updated. Verify: scrolling in chains glides without waiting for the slide tail; stopping settles within two wheels; reversal stays smooth.

## 4. Feedback UI

- [x] 4.1 Draw pagination feedback under the wheel at rest: dots with active highlight when `wheelCount ≤ 9`, otherwise "i/N" counter; keep the hovered-skin name visible only at rest. Verify: 3 wheels → 3 dots with active highlighted; 12 wheels → counter; name hidden during slides.

## 5. Config option

- [x] 5.1 Add `rememberWheelPosition: Boolean = false` to `SimpleSkinSwapperConfig`, tickbox in a new "Skin wheel" group of the Options category, EN/FR lang keys; session-scoped last position (companion object) restored on open when enabled. Verify: option persists in JSON; disabled → opens at wheel 0; enabled → reopens at last wheel within the session; restart → wheel 0.

## 6. Verification

- [x] 6.1 Full build on all four versions with zero Kotlin warnings (`./gradlew build`). Verify: BUILD SUCCESSFUL, no `w: file:` lines.
- [x] 6.2 Manual pass on the 26.3 dev client: libraries of <10, ~23 and 100+ skins — pagination, side-wheel peeking, slide + reverse + clamp, culling stability, dots/counter, config option behavior, apply/close unchanged. Verify: behaviors observed in `runClient`. (Validated by user, including the lead-clamp 2.0 and constant-width gap fixes.)
