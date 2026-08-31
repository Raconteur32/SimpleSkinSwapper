# Tasks — Categorized Skin Library

## 1. Category store

- [x] 1.1 Create `SkinCategoriesStore` (gui or changeskin package): load/save `categories.json` at the skins root, schema `{categories:[{name,color,maxWheels,skins:[]}]}`, write-through on every mutation, skip missing referenced files, empty store when file absent. Verify: unit-style main-test or temporary run — save/reload round-trip, missing file, dangling reference.
- [x] 1.2 Define the fixed 20-color palette (10 hues × pastel/vivid) as data shared by library UI and wheel tinting. Verify: palette resolves to 20 distinct ARGB colors, vivid variant used as category color.

## 2. Library screen shell

- [x] 2.1 Create `SkinLibraryScreen` replacing `SkinCarouselScreen` (file deleted, open hooks updated): left tab strip (pinned "All skins" + category tabs in order, click/right-click select, tooltip, selected style), grid panel, footer/header with import file, import account, delete entry, Done. Verify: opens from the existing entry points on 26.3, tabs switch views, import/delete flows work.
- [x] 2.2 Implement the grid layout pure function + rendering: columns clamp 3..8 by panel width, portrait cells centered, reading order left→right/top→bottom, vertical scroll one row per wheel notch with clamp, per-frame culling of fully invisible cards. Verify: order matches category list, culling does not crash at panel edges, wheel scrolls by row.
- [x] 2.3 GUI scale adaptation: strip/grid/band/footer fit at small logical resolutions with minimum sizes; preferred layout unchanged at common scales. Verify: 960×540 unchanged look, ~480×270 usable without overlap.

## 3. Card interactions

- [x] 3.1 Carry over hover animation (idle static, hover limb walk, eased settle) from the carousel cards; suppress hover animation on cards beneath a reorder drag. Verify: parity with carousel behavior; drag-over does not trigger walk animations.
- [x] 3.2 Split drag zones: model drag = rotate (unchanged, vertical included); frame + ≥12×12 ⋮⋮ handle drag = reorder with floating card, empty origin slot, lerp shift, insertion index from cursor in reading order refined by cell half; persist order on release only. Verify: rotate never reorders; handle/frame reorders; restart keeps order.
- [x] 3.3 Card position number (1-based) + allocation marker in category color for the first `maxWheels × 10` cards, visually distinct beyond; updates live on order/allocation changes. Verify: marker set matches allocation math after drags.
- [x] 3.4 Cross-category drop: tabs highlight as drop targets during card drag; drop on category = append to its list (remove from origin category when dragging from one; from All skins = assign; to All skins = unassign); never deletes the file. Verify: each of the three moves; file still on disk.

## 4. Tab drag & category config

- [x] 4.1 Tab drag reorder: 5 px click-vs-drag threshold, insertion gap between remaining tabs, edge auto-scroll (16 px band, linear ramp, ~2 tabs/s max) with continuously recomputed insertion index; persist category order on drop. Verify: reorder persists; overflowing strip auto-scrolls both directions; click still selects.
- [x] 4.2 Config band above the grid for a selected category (collapsed by default, one open at a time, none on All skins): 10×2 palette swatches, allocation stepper including 0, EditBox rename, delete with confirmation prompt (skins become uncategorized); write-through on each change; band stays open while its tab is dragged. Verify: all four controls mutate store + UI live; prompt blocks deletion until confirmed.

## 5. Wheel integration

- [x] 5.1 Wheel composition from categories: allocated categories in order, `skins.take(maxWheels*10).chunked(10)`, allocation 0 excluded, uncategorized excluded, empty result renders no sectors without crash; wheel position memory unaffected. Verify: matches the spec scenario A(2,14)/B(0)/C(1,5) → 3 wheels.
- [x] 5.2 Pagination dots colored by owning category, tooltip with category name, click slides to that category's first wheel. Verify: colors and jump against the multi-category case.

## 6. Validation & rollout

- [x] 6.1 Full build all four versions (1.21.11, 26.1.2, 26.2, 26.3), zero warnings; stonecutter guards reviewed on touched files. Verify: `./gradlew build` clean.
- [ ] 6.2 Manual pass in game (user-run): library navigation, drags (rotate/reorder/cross-category/tab), config band, wheel composition + colored dots, restart persistence. Verify: checklist against the spec scenarios.
