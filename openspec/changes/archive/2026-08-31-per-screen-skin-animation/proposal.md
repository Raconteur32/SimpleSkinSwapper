# Per-Screen Skin Animation

## Why

The limb animation toggle introduced by PR #3 is global: one option drives the walk animation of every skin preview (menu buttons, carousel cards, wheel sectors), although each surface calls for different behavior. The option is also misnamed (`enableMovingLegs` — it animates arms and legs) and its scope will shrink to menu previews only. Animation reactivity (hover) belongs to the preview widgets, not to a global switch.

## What Changes

- **BREAKING** — Remove the global `enableMovingLegs` config semantics. The animated/static decision moves to each render call site: `SkinRenderer` callers explicitly request animation.
- Menu previews (title screen + pause menu skin buttons): animated according to a new `animateMenuPreview` config option (default: true), replacing `enableMovingLegs`. Config UI label/description updated (EN + FR) to say the option covers the menu previews and the whole body (arms + legs).
- Carousel: card previews are static by default and play the walk animation while the mouse hovers the card. When the hover ends, limbs return smoothly (eased) to the neutral pose instead of freezing, in the same easing family as the existing drag spring-back. Drag-to-rotate behavior is unchanged.
- Wheel: all sector previews are live 3D renders again at all times — the baked-texture optimization (render PNG until hovered) is removed. The hovered sector's preview plays the walk animation, with the same smooth return on unhover; other previews render statically. The wheel stays non-draggable (click applies the hovered skin).
- The wheel-only preview bake pipeline (`SkinPreviewCache`, the `GuiRendererMixin` bake-queue hook) is removed along with its spec requirements.

## Capabilities

### New Capabilities

<!-- none -->

### Modified Capabilities

- `config-screen`: new scoped requirement — the config screen offers a "menu preview animation" option (replacing the global limb-animation option) that controls whether the title-screen and pause-menu skin previews play the walk animation.
- `skin-carousel`: new requirement — card previews animate on hover with a smooth eased return to the neutral pose on unhover; drag-to-rotate is unaffected.
- `skin-wheel`: replaced requirements — all sector previews are live 3D renders (no baked-texture path, no global bake cache), and only the hovered sector animates, with smooth return on unhover. Sector mesh drawing requirement is untouched.

## Impact

- `SkinRenderer.kt`: `buildRenderState`/render helpers take an explicit animation parameter (intensity factor) instead of reading the global config.
- `SkinPreviewButton.kt`: passes the config-driven flag for menu previews.
- `SkinCard.kt`: tracks hover state, eases an animation factor per card (reuse the nano-time exponential easing pattern of `updateSpringBack`).
- `SkinWheelScreen.kt`: renders every sector preview live; eased hover animation factor per sector; drops `SkinPreviewCache` usage.
- `SimpleSkinSwapperConfig.kt` / `YaclConfigScreen.kt` / `en_us.json` / `fr_fr.json`: option rename `enableMovingLegs` → `animateMenuPreview` (saved configs fall back to the default true), updated label + description.
- Removed if wheel-only (verified during implementation): `SkinPreviewCache.kt`, `GuiRendererMixin.java` (+ entry in `simpleskinswapper.mixins.json`).
- Performance: the wheel may submit up to 10 live picture-in-picture entity renders per frame (previously capped at 1 live + N baked blits) — accepted trade-off per product decision.
