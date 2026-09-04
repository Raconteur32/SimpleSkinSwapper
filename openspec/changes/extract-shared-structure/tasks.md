# Tasks: Extract shared structure

## 1. Serialization toolchain

- [x] 1.1 Add `kotlin("plugin.serialization")` (KGP-matched) and `include(legacy?) kotlinx-serialization-json` + core deps wired for all 4 stonecutter versions; verify `./gradlew build` passes and the jar embeds the runtime
- [x] 1.2 Smoke-check a `@Serializable` data class round-trip in one store before wider use

## 2. JsonFileStore + store migration

- [x] 2.1 Create `JsonFileStore<T>` (typed load/save, pretty-print, SerializationException handling) and migrate `SkinTypeStore`, `SkinNameStore`, `SkinCategoriesStore` onto it; file structures unchanged (fixture-checked against current JSON shapes)
- [x] 2.2 Narrow catches from `Exception` to `SerializationException`; retire the corresponding `@Suppress("TooGenericExceptionCaught")` where honest
- [x] 2.3 Review `SelectedSkinStore`'s file shape and decide: migrate to `JsonFileStore` or keep bespoke (record the decision here)
- [x] 2.4 Build all 4 versions, in-game load test of existing `categories.json`/`types.json`, commit

## 3. Typed API DTOs

- [x] 3.1 `@Serializable` models for Mojang profile, MineSkin response, and base64 texture payloads; rewire `AccountSkinFetcher`, `StartupSkinSync`, `MineSkinUploader`, `MineSkinCache`
- [x] 3.2 Retire or narrow the remaining network total-guard suppressions; build all 4 versions, commit

## 4. Overlay base extraction

- [x] 4.1 Extract `AbstractSkinOverlayPanel` (lifecycle, ESC/Enter, click-away blur/commit, column layout, switch widget) from the `SkinAddPanel`/`SkinDetailPanel` clone; panels keep only their specific content
- [x] 4.2 Build all 4 versions, flag both overlays for the in-game pass, commit

## 5. SkinLibraryScreen decomposition

- [ ] 5.1 Extract `TabStripController` (strip layout, hit-testing, drag-reorder, auto-scroll); build + commit
- [ ] 5.2 Extract `CardDragController` (reorder drag, insertion index, easing, rotate handoff, add-card slotting); build + commit
- [ ] 5.3 Extract `CategoryBand` (wheels stepper, swatch picker, rename/delete confirm); build + commit
- [ ] 5.4 Extract `LibraryFileWatcher` (WatchService loop, self-trigger suppression); build + commit
- [ ] 5.5 Screen keeps orchestration only; flag the in-game pass (tabs drag, card reorder, band, watcher)

## 6. Lock the gains

- [ ] 6.1 Split the 7 deferred `@Suppress("CyclomaticComplexMethod")` functions and retire the suppressions (screen/card/panel/wheel/`sendServerCommandIfNeeded`)
- [ ] 6.2 Adopt `LongMethod`, `LargeClass`, `LongParameterList` in the detekt ledger (fix findings); decide `MagicNumber` with an evidence-based ignore list — defer explicitly if noise-dominated
- [ ] 6.3 Re-run jscpd, compare against the 4.74% baseline, record the new numbers in this file; build all 4 versions, commit
