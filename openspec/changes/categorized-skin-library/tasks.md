# Tasks — Categorized Skin Library

## 1. Category store

- [x] 1.1 Create `SkinCategoriesStore` (gui or changeskin package): load/save `categories.json` at the skins root, schema `{categories:[{name,color,maxWheels,skins:[]}]}`, write-through on every mutation, skip missing referenced files, empty store when file absent. Verify: unit-style main-test or temporary run — save/reload round-trip, missing file, dangling reference.
- [x] 1.2 Define the fixed 20-color palette (10 hues × pastel/vivid) as data shared by library UI and wheel tinting. Verify: palette resolves to 20 distinct ARGB colors, vivid variant used as category color.

## 2. Library screen shell

- [x] 2.1 Create `SkinLibraryScreen` replacing `SkinCarouselScreen` (file deleted, open hooks updated): left tab strip (an "All skins" tab first, scrolling like the others + category tabs in order, click/right-click select, tooltip, selected style), grid panel, footer/header with import file, import account, delete entry, Done. Verify: opens from the existing entry points on 26.3, tabs switch views, import/delete flows work.
- [x] 2.2 Implement the grid layout pure function + rendering: columns clamp 3..8 by panel width, portrait cells centered, reading order left→right/top→bottom, vertical scroll one row per wheel notch with clamp, per-frame culling of fully invisible cards. Verify: order matches category list, culling does not crash at panel edges, wheel scrolls by row.
- [x] 2.3 GUI scale adaptation: strip/grid/band/footer fit at small logical resolutions with minimum sizes; preferred layout unchanged at common scales. Verify: 960×540 unchanged look, ~480×270 usable without overlap.

## 3. Card interactions

- [x] 3.1 Carry over hover animation (idle static, hover limb walk, eased settle) from the carousel cards; suppress hover animation on cards beneath a reorder drag. Verify: parity with carousel behavior; drag-over does not trigger walk animations.
- [x] 3.2 Split drag zones: model drag = rotate (unchanged, vertical included); frame + ≥12×12 ⋮⋮ handle drag = reorder with floating card, empty origin slot, lerp shift, insertion index from cursor in reading order refined by cell half; persist order on release only. Verify: rotate never reorders; handle/frame reorders; restart keeps order.
- [x] 3.3 Card position number (1-based) + allocation marker in category color for the first `maxWheels × 10` cards, visually distinct beyond; updates live on order/allocation changes. Verify: marker set matches allocation math after drags.
- [x] 3.4 Cross-category drop: tabs highlight as drop targets during card drag; drop on category = append to its list (remove from origin category when dragging from one; from All skins = assign; to All skins = unassign); never deletes the file. Verify: each of the three moves; file still on disk.

## 4. Tab drag & category config

- [x] 4.1 Tab drag reorder: 5 px click-vs-drag threshold, insertion gap between remaining tabs, edge auto-scroll (16 px band, linear ramp, ~2 tabs/s max) with continuously recomputed insertion index; persist category order on drop. Verify: reorder persists; overflowing strip auto-scrolls both directions; click still selects.
- [x] 4.2 Config band inside the card zone for a selected category — scrolls away with the content, pushes the grid when expanded (collapsed by default, one open at a time, none on All skins; constant screen layout): 10×2 palette swatches, allocation stepper including 0, EditBox rename, delete with confirmation prompt (skins become uncategorized); write-through on each change; band stays open while its tab is dragged. Verify: all four controls mutate store + UI live; prompt blocks deletion until confirmed.

## 5. Wheel integration

- [x] 5.1 Wheel composition from categories: allocated categories in order, `skins.take(maxWheels*10).chunked(10)`, allocation 0 excluded, uncategorized excluded, empty result renders no sectors without crash; wheel position memory unaffected. Verify: matches the spec scenario A(2,14)/B(0)/C(1,5) → 3 wheels.
- [x] 5.2 Pagination dots colored by owning category, tooltip with category name, click slides to that category's first wheel. Verify: colors and jump against the multi-category case.

## 6. Validation & rollout

- [x] 6.1 Full build all four versions (1.21.11, 26.1.2, 26.2, 26.3), zero warnings; stonecutter guards reviewed on touched files. Verify: `./gradlew build` clean.
- [ ] 6.2 Manual pass in game (user-run): library navigation, drags (rotate/reorder/cross-category/tab), config band, wheel composition + colored dots, restart persistence. Verify: checklist against the spec scenarios.

## 7. Skin detail overlay

- [x] 7.1 Plain card click scales the card up (animated rect lerp, ~0.15 s) into a large panel inset 24 px from every screen edge; base screen stays visible around it; clicking outside the panel or ESC closes it (animated shrink). Verify: open/close from several cards; base screen visible on all four sides.
- [x] 7.2 Detail layout: bulk skin preview on the right (drag to rotate), thin vertical separator in the middle, management controls on the left. Verify: zones never overlap at min window size.
- [x] 7.3 File rename field (sanitized, committed on Enter/blur/click-away, stores migrated: types.json, names.json, categories.json; texture reloaded; detail re-bound to the fresh entry). Verify: rename persists on disk and after restart; old name gone from all stores.
- [x] 7.4 Display name field defaulting to the file name; a set value replaces the file name in the card preview and wheel labels; clearing it restores the file name (skins/names.json). Verify: live card label update in both directions.
- [x] 7.5 Wide/Slim switch built from the library sprites: thin darkened-card body (shorter than the knob, barely longer), full-color overlay square knob sliding over the active side's head (Steve left / Alex right); persists via types.json. Verify: toggle persists; knob side matches applied model.
- [x] 7.6 Two-step Delete button (arm then confirm) deleting the file and cleaning all stores; closes the overlay. Verify: file gone, stores cleaned, grid updates.

## 8. Add-skin flow and card simplification

- [x] 8.1 Card simplification: the Wide/Slim and Delete buttons are removed from skin cards (both live in the detail overlay); the replay button is the single bottom row and the preview gains the freed height. Verify: cards show only the replay button; preview is taller.
- [x] 8.2 Detail fixes: preview scales to its rect (head and feet visible), closes commit a pending file rename (ESC / click outside), drag rotates with a spring return to the rest pose and never freezes the walk animation; the switch heads sit outside the body on each side and the whole row stays inside the panel. Verify: each behavior in game.
- [x] 8.3 Overlays survive a window resize (re-attached by init, bounds synced) and a detached overlay can no longer swallow input invisibly. Verify: open a skin, resize in every direction, ESC still works.
- [x] 8.4 Trailing "+" card at the end of every skin list (idle card frame, bare plus, no preview/name); hidden in an empty category. Verify: shows at the end of All skins and non-empty categories; absent in empty categories.
- [x] 8.5 Add-skin overlay opened from the "+" card (same shell as the detail overlay): source buttons at the top of the left column (native PNG picker; MC name download with invalid-account flash), staged bulk preview on the right, file name and display name fields, wide/slim switch to override auto-detection, Add/Cancel split evenly on the column; Add stays disabled until a skin is staged and the target name is free. Verify: both sources, preview, confirm gating, collision refusal.
- [x] 8.6 Empty category: two-line balanced centered message (drag from All skins / click to open the add menu); clicking anywhere in the card zone opens the add overlay. Verify: message wraps on two lines; click opens the overlay.
