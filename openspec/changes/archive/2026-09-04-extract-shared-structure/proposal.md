# Proposal: Extract shared structure (overlays, stores, screen)

## Why

jscpd measured 25 exact clones (4.74%) after the detekt adoption change, concentrated in three places: 264 lines shared by the two overlay panels (`SkinAddPanel` / `SkinDetailPanel`), a triplicated JSON store pattern, and a 1 447-line `SkinLibraryScreen` whose commented sections are already the table of contents of its decomposition. Meanwhile 7 `@Suppress("CyclomaticComplexMethod")` markers hold the gate open for structural debt, and Gson's `JsonObject` navigation produces the shape-error class (NPE / `IllegalStateException` on unexpected JSON) that forced a batch of justified total-guard suppressions during the exceptions wave.

## What Changes

- **Adopt kotlinx.serialization** (compiler plugin + runtime Jar-in-Jar via loom `include`) as the mod's JSON layer, replacing hand-rolled `Gson`/`JsonObject` handling in stores and network DTOs. On-disk formats stay byte-compatible with existing user files (`categories.json`, `types.json`, `names.json`, config, cache).
- **`JsonFileStore<T>`**: one generic typed store replaces the copy-pasted load/save/defaults pattern in `SkinTypeStore`, `SkinNameStore`, `SkinCategoriesStore` (and evaluates `SelectedSkinStore`); malformed input catches narrow from `Exception` to `SerializationException`, retiring the corresponding suppressions. First unit-testable seam of the mod.
- **Typed API DTOs**: Mojang profile / MineSkin response / base64 texture payload parsing become `@Serializable` data classes in `AccountSkinFetcher`, `StartupSkinSync`, `MineSkinUploader`, `MineSkinCache`; remaining total-guard suppressions there are retired or narrowed.
- **Overlay base**: extract the shared overlay skeleton (layout, field wiring, click-away blur/commit, close handling, switch rendering) from `SkinAddPanel` + `SkinDetailPanel` into a common base/composition — the 264-line clone.
- **`SkinLibraryScreen` decomposition**: extract `TabStripController`, `CardDragController`, `CategoryBand`, `LibraryFileWatcher` collaborators per the screen's existing section layout; the screen keeps orchestration only.
- **Lock the gains**: adopt the structuring detekt rules (`LongMethod`, `LargeClass`, `LongParameterList`, `MagicNumber` — pixel-value handling decided during design) and retire the 7 deferred `@Suppress("CyclomaticComplexMethod")` markers; re-run jscpd to confirm the drop.

Behavior stays identical: same files, same JSON shapes, same UX. Pure refactor + tooling.

## Capabilities

### New Capabilities

(none — no spec-level behavior change)

### Modified Capabilities

(none)

## Impact

- **Build**: `build.gradle.kts` (serialization plugin, `include` dependencies) — all 4 stonecutter versions.
- **Code**: `gui/library/` (panels, screen, card), `gui/` stores, `changeskin/`, `networking/`; no new features.
- **Dependencies**: `org.jetbrains.kotlinx:kotlinx-serialization-json` (+ core) embedded in the mod jar (~700 KB), aligned with the project's Kotlin version.
- **Process**: detekt ledger grows (structuring rules); `jscpd-findings.md` from `adopt-detekt-incrementally` is the input; after merge, jscpd re-run replaces that baseline.
