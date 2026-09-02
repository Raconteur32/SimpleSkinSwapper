# Categorized Skin Library

## Why

The skin library is a single flat, arbitrary-ordered list (the carousel): as the collection grows, finding a skin means scrolling, and ordering skins for the wheel means hand-sorting one global list. With the paginated wheel landing, the flat order no longer scales: the user needs to group skins (categories) and control which groups feed the wheel and in what order.

## What Changes

- **BREAKING**: the carousel screen (horizontal card strip + reorder arrows) is removed and replaced by a full-screen **Skin Library**: vertical category tab strip on the left (an "All skins" tab first, scrolling like the others, scrollable strip) and a responsive grid of skin cards (reading order left→right, top→bottom) that scrolls vertically.
- New **category** concept: name, color (fixed 20-color palette), max wheel allocation (0 = excluded from the wheel), ordered skin list. Persisted in `categories.json` with per-category ordered file lists (same file allowed in several categories later). Uncategorized skins remain visible in "All skins".
- Card interactions: drag-to-rotate on the model (unchanged), reorder by dragging the card frame or a dedicated handle (⋮⋮), index number + category-color marker showing which cards are inside the wheel allocation, cross-category move by dropping on a tab.
- Category tab strip: click/right-click to select, drag to reorder with edge auto-scroll when dragging past the visible strip, drop target for cross-category moves.
- Collapsible per-category config band above the grid (one at a time): color palette, wheel-count stepper, rename, delete (with confirmation prompt; skins fall back to uncategorized).
- Wheel integration: wheels are composed per category in category order, capped by each category's max wheel allocation; pagination dots are colored by category and clickable to jump to a category's first wheel.

## Capabilities

### New Capabilities

- `skin-categories`: category data model and persistence — schema, per-category ordered skin lists, color palette, wheel allocation, migration from the pre-categories state.
- `skin-library`: the management screen replacing the carousel — category tab strip, responsive card grid, drag interactions (reorder, rotate, cross-category), config band, import/delete flows.

### Modified Capabilities

- `skin-wheel`: wheels are composed per category (order, allocation) instead of from the flat global order; pagination dots become category-colored and clickable to jump to a category.

## Impact

- **Code**: `SkinCarouselScreen.kt` replaced by a new `SkinLibraryScreen.kt` (+ tab strip, grid, and config band widgets); new `CategoriesStore` (persistence); `SkinEntry`/order handling reworked around category lists; `SkinWheelScreen.kt` wheel composition + pagination dots; lang files (EN/FR); no server/networking changes.
- **Dependencies**: builds on the paginated wheel (`paginated-skin-wheel`, applied but not yet archived) — this change's wheel deltas assume pagination as the baseline and must be applied/archived after it.
- **Specs**: `skin-carousel` requirements are removed (culling, GUI-scale adaptation and hover-animation behavior are re-specified grid-adapted under `skin-library`).
