# Mod-owned GUI assets

## Why

The library screen borrows vanilla assets for its chrome (`recipe_book/overlay_recipe`, dye item sprites, steve/alex skin textures). A resource pack cannot retheme the mod's UI without overriding vanilla assets that are used elsewhere in the game — and conversely, vanilla renames on any of the four target versions (1.21.11 → 26.3) can silently break the mod's rendering. The mod already owns two of its sprites (`library/card`, `library/page`); this change completes that ownership.

## What Changes

- Add mod-namespace GUI sprites to `assets/simpleskinswapper/textures/gui/sprites/`:
  - `library/tab.png` + `.mcmeta`, `library/card_hover.png` + `.mcmeta`, `library/switch_knob.png` + `.mcmeta` — three semantic copies of the vanilla `recipe_book/overlay_recipe` nine-slice frame (32×32, border 4), one per role
  - `dye/<color>.png` ×16 — mod-owned copies of the 16 vanilla dye item sprites
  - `library/head_wide.png`, `library/head_slim.png` — 8×8 head-face crops extracted from vanilla `wide/steve.png` / `slim/alex.png`
- Repoint rendering code from vanilla identifiers to the mod-owned sprites:
  - `SkinLibraryScreen.PANEL_SPRITE_ACCESS` splits into per-role sprite constants (`library/tab`, `library/card_hover` via `drawCardFrame`, `library/switch_knob`)
  - `DyeIcons` resolves mod-namespace sprites through the standard GUI atlas instead of the items atlas
  - The wide/slim switch heads blit the standalone 8×8 sprites instead of UV-cropped 64×64 entity textures
- No visual change: the copies are pixel-identical to what is drawn today.
- Out of scope: vanilla buttons on title/pause screens, the library footer buttons, and `EdgeSafeButtonWidget` keep vanilla widget sprites — they are chrome of (or hosted in) vanilla screens.

## Capabilities

### New Capabilities

- `ui-theming`: The mod's own screens draw their decorative chrome exclusively from mod-namespace assets, so a resource pack can retheme the mod without touching vanilla assets.

### Modified Capabilities

<!-- None: layout and behavior requirements are unchanged; this change only relocates asset sourcing. -->

## Impact

- **Code**: `SkinLibraryScreen.kt` (sprite constants + `drawBookPanel`/`drawCardFrame`), `AbstractSkinOverlayPanel.kt` (knob + head blits), `DyeIcons.kt`, `SkinCategoryPalette.kt` (dye name source, unchanged shape).
- **Simplifications**: `DyeIcons` loses its stonecutter `//? if >=26.1` guards and `@Suppress(DEPRECATION)` (no more items-atlas `SpriteId`/`Material`); no more manual UV blits for steve/alex; no `withDefaultNamespace` references left in mod UI rendering.
- **Assets**: 22 new files under `assets/simpleskinswapper/textures/gui/sprites/` (3 frames + 3 mcmeta + 16 dyes + 2 heads).
- **Risk**: low — pixel-identical copies; the only observable difference is namespace, visible only to resource packs.
