# Categorized Skin Library — Design

## Context

The carousel (`SkinCarouselScreen.kt`) shows one flat horizontal strip ordered by the skins directory listing; the wheel chunks that same flat order. The paginated wheel (`paginated-skin-window`, applied, unarchived) already splits the wheel into sliding pages of 10 with pagination dots — this change redefines what a page contains. Vanilla references studied in the decompiled 26.3 sources: `AdvancementsScreen` (vertical tabs `LEFT/RIGHT` 32×28 px, pitch = tab height, first/middle/last sprites, right-click selects, right-drag pans, `HeaderAndFooterLayout`, `nextStratum()` layering), `CreativeModeInventoryScreen` (scrollbar drag math, per-position tab sprites, item grid), `AbstractScrollArea` (thumb 6 px, min 32, scrollRate), `CursorTypes` (no MOVE cursor — closest is `POINTING_HAND`).

## Goals / Non-Goals

**Goals:**
- Categories as first-class, persisted data driving both the library screen and the wheel composition.
- A library screen that stays comfortable with hundreds of skins (scroll, search-free navigation via categories).
- Drag interactions that never misinterpret intent (rotate vs reorder are spatially separated).

**Non-Goals:**
- Shared membership UI (a skin in several categories at once) — format supports it, v1 UI assigns at most one category.
- Free-form color picker, per-category icons, drag animations beyond simple lerp/insertion gap.
- Server-side or networking changes; search/filter field.

## Decisions

- **Single `categories.json` store, per-category ordered lists.** One JSON file at the skins root: `{"categories":[{"name","color","maxWheels","skins":[file names in order]}]}`. Rejected: file-per-category (orphan files, atomic rename issues) and a single global ordered list (cannot express future shared membership). The skins folder stays the source of truth for which files exist; category lists reference files by name and skip missing ones.
- **Tab strip modeled on AdvancementsScreen, but left-only and scrollable.** Tabs 32×28 px, pitch 28 (no gap), drawn as flat fills in mod style (no vanilla tab sprites — the mod draws its own widgets everywhere; keeps stonecutter diffs nil across 1.21.11→26.3). "All skins" pinned above the scrollable category range. Overflow scrolls instead of vanilla's 5-left/5-right split.
- **Grid layout is a pure function** recomputed on init/resize: `columns = clamp(panelWidth / MIN_CELL_W, 3, 8)`, cell aspect ≈ 3:4 (portrait cards), grid centered in the panel, slot i → (i % cols, i / cols), vertical scroll one row per wheel notch, simple clamp (no wheel-style lead physics). Culling per frame like the carousel does today.
- **Drag zones (spatial disambiguation, A+C).** Model area = rotate (existing behavior, including vertical). Card frame + dedicated ⋮⋮ handle = reorder. No gesture heuristics: vertical model drags are legitimate rotation. Click-vs-drag threshold 5 px for tabs (press+release without move = select). During reorder the dragged card floats on a higher stratum, origin slot stays empty, other cards lerp to shifted slots; insertion index from cursor position in reading order refined to before/after by cell half.
- **All drag/selection state keyed by category object identity**, never index — reordering categories must not retarget the open config band or selection mid-drag. (The config band therefore stays open while its tab is dragged.)
- **Edge auto-scroll for the tab strip**: within a 16 px band of the strip's visible top/bottom during a tab drag, scroll speed ramps linearly up to ~2 tabs/s: `speed = max × (1 − dist/16)`. Insertion index recomputed continuously so tabs beyond the visible range can be crossed.
- **Config band above the grid**, collapsed by default, one open at a time, none for All skins: 20 palette swatches (10×2), allocation stepper `[−] n [+]` (0 allowed), `EditBox` rename, delete button → confirmation prompt. Write-through persistence on every change; cards' markers update in place.
- **Fixed 20-color palette** (10 hues × pastel/vivid, Tailwind-grade values tuned for dark UI): red `#FCA5A5/#EF4444`, orange `#FDBA74/#F97316`, yellow `#FCD34D/#F59E0B`, lime `#BEF264/#84CC16`, green `#86EFAC/#22C55E`, cyan `#67E8F9/#06B6D4`, blue `#93C5FD/#3B82F6`, violet `#A5B4FC/#6366F1`, pink `#F9A8D4/#EC4899`, brown `#C8A882/#8B5E3C`. Category color = the vivid variant; wheel background tints the vivid at low alpha.
- **Wheel composition reads categories**: `categories.filter(maxWheels > 0).flatMap { it.skins.take(maxWheels * 10).chunked(10) }` — unchanged wheel mechanics (continuous `wheelPos`/`targetPos`, sliding, culling). Clicking a category-colored dot sets `targetPos` to that category's first wheel index; the existing slide handles the jump.
- **Screen file strategy**: new `SkinLibraryScreen.kt` + small colocated widgets (tab strip, grid, config band) in the `gui/library/` package; `SkinCarouselScreen.kt` deleted. `SkinEntry`, preview cache, `SkinRenderer`, import/delete flows are reused as-is.

## Risks / Trade-offs

- **Drag complexity on small cells** — handle + frame zones must remain distinguishable at GUI scale 4; mitigate with generous hit areas (handle ≥ 12×12) and cursor feedback (`POINTING_HAND`).
- **Spec baseline dependency** — this change's wheel delta assumes `paginated-skin-wheel` requirements; it must be applied/archived after it, never before.
- **Big libraries** — grid culling keeps draws bounded; categories.json write-through on every drag frame is avoided by persisting on drop/release, not continuously.
- **Stonecutter drift** — `EditBox`/prompt APIs differ pre/post 26.x; keep version-guarded code in thin adapters like the existing screens do.
