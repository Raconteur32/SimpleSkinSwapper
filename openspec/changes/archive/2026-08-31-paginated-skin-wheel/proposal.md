# Paginated Skin Wheel

## Why

The skin wheel hard-caps at 10 entries (`MAX_ENTRIES`) and silently skips every skin beyond the first ten, making it unusable as a quick picker for larger skin libraries. Pagination into several wheels removes the cap while keeping the wheel readable.

## What Changes

- Remove the 10-skin cap: the wheel pages through the entire skin library in wheels of ten (in `order.txt` order).
- Multi-wheel layout: the active wheel sits at screen center; the previous and next wheels peek at the left and right screen edges — half off-screen, scaled down (~0.7), display-only. Navigation is circular (left of the first wheel is the last).
- Scroll navigation: one scroll notch targets the adjacent wheel; the slide between wheels is an animated, interruptible transition driven by a continuous wheel position chasing an integer target (same exponential easing family as existing mod animations). Target movement is clamped to ±1 wheel from the current position; extra notches mid-slide are absorbed.
- With exactly one wheel no side wheels render; with two wheels the same neighbor legitimately appears on both sides (circular wrap) — placement is derived per slot, so duplicates need no special casing.
- Viewport culling: sector previews whose projected rect lies fully outside the screen are not submitted; partial rects are clamped to the viewport (reusing the 26.2 scissor-safety lesson).
- Pagination feedback below the wheel: dots when there are at most 9 wheels, an "i/N" counter beyond. The hovered-skin name is shown only when the wheel is at rest.
- New config option `rememberWheelPosition` (default: false): when enabled, the wheel reopens at the last position of the session instead of wheel 0. The remembered position itself is session-scoped (in-memory), so a game restart starts at wheel 0.
- The live-rendering and hover-animation behavior stays as-is (no baking), extended to the neighbor wheels.

## Capabilities

### New Capabilities

<!-- none -->

### Modified Capabilities

- `skin-wheel`: pagination of the whole library into wheels of ten, circular multi-wheel layout with non-interactive peeking neighbors, interruptible scroll navigation with ±1 clamp, viewport culling, pagination feedback, opening position rule.
- `config-screen`: new option controlling whether the wheel reopens at its last position (default: off).

## Impact

- `SkinWheelScreen.kt`: becomes a small wheel layout — continuous wheel position, per-slot transform (offset/scale), sector rendering reused per wheel, scroll handling, culling, feedback UI. Hover-animation factors become per-wheel.
- `SkinCarouselScreen.loadOrderedEntries()` reuse stays; `MAX_ENTRIES` cap removed.
- `SimpleSkinSwapperConfig.kt` / `YaclConfigScreen.kt` / `en_us.json` / `fr_fr.json`: new `rememberWheelPosition` option (default false) in the Options category.
- Performance: worst case ~30 live picture-in-picture entity renders per frame (3 visible wheels × 10 sectors), partially reduced by culling — accepted; GUI framerates are sufficient.
- No new dependencies; no mixin changes.
