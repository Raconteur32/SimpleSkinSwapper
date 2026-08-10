# Fix carousel crash from off-screen card scissor on MC 26.2

## Why

Opening the skin carousel with more skins than fit on screen crashes the client on Minecraft 26.2 with `IllegalArgumentException: Scissor size must be >0, was 0x120`. The carousel positions and renders every card without culling, so cards scrolled fully past the screen edge still render their child buttons; vanilla button labels carry an unclamped scissor rectangle, and 26.2's new `RenderPass.enableScissor` validation rejects the resulting zero-width scissor. Any user on 26.2 with ~5+ skins hits this reliably.

## What Changes

- Skip rendering (and interaction) for carousel cards that lie fully outside the visible screen area, on both left and right edges, by toggling card visibility when the carousel assigns card positions each frame.
- No change to card layout, scroll behavior, or partially-visible card rendering.

## Capabilities

### New Capabilities

- `skin-carousel`: Culling of off-screen cards in the skin carousel so that no GUI element is submitted with a scissor rectangle lying fully outside the window.

### Modified Capabilities

(none — no existing specs)

## Impact

- `SkinCarouselScreen.kt`: card positioning loop in `extractRenderState` gains a visibility toggle per card.
- No API, config, or data-format changes. Other screens (title, pause, wheel) are unaffected — only the carousel creates horizontally scrolled off-screen widgets.
- Crash is specific to MC 26.2's stricter scissor validation, but the fix is version-agnostic and harmless on older versions.
