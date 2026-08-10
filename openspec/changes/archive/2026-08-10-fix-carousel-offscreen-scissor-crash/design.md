# Design: off-screen card culling

## Context

The crash mechanism (verified in 26.2 bytecode): `SkinCarouselScreen.extractRenderState` positions every card via `overridePosition(cardX, cardTop)` and renders all of them. Cards fully past the screen edge still render child buttons; vanilla `AbstractWidget` label rendering attaches an unclamped scissor (the button bounds) to the text render state, `GuiRenderer` clamps it to a zero-width physical scissor, and 26.2's new `RenderPass.enableScissor` validation throws. The same latent behavior exists on older versions but is silently tolerated there.

## Goals / Non-Goals

**Goals:**
- No render state is ever submitted for fully off-screen cards.
- Works on both screen edges and at any scroll position, evaluated per frame.
- Identical behavior across all targeted versions (1.21.11, 26.1.2, 26.2) with no per-version code.

**Non-Goals:**
- No virtual scrolling / widget recycling (cards stay instantiated; only rendering is gated).
- No change to layout math, scroll physics, or the title/pause menu previews.
- No workaround inside Mojang's text scissor pipeline (e.g. mixins into `GuiRenderer`).

## Decisions

**Toggle `visible` in the positioning loop, not clamping or early-returns.**

In the existing per-frame loop that calls `card.overridePosition(cardX, cardTop)`, also set `card.visible = cardX + cardW - CONTENT_MARGIN > 0 && cardX + CONTENT_MARGIN < screenWidth`, where `CONTENT_MARGIN` (4px) matches the inset of the card's scissored content (name text and child buttons, both inset by `BUTTON_MARGIN = 4`). Rationale:

- `SpruceWidget.setVisible(false)` skips both rendering and input: `SkinCard` extends SpruceUI's own widget hierarchy (not vanilla `AbstractWidget`), and SpruceUI's overridable `extractRenderState(SpruceGuiGraphics, …)` and `mouseClicked` both gate on `isVisible()` (verified in SpruceUI 10/11 bytecode). Direct Kotlin field access (`card.visible = …`) does not compile — the private SpruceUI field shadows the synthetic property — so the explicit `setVisible(…)` call is used, portable across SpruceUI 9/10/11.
- Single touch point, next to the code that computes the off-screen position — the invariant is impossible to miss later.
- **The margin is load-bearing, not cosmetic.** Vanilla `GuiRenderer` performs no scissor culling (verified in `addElementToMesh`): any submitted element whose scissor clamps to zero width crashes. A card straddling the edge by 1–3px would render while its buttons (inset 4px) are already fully off-screen → their label scissor still crashes. Fractional scroll positions (`cardIndex` is a `Double`) guarantee such slivers occur during normal scrolling, so the visibility test must be derived from the card's *content* bounds, not its frame.

Alternatives considered:

- **Clamp the mod's own scissor in `SkinCard` (line 232, skin name clip).** Rejected: the crashing scissor belongs to *vanilla button labels*, not to the mod's scissor — clamping ours would not fix the crash.
- **Early-return in `SkinCard.extractRenderState` when off-screen.** Would stop rendering but leave the card interactive (clickable/focusable off-screen) and spreads the invariant into a second class; inferior to `visible`.
- **Only add visible cards as screen children.** Requires add/remove churn on every scroll frame; the renderable list is not designed for per-frame mutation. `visible` is the intended mechanism.

## Risks / Trade-offs

- [A card exactly at the boundary (1px visible) renders fully, including its PiP preview texture] → Accepted: correct behavior per spec, negligible cost.
- [Narration/focus order skips hidden cards — off-screen cards unreachable by keyboard] → Acceptable: they are not visible either; focus returns when scrolled into view.
- [Future card content that must tick while hidden (e.g. animations)] → None exists today; `SkinCard` preview animation is time-based, not frame-count-based, so hiding causes no visible jump.

**Per-button edge gating in `SkinCard` (tried, then replaced by edge-safe labels).**

Card-level culling alone was insufficient: the first verification run crashed again with `Scissor at -12, 450 …` — 26.2's validation also rejects *negative scissor origins*, so a button merely touching the left screen edge crashes even while its card is mostly visible. A first iteration hid each child button as it touched the edge (`updateEdgeVisibility`), which stopped the crashes but made buttons visibly pop during scrolling.

**Final decision: `EdgeSafeButtonWidget` (edge-safe label rendering).**

The root cause is one line in SpruceUI: `AbstractSpruceButtonWidget.extractText` renders the label via `ActiveTextCollector.acceptScrolling`, which attaches its scissor **unclamped** to the text render state. The button sprite, by contrast, is a plain blit — safe off-screen. The fix replaces only the label path: a `SpruceButtonWidget` subclass overrides `extractText` (SpruceUI 10/11) / `renderText` (SpruceUI 9, 1.21.11) to clip the label through the clamped `ScissorStack` (`enableScissor`/`disableScissor`), reproducing vanilla's marquee for overflowing labels. Sprite, hover, focus, narration, tooltip and click behavior are inherited unchanged, buttons slide under screen edges without popping, and the per-button visibility choreography reverts to its original delete-confirm-only form.

This mirrors both vanilla's own pattern (scrollable content is custom-rendered inside a clamped scissor) and the community's observed response to 26.2 (VoxelMap-x-SeedMapper#8, skyblock-pv#150 — same crash, fixed per-widget; nobody hooks `GuiRenderer`). The sole external hook is a `protected` SpruceUI method — far more stable than Mojang's GUI renderer internals, and any rename fails at compile time, not in production.
