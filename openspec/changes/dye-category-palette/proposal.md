## Why

Category colors use a hand-picked 10-hue Tailwind-style palette (20 pastel/vivid swatches) with no connection to the game's visual vocabulary. Minecraft ships 16 dyes with wool colors players already know and recognize, and 16 > 10 distinct categories. Dye item textures are flat pre-colored PNGs, so the picker and the category tab can display the actual dye icons without any item-model rendering.

## What Changes

- Replace the `SkinCategoryPalette` entries with the 16 `DyeColor` wool colors (MapColor-based, muted look — deliberate, to be tested in game).
- Replace the band's color swatch `fill()` grid (10×2) with 16 dye item icons blitted from the item atlas (8×2, same width), white border on the selected dye, vanilla dye item name as hover tooltip (no new lang keys).
- Replace the category tab's 8×8 color square with the category's dye icon.
- All other consumers (card allocation tints, wheel ring, sector fills, allocation markers) keep consuming the stored hex — they simply render wool colors going forward.
- No migration of legacy `colorHex` values: existing categories keep rendering with their stored color; the picker simply shows no selected dye until the user picks one.

## Capabilities

### New Capabilities

None — the color picking behavior already exists; this changes its palette and icons.

### Modified Capabilities

- `skin-library`: the category band's palette swatches become dye icons; the tab shows the dye icon; picking a color still updates band, tab and markers immediately.
