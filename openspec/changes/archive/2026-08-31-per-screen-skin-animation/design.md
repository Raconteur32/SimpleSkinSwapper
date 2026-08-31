# Design — Per-Screen Skin Animation

## Context

All skin previews render through `SkinRenderer`, whose `buildRenderState` reads the global `enableMovingLegs` config option and derives `walkAnimationPos` (phase) / `walkAnimationSpeed` (amplitude) from `SimpleSkinSwapperClient.TOTAL_TICK_DELTA` (tick-paced, 20 steps/s). Call sites today:

- `SkinPreviewButton` (title/pause menus) → `renderPlayerFollowingMouse` — mouse-follow pose, animation per global option.
- `SkinCard` (carousel) → `renderPlayerRotatable` — drag yaw/pitch with exponential spring-back (`updateSpringBack`, nano-time delta, `SPRING_RETURN_SPEED`, snap epsilon). Always live-rendered; no hover state today.
- `SkinWheelScreen` → hovered sector live via `renderPlayer`, others via `SkinPreviewCache` baked blits (bake queue drained by `GuiRendererMixin` at GUI-render HEAD).

Vanilla model note: `walkAnimationSpeed` acts as the swing amplitude of the shared HumanoidModel walk cycle — easing it to 0 smoothly returns arms and legs to the neutral pose regardless of phase, which is exactly the desired "smooth settle" behavior.

## Goals / Non-Goals

**Goals:**
- Animation intensity becomes a per-call parameter of the renderer; no call site reads the global toggle.
- Hover-driven animation with eased settle for carousel cards and wheel sectors, reusing the existing exponential-easing pattern.
- Wheel returns to fully live rendering; bake pipeline deleted.
- Config option renamed and rescoped to menu previews.

**Non-Goals:**
- Any drag/rotate capability on the wheel (stays click-to-apply).
- Frame-time-based animation clocks (keep tick-paced animation).
- Changing menu-preview mouse-follow behavior, carousel layout, or sector mesh drawing.

## Decisions

- **Renderer parameter = float intensity, not boolean.** `buildRenderState(skin, limbSwingIntensity: Float, ...)`: `walkAnimationSpeed = AMPLITUDE * intensity`, `walkAnimationPos = TOTAL_TICK_DELTA * SPEED_FACTOR` when intensity > 0 else 0. Easing lives in the widgets: each card/sector holds a `hoverAnimFactor` eased toward 1 (hovered) or 0 (not) with `1 - exp(-k * dt)` — same family as `updateSpringBack` (k ≈ 10, same snap epsilon). Alternative considered: boolean + separate "settle timer" — rejected, the amplitude ramp gives the settle for free and also animates the ease-in.
- **Hover detection: widget-local.** `SkinCard` already has `isMouseOverCard`; reuse it each render pass to compute the hovered state (cards reposition every frame, so a screen-level hover map adds nothing). Wheel: `selectedIndex` already computed per frame is the hover signal.
- **Eased clocks advance with real time, not ticks** (`System.nanoTime` delta like the spring) so the settle duration is stable regardless of tick timing; the walk phase itself stays tick-driven (`TOTAL_TICK_DELTA`).
- **Config rename `enableMovingLegs` → `animateMenuPreview`** (Boolean, default true). Old saved value is silently dropped (falls back to default). YACL tickbox label: "Animate menu skin preview" / FR "Animer l'aperçu de skin dans les menus"; description mentions arms + legs scope. Lang keys renamed accordingly (`simpleskinswapper.config.animate_menu_preview`).
- **Delete the wheel bake pipeline.** `SkinPreviewCache` and `GuiRendererMixin` are only used by the wheel (verified by grep); both are removed, including the mixins.json entry. `SelectedSkinStore.getPreviewTexture()` (menu button) is unrelated and stays.
- **Menu previews read the config once per render** (`SimpleSkinSwapperConfig.get().animateMenuPreview`) and pass 1.0f/0.0f as intensity — keeps the "decision at call site" rule without extra plumbing.

## Risks / Trade-offs

- [Up to 10 live PiP entity renders per frame on the wheel (was 1 + blits)] → accepted per product decision; if profiling shows a stall, the fallback is re-introducing baking behind the same call-site API without changing specs' behavior contracts.
- [Amplitude ease-out can leave limbs visibly mid-swing if phase continues while intensity decays] → decay at k≈10 reaches sub-epsilon (~<2% amplitude) in ~0.4 s; imperceptible. Snap to exactly 0 below epsilon.
- [Renaming the config field loses the user's saved value] → documented breaking change; default (true) matches the PR's default so fresh behavior is unchanged.
- [Removing a mixin (`GuiRendererMixin`) changes mixin fingerprint] → harmless: mixin set is versioned with the mod jar; no saved state references it.

## Migration Plan

Single release: option rename + behavior change ship together. Rollback = git revert of the change commit; old config files load fine (unknown field ignored, missing field → default).

## Open Questions

None.
