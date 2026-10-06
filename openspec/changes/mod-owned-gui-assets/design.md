# Design — Mod-owned GUI assets

## Context

The library screen draws chrome from three kinds of vanilla sources: the GUI sprite `recipe_book/overlay_recipe` (via `SkinLibraryScreen.PANEL_SPRITE_ACCESS`), 16 dye item sprites pulled from the items atlas (`DyeIcons`), and two full player-skin textures UV-cropped to head faces (`AbstractSkinOverlayPanel` STEVE/ALEX_TEXTURE). All targets (1.21.11, 26.1.2, 26.2, 26.3) share one chiseled source tree; GUI sprites resolve through the GUI atlas, which already stitches mod-owned sprites (`library/card`, `library/page` prove the path end-to-end). See proposal.md for motivation.

## Goals / Non-Goals

**Goals:**
- Every asset the mod's own screens draw lives under `assets/simpleskinswapper/`.
- Per-role theming granularity for the frame chrome (pack authors can retune one role alone).
- Use the migration to delete version-divergence code (stonecutter guards, deprecation suppress, manual UV blits).

**Non-Goals:**
- Re-skinning buttons hosted in vanilla screens (title/pause preview buttons, library footer `Button`s, `EdgeSafeButtonWidget`) — they stay vanilla on purpose (see specs).
- Any visual change, sprite restyle, or new look for the mod.
- Retheming the mod's own two existing sprites (`card`, `page`) — untouched.

## Decisions

**D1 — Three semantic copies of the frame, not one shared asset.**
`overlay_recipe` plays three roles with ~7 visual usages: tab strip (selected/dragged/inactive/ghost/add — dimming stays a code-side `fill` overlay on the single sprite), card hover highlight, switch knob. Alternatives: keep vanilla (rejected: coupling + rename risk) or one shared `library/frame.png` (rejected: a pack could not retheme a single role; names also document usage at call sites). The three PNGs start pixel-identical — divergence is future freedom, not present cost. All three ship the vanilla `.mcmeta` (`nine_slice`, 32×32, border 4).

**D2 — Head icons are standalone 8×8 sprites, not copied 64×64 skins.**
Only the face region (UV 8,8 → 16,16 of the 64×64 texture) is ever drawn. Ship `library/head_wide.png` / `library/head_slim.png` cropped once at asset-creation time; the code drops manual-UV `blit` calls for standard `blitSprite`. Alternatives: full-skin copies with namespace-only change (rejected: keeps 64×64 payloads and UV magic alive for no benefit).

**D3 — Dye swatches move from the items atlas to the GUI atlas.**
Ship `dye/<color>.png` ×16 (16×16, flat pre-colored sprites — no animation, no mcmeta needed) and blit them like any GUI sprite. This deletes the whole items-atlas divergence: no `SpriteId` vs `Material` stonecutter guards, no `@Suppress(DEPRECATION)` on `TextureAtlas.LOCATION_ITEMS` (`DyeIcons.kt:26-37`). `DyeIcons` shrinks to `spriteId()` + a namespace-agnostic `draw()`.

**D4 — Asset extraction from the chisel base version (26.3).**
PNGs are extracted once from the 26.3 vanilla jar (the stonecutter active target) and committed: `recipe_book/overlay_recipe.png` + `.mcmeta` (×3 under role names), the 16 dye item textures, and the 8×8 face crops of `wide/steve.png` / `slim/alex.png`. They are version-pinned from then on — that is the point.

**D5 — Code repoint stays inside existing call shapes.**
`drawBookPanel` blits `library/tab`; `drawCardFrame` swaps `card` ↔ `card_hover`; the knob blits `library/switch_knob`; heads blit the head sprites. `PANEL_SPRITE_ACCESS` splits into per-role constants; `AbstractSkinOverlayPanel` drops its STEVE/ALEX texture constants.

## Risks / Trade-offs

- [Head crop wrong region] → the 8×8 crop must be pixels (8,8)–(15,15) of the 64×64 source for both models; verify side-by-side against the current render before committing.
- [GUI atlas vs items atlas filtering differences] → dye sprites are flat 16×16 with no mcmeta; both atlases stitch them identically — visually confirmed once on each target.
- [Vanilla-theme packs lose free cohesion] → intentional, per proposal; a match-vanilla pack trivially copies the vanilla PNGs into the mod namespace.
- [3 identical frame files to keep in sync] → acceptable at 32×32; they only change if the mod restyles (a deliberate, rare event).

## Migration Plan

Single change set: add the 22 asset files → repoint the four call sites → build all four stonecutter targets → visual pass of library screen (tabs all states, hover, switch, dye swatches, band color picker) → done. Rollback: revert the commit; no data or format touched.

## Open Questions

None.
