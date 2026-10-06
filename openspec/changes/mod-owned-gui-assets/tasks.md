# Tasks — Mod-owned GUI assets

## 1. Asset extraction

- [x] 1.1 Extract `recipe_book/overlay_recipe.png` + `.png.mcmeta` from the 26.3 vanilla jar and commit three copies under `src/main/resources/assets/simpleskinswapper/textures/gui/sprites/library/` as `tab.png`, `card_hover.png`, `switch_knob.png` (each with its `.png.mcmeta`); verify 32×32 dimensions and `nine_slice` border 4 in every mcmeta
- [x] 1.2 Extract the 16 vanilla dye item textures and commit them as `src/main/resources/assets/simpleskinswapper/textures/gui/sprites/dye/<color>.png` for all 16 `DyeColor` names; verify 16×16 dimensions and that the set matches `SkinCategoryPalette` color names exactly
- [x] 1.3 Crop the face region (pixels 8,8 → 15,15) from vanilla `textures/entity/player/wide/steve.png` and `slim/alex.png` (64×64) and commit `library/head_wide.png` / `library/head_slim.png` (8×8 each); verify pixel-identical to the region the current UV blit draws (compare against a screenshot of the current switch)

## 2. Code repoint

- [x] 2.1 In `SkinLibraryScreen.kt`, replace `PANEL_SPRITE_ACCESS` with per-role mod-namespace constants (`library/tab`, `library/card_hover`, `library/switch_knob`): `drawBookPanel` blits `tab`, `drawCardFrame` swaps `card` ↔ `card_hover`, and the knob site in `AbstractSkinOverlayPanel.kt` blits `switch_knob`; verify no remaining `recipe_book/overlay_recipe` reference (`rg overlay_recipe src/` returns nothing)
- [x] 2.2 Simplify `DyeIcons.kt` to resolve `simpleskinswapper:dye/<color>` through the GUI atlas: remove the `//? if >=26.1` guards, the `Material`/`SpriteId` imports, and the `@Suppress(DEPRECATION)`; verify `rg "LOCATION_ITEMS|SpriteId|Material" src/main/kotlin/fr/raconteur/simpleskinswapper/gui/library/DyeIcons.kt` returns nothing
- [x] 2.3 In `AbstractSkinOverlayPanel.kt`, replace the STEVE/ALEX texture constants and manual-UV blits with standard `blitSprite` of `library/head_wide` / `library/head_slim`; verify no remaining `withDefaultNamespace` reference in mod UI rendering code

## 3. Verification

- [x] 3.1 Build all four stonecutter targets (`./gradlew build` across versions) and confirm no missing-sprite warnings (purple/black checkerboard or atlas log errors) at runtime
- [x] 3.2 Visual pass on at least one 1.21.11 and one 26.x target: tabs (selected, dragged, inactive, ghost, add "+"), card hover highlight, wide/slim switch knob and heads, category tab dye icons, and band color-picker swatches all render identically to pre-change screenshots
- [x] 3.3 Resource-pack smoke test: ship a test pack overriding `simpleskinswapper:library/tab` with a solid color and confirm only the tab strip changes, then confirm vanilla UI (recipe book, buttons, items) is unaffected with no pack present
