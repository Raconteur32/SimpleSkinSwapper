# Design: Extract shared structure

## Context

Baseline from `adopt-detekt-incrementally` (archived): 25 jscpd clones / 4.74%, with 264 lines in the Add/Detail overlay pair, three near-identical JSON stores, and a 1 447-line screen. Seven `@Suppress("CyclomaticComplexMethod")` deferrals are greppable debt markers. JSON handling is Gson `JsonObject` navigation; 6+ justified `@Suppress("TooGenericExceptionCaught")` exist because shape errors surface as runtime NPE/`IllegalStateException`. Project Kotlin is 2.0.x (no multi-catch `|`); targets JVM 21 / 25 via stonecutter; detekt runs without type resolution.

## Goals / Non-Goals

**Goals:**
- One data path: typed `@Serializable` models for persisted files and network payloads; on-disk formats unchanged.
- Deduplicate: overlay skeleton, store pattern, screen sections.
- Retire every deferred complexity suppression by actually splitting the functions.
- End state: structuring detekt rules active; jscpd re-run shows a materially lower clone rate.

**Non-Goals:**
- Any behavior/UX/file-format change (rename migrations, storage layout, etc.).
- Unit-test coverage beyond the new `JsonFileStore` (tests deserve their own change).
- detekt type-resolution adoption (needs a classpath wiring change; revisit later).
- Touching `SkinWheelScreen` beyond retiring its suppression if a clean split emerges; the wheel is not this change's focus.

## Decisions

### kotlinx.serialization over Gson-with-TypeToken for the generic store
`JsonFileStore<T>` is the pivot: with kotlinx, `T` is a `@Serializable` type and defaults live declaratively on properties; with Gson it needs reified `TypeToken` plumbing and still leaves JsonObject navigation for anything nested. kotlinx costs an embedded runtime (~700 KB, loom `include`, standard Fabric practice) and the `plugin.serialization` compiler plugin (KGP-matched version). The mod ships no other heavyweight dependency, so one embedded library is acceptable; in exchange, malformed input surfaces as `SerializationException` — a precise catch that retires the total-guard suppressions honestly instead of broadening them.
*Alternative considered*: staying on Gson, `JsonFileStore<T>` via `TypeToken`. Zero new bytes, but nested models (config, categories, API payloads) keep the shape-error class; the suppressions stay.

### Migration is format-preserving, verified by fixtures
Existing user files must load untouched. Each migrated store gets the exact current file shape asserted against a fixture (JSON strings checked into test resources or checked by a temporary main-less harness during the change): `types.json`/`names.json` (`Map<String,String>`), `categories.json` (`{"categories":[...]}` with name/color/maxWheels/skins), config (nullable enums + `serverCommands` map). Pretty-print settings match Gson's output style closely enough that regenerating a file changes only formatting, never structure. First launch after migration rewrites files in kotlinx formatting — accepted.
*Alternative considered*: Gson round-trip adapters to keep byte-identical output. Rejected — byte identity has no user value; structure identity does.

### Overlay sharing via abstract base, not composition, for the skeleton
`SkinAddPanel` and `SkinDetailPanel` share lifecycle (open/close/instant-close, ESC/Enter, click-away blur/commit, `SkinOverlayPanel` plumbing), column layout math, and the switch widget. A common abstract base (`AbstractSkinOverlayPanel`) holding lifecycle + layout + switch, with abstract hooks (`canConfirm()`, content building), fits how the panels already diverge (staging vs entry-backed). Geometry helpers (`inRect`, preview rects) move to `SkinUtils` as they emerge.
*Alternative considered*: composition/delegates per concern. Rejected for now — the clone is one cohesive skeleton; premature decomposition adds indirection without a second consumer.

### Screen decomposition follows the existing section comments
`SkinLibraryScreen`'s sections become collaborators: `TabStripController` (strip layout, hit-testing, drag-reorder, auto-scroll), `CardDragController` (reorder drag, insertion index, easing, rotate handoff), `CategoryBand` (wheels stepper, swatches, rename/delete confirm), `LibraryFileWatcher` (WatchService loop, self-triggered file suppression). The screen keeps: init/rebuild, scroll, overlay orchestration, click routing. Each extraction is behavior-preserving and lands as its own commit so any regression is bisectable.
*Alternative considered*: big-bang split. Rejected — 4-version build correctness and in-game testing by the user favor incremental commits.

### Structuring rules adopted last, `MagicNumber` with a pixel-ignore list
`LongMethod`, `LargeClass`, `LongParameterList` activate after the splits they gate. `MagicNumber` activates with `ignoreNumbers` extended for common GUI literals (e.g. -1, 0, 1, 2, 10, 100) if needed — the codebase mostly uses named constants already, so the list starts minimal and grows only on evidence. If `MagicNumber` proves noise-dominated even after extraction, deferring it (like TR rules) is an explicit, recorded decision.
*Alternative considered*: adopting all four immediately. Rejected — they'd flood the change with noise before the splits land.

## Risks / Trade-offs

- [kotlinx.serialization + KGP version skew (project Kotlin 2.0.x)] → pick the kotlinx release line matching the KGP version (1.7.x for 2.0.x); verify the compiler plugin applies on all 4 stonecutter version projects in the first task.
- [File-format drift breaks existing users' data] → fixtures per store assert structure compatibility; manual test loads a real `categories.json` in game before merge.
- [Extraction regressions in click routing / render] → each extraction compiles on all 4 versions and lands as its own commit; GUI-behavior commits are flagged for the user's in-game pass (click routing, drag, band, overlays).
- [Embedded library grows the jar] → ~700 KB accepted once; revisit only if more runtimes would follow.

## Migration Plan

Order: toolchain (serialization plugin + include) → `JsonFileStore` + store migration (fixtures) → API DTOs → overlay base → screen extractions (one commit each) → structuring rules + suppression retirement → jscpd re-run. Rollback per commit (git revert) since steps are independent; the serialization toolchain commit is the only one touching build infra.

## Open Questions

- Does `SelectedSkinStore` (property + preview state, different file shape) join `JsonFileStore` or keep its bespoke store? Decide when its file shape is reviewed in-change.
- Final `MagicNumber` ignore list — evidence-based during the last wave.
