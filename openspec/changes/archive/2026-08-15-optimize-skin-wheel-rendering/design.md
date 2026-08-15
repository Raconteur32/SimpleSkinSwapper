# Design: Optimize skin wheel rendering

## Context

`SkinWheelScreen` (gui/SkinWheelScreen.kt) draws, every frame:

1. **Pie sectors** via `fillSector()` — one `context.fill()` per pixel column, ~181 columns per sector at radius 90, up to 10 sectors plus the center circle ≈ **1900 quads/frame**. The geometry is identical every frame; only the hover color changes on one sector.
2. **Skin previews** via `SkinRenderer.renderPlayer()` — for each of up to 10 entries: a fresh `PlayerSkin`, a fresh `AvatarRenderState` (~80 fields), and a `GuiEntityRenderState` picture-in-picture, i.e. a **full offscreen entity render** (own projection, lighting, framebuffer). Vanilla does this for one entity (inventory); we do it ×10.

Target pipeline is MC 26.x `GuiGraphicsExtractor` / `guiRenderState` (stonecutter-conditioned for older versions). A future layout with multiple scrollable wheels (left / active / right) is planned — the design must scale to ~30 visible skins without architectural changes. See proposal.md for motivation.

## Goals / Non-Goals

**Goals:**
- At most 1 live PiP entity render per frame (the hovered sector's preview), regardless of skin count.
- O(N) draw submissions for sector backgrounds instead of O(N × radius).
- Zero allocation churn per frame for previews (`PlayerSkin`, `AvatarRenderState` no longer rebuilt for static previews).
- A preview cache usable by future sibling wheels without refactoring.

**Non-Goals:**
- Re-animating static previews (2D wobble of blits, lazy re-bake rotation). Screen feeling "dead" is an accepted trade-off — explicitly not a priority.
- The multi-wheel layout itself (this change only keeps the door open).
- Changing wheel visuals, geometry, colors, or interaction.

## Decisions

### D1: Bake previews via render-to-texture, blit per frame

Each skin preview is rendered once into a small offscreen `RenderTarget` (≈64×96, matching the 32×48 preview box at 2× for sharpness), registered/cached, then drawn with a plain texture blit each frame.

- Reuse the existing `AvatarRenderState` construction from `SkinRenderer.buildRenderState()` to produce the baked frame, rendered through the entity render dispatcher into the target, with the same rotation/offset constants so baked previews look identical to today's live ones.
- Baking happens progressively: at most 1–2 bakes per frame; unbaked previews simply skip drawing (same as today's not-yet-loaded textures). This keeps open-time flat even with 30+ skins.
- **Alternatives considered**: (a) keep all PiP live but cache `AvatarRenderState` — removes allocations but keeps the dominant cost (10 entity renders/frame), rejected; (b) bake the entire wheel (sectors + previews) into one texture — couples sector hover to the bake and prevents the live hover preview, rejected.

### D2: Exactly one live PiP, bound to the hover

The hovered sector's preview is rendered through the existing `SkinRenderer.renderPlayer()` PiP path, drawn after (on top of) the baked blits. Since there is a single mouse cursor, hover is globally unique — the invariant "≤1 live PiP" holds naturally for the future multi-wheel layout. When nothing is hovered (e.g. during a future scroll), there are zero live PiP renders.

### D3: Sectors as triangle fans through BufferBuilder

Each sector becomes one triangle fan (center + arc samples, ~16–32 segments per sector, gap angle preserved), drawn with the flat-color GUI shader. 10 sectors + center disc ≈ 11 small meshes per frame instead of ~1900 `fill()` calls.

- Hover only changes a vertex color, so the hovered sector's mesh is emitted with the hover color — no caching of meshes needed, rebuild is trivial (few hundred vertices).
- **Alternatives considered**: (a) bake sectors into a texture and redraw only hover — saves rebuilds but adds a render target and complicates hover; meshes are already cheap, rejected; (b) keep per-column fills — rejected, that's the problem.

### D4: Global preview cache

A shared `SkinPreviewCache` (object-level, keyed by skin file / texture id) owns the baked `RenderTarget`s and the progressive bake queue. `SkinWheelScreen` only queries it. This is what makes the future multi-wheel layout free: sibling wheels read the same cache, and D2's single-live-PiP rule already assumes one global hover.

- Cache invalidation: reuse the same lifetime as skin textures (entries are rescanned on screen open; deleting/replacing a skin file invalidates its entry). RenderTargets must be released when entries are evicted to avoid GPU leaks.

## Risks / Trade-offs

- **Static previews lose the idle animation on 9 of 10 skins** → Accepted by user (out of scope). Partially offset by D2: the hovered skin animates, which doubles as hover feedback.
- **Render-to-texture of an entity requires reproducing PiP setup manually** (render dispatcher, lighting, matrix stack into a `RenderTarget`) → Mitigation: keep the code path close to vanilla's `GuiEntityRenderer`/inventory rendering; validate visually against current live previews (same constants, same framing).
- **Bake spike on first open with many skins** → Mitigation: progressive baking (D1), 1–2 per frame, previews pop in over a few frames — same pattern as the existing async texture load.
- **GPU memory: one 64×96 target per skin** (~25 KB each; 30 skins ≈ 0.75 MB) → negligible; released on eviction (D4).
- **Scissor/overlap behavior changes**: today PiP previews are scissored per preview box and painter-sorted; baked blits still need the same sort order for overlapping boxes → keep the existing painter's-order sort for blits, and submit each blit via `addGuiElement` with real bounds (not `addBlitToCurrentLayer` with null bounds) so the GUI render state's bounds-based layering reproduces the PiP ordering; blits piled into one node get re-sorted per texture and lose the painter's order.

## Open Questions

- Exact `RenderTarget` size for baked previews (64×96 vs 32×48) — pick by visual comparison during implementation; does not affect specs or task breakdown.
