# Tasks: Optimize skin wheel rendering

## 1. Sector meshes

- [x] 1.1 Replace `fillSector()`/per-column fills in `SkinWheelScreen` with triangle-fan emission (center + arc samples, gap angle preserved) through BufferBuilder with the flat-color GUI shader; handle the full-circle case (center disc) as a fan too
- [x] 1.2 Emit hovered sector with hover color, others with base color; verify visual parity (shape, gaps, colors) at several skin counts (1, 3, 10)

## 2. Baked preview cache

- [x] 2.1 Create `SkinPreviewCache` (shared object, keyed by skin file/texture id) holding baked `RenderTarget`s, with eviction that releases GPU targets when skin entries change
- [x] 2.2 Implement render-to-texture of a skin preview (reuse `SkinRenderer.buildRenderState()` + same rotation/offset constants) into a ~64×96 target
- [x] 2.3 Add progressive bake queue: at most 1–2 bakes per frame; unbaked previews are skipped (no draw)

## 3. Wheel integration

- [x] 3.1 In `SkinWheelScreen`, draw previews as blits from `SkinPreviewCache` in existing painter's order; fall back to nothing (skip) when not yet baked
- [x] 3.2 Draw the single hovered sector's preview through the existing live PiP path (`SkinRenderer.renderPlayer`) on top of the blits; zero live PiP when nothing is hovered
- [x] 3.3 Remove per-frame `PlayerSkin`/`AvatarRenderState` construction for non-hovered previews

## 4. Verification

- [x] 4.1 Visual check: baked previews match former live framing/pose; hovered preview animates; pop-in on first open is acceptable
- [x] 4.2 Perf check: measure frame time with 10 skins — confirm ≤1 PiP render/frame and O(N) draw submissions for sectors
- [x] 4.3 Regression check: apply-on-click, right-click close, key release close, empty-library message, and cache reuse across close/reopen all still work
- [x] 4.4 Run `openspec validate optimize-skin-wheel-rendering --strict` (if not already done) and the project's build for all stonecutter targets touched by the rendering code
