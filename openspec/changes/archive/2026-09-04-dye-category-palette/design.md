## Context

Exploration mapped the color system: `SkinCategoryPalette` (10 hues × pastel/vivid, hex-persisted in `categories.json` via `colorHex`) feeds five consumers — band picker swatches, tab color square, card allocation tints (`allocationColorFor`), wheel ring and sector fills. The band picker and tab are the only discrete "which color" chips; everything else consumes the stored hex. The mod already blits GUI sprites in unbranched code (`graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier, ...)`), but dye textures live in the item atlas, not the GUI atlas. Specs: `skin-library` band requirement already mandates "palette color swatches" — hence the delta; `skin-categories` ("a palette color") is agnostic and untouched.

## Goals / Non-Goals

**Goals:**
- Category colors become the 16 Minecraft dye wool colors (muted look — explicit user choice, to be validated in game).
- The picker shows real dye item icons; the tab shows the category's dye icon.
- Zero item-model rendering, zero new lang keys, zero data migration.

**Non-Goals:**
- No item rendering via `renderItem(ItemStack, ...)` — user explicitly rejected it (model/baking API churn across the four versions).
- No migration or nearest-dye snap of legacy `colorHex` values.
- No change to wheel/card/marker consumers — they keep parsing the stored hex.
- No pastel/vivid duality anymore (verified: pastel variants are consumed by the picker only).

## Decisions

### D1 — Wool colors from `DyeColor` map colors, not firework colors

Each of the 16 `DyeColor`s contributes its map color (`getMapColor()`), the muted wool-block tone. Accepted trade-off (user choice): wheel sectors and card tints will look duller than the current Tailwind palette. The palette stays a static list of (dye → ARGB int, hex string) resolved once; `toHex`/`parse`/`DEFAULT_HEX` keep their contracts so persistence and all consumers are untouched.

### D2 — Dye icons via item-atlas sprite blits, not `renderItem`

Dye item textures are flat pre-colored 16×16 PNGs (`minecraft:textures/item/<color>_dye.png`) — no tint, no model. Render path (spike resolved, verified against the mapped jars of all four versions): `blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, w, h)` exists everywhere; only sprite acquisition diverges — 1.21.11 keeps item sprites in the blocks atlas (`graphics.getSprite(Material(TextureAtlas.LOCATION_BLOCKS, id))`), while 26.1.2/26.2/26.3 stitch them into the separate items atlas (`graphics.getSprite(SpriteId(AtlasIds.ITEMS, id))`, confirmed via `atlases/items.json` inside the client jar; the 26.3 `atlases/blocks.json` has no `item/` source). All behind one `//? if >=26.1` branch in `DyeIcons`.

Stonecutter chisel convention (spike lesson): `src/main/kotlin` is resolved for the vcs version (26.2) — inactive branches must be written pre-commented (`/*...*/` with directives intact); raw directives in a new file break the active-version compile. `DyeColor.values()` + `getMapColor().col` + `getName()` are identical across all four versions, so the palette derives from the enum at runtime (no hardcoded hexes, vanilla dye order). Picker cells: 16 icons in 8×2 (same width as the current 10×2 grid); tab: 8×8 icon (test in game, grow to 10px if the pouch shape reads poorly).

### D3 — Legacy hexes render as-is, no selection highlight

`categories.json` stores hex strings; old values keep flowing through `parse()` into every consumer. In the picker, a stored hex matching no dye simply shows no white border; picking any dye immediately converts the category. This preserves user data untouched and deletes the migration question. The delta spec encodes this as the "Legacy color without dye match" scenario.

### D4 — Tooltips reuse vanilla item names

Hovering a picker cell shows `Component.translatable(item.minecraft.<color>_dye)` (the dye item's vanilla key) — free i18n in every game language, no new lang keys. If resolving the item's key at runtime is cleaner than a hand-built string, do that; both are version-stable.

## Risks / Trade-offs

- [blitSprite overload missing/divergent in an old tree] → D2 fallback (atlas bind + UV blit); spike lands in task 1 before any UI work.
- [8×8 tab icon illegible] → grow to 10px in game testing; the spec says "dye's item icon", not its size.
- [Wool colors too muted on the wheel] → user wants to test live; if rejected, swap D1 to firework colors — a one-list change, no spec impact (spec never mandates specific color values).
