## Tasks

- [x] 1. Spike the icon render path: blit a dye atlas sprite through the GUI pipeline in all 4 generated trees (preferred: `blitSprite` sprite overload; fallback: atlas bind + `getU0()/getV0()`); resolve the 16 dye map colors/ARGBs; record the chosen path and values in design.md; commit
      RESULT: blitSprite(TextureAtlasSprite) overload exists in all 4 trees; only acquisition branches (1.21.11 blocks atlas via Material, 26.x items atlas via SpriteId/AtlasIds). Palette derives from DyeColor at runtime. LESSON: src/main/kotlin is chiseled for vcs version 26.2 — new branched files must be written pre-commented (raw directives break the active compile).
- [ ] 2. Rework `SkinCategoryPalette` to the 16 dyes (ENTRIES from DyeColor wool values, `swatches()` → dye list, DEFAULT_HEX → blue dye); keep `toHex`/`parse` contracts; all existing consumers build and detekt green; commit
- [ ] 3. Band picker: 16 dye icons in 8×2 (replacing the 10×2 `fill()` grid), white border on selected dye, no highlight when no dye matches, vanilla dye-name tooltip; re-run the band layout_check script if geometry shifts; commit
- [ ] 4. Category tab: dye icon (8×8, grow if illegible in game) replacing the flat color square; test in game on 26.3 (picker, tab, wheel sectors, card tints — wool palette look); commit
