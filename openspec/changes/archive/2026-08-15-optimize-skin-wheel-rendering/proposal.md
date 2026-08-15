# Proposal: Optimize skin wheel rendering

## Why

`SkinWheelScreen` re-renders everything from scratch every frame: up to 10 full picture-in-picture 3D entity renders (one per skin preview) plus ~1900 single-column `fill()` quads for the pie sectors. This is far more CPU/GPU work than the visuals justify, and the cost will grow linearly with a planned multi-wheel layout (scrollable wheels left/right of the active one). The rendering strategy must change now so the future layout scales.

## What Changes

- **Bake skin previews to textures**: each skin's 3D preview is rendered once into an offscreen texture, then blitted each frame. Baking is progressive (a bounded number per frame) so opening the wheel never spikes.
- **Keep exactly one live PiP render**: only the currently hovered sector's preview stays a real-time 3D entity render (idle animation preserved on hover). With a single mouse cursor there is at most one hovered sector globally, so this invariant survives the future multi-wheel layout.
- **Replace per-column sector fills with triangle fans**: pie sectors are drawn as triangle meshes (one mesh per sector) instead of ~181 `fill()` calls per sector per frame.
- **Store baked previews in a global cache** keyed by skin, not in the wheel screen, so future sibling wheels share them without refactoring.

Out of scope: making static previews feel "alive" (2D re-animation of blits, lazy re-baking). Explicitly not a priority.

## Capabilities

### New Capabilities

- `skin-wheel`: The radial skin picker wheel — its rendering pipeline (baked previews, single live preview on hover, mesh-based sector drawing) and its readiness for a multi-wheel layout.

### Modified Capabilities

(none — the wheel has no existing spec)

## Impact

- `gui/SkinWheelScreen.kt`: sector drawing moves from `fill()` columns to triangle meshes; preview drawing moves to blits + one conditional live PiP render.
- `gui/SkinRenderer.kt`: gains (or is complemented by) a render-to-texture path for baking previews.
- New shared component: a global baked-preview cache (keyed by skin file/texture id).
- No config, networking, or save-format changes. No user-visible behavior change except smoother rendering.
- Forward compatibility: the design must hold for multiple scrollable wheels (left/active/right) without architectural changes.
