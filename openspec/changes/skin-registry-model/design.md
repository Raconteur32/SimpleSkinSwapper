## Context

The library currently equates "skin" with "file": `SkinEntry` wraps a user-named png, `SkinCategoriesStore` stores filename lists, `SkinNameStore`/`SkinTypeStore` key per-file metadata, and `confirmAddSkin` refuses filename collisions while a duplicated texture under another name becomes a second skin. Store paths resolve through `FabricLoader.getInstance().gameDir` inline. There is no test source set. Stonecutter chisels `src/main/kotlin` for the vcs version (26.2); branches for other versions live in `/*...*/` comments.

## Goals / Non-Goals

**Goals:**
- A three-level model (texture / skin / card) deduplicating by construction, with a reference-counted texture lifecycle.
- A Minecraft-free core exercised by plain JVM unit tests, extensible with new tests over time.
- A one-shot migration preserving every original file.

**Non-Goals:**
- Detecting manually dropped files (ignored; maybe later).
- Reordering UI in derived or category views (default order only).
- Changing wire formats (proxy/SkinShuffle Bridge stay frozen; `SkinShuffleImporter` just re-routes through the ingest).

## Decisions

### D1 — Environment seam, not a service loader

The only Minecraft touchpoint in the core is the skins-folder path. A tiny `SkinLibraryEnv` interface (`fun skinsDir(): Path`) is the single seam: production wires `FabricLoader...gameDir.resolve("skins")` once at client init, tests pass a temp folder. The core becomes instantiable classes (registry, naming, lifecycle, migration, delete decision) receiving the env by constructor; the existing `object` stores become thin facades delegating to the production instance so GUI churn stays minimal. No `ServiceLoader`, no DI framework — if a second Minecraft dependency ever leaks in, the interface widens.

### D2 — Identity and naming: full hash decides, short hash only names

Identity = SHA-256 over canonical pixels (decoded RGBA via `ImageIO`) plus the model. File names take a short hash prefix; on a collision between different values the prefix lengthens until unique. All comparisons (dedup, ingest) use the full hash — never the short one — so a collision degrades gracefully into a longer name instead of a wrong merge. Hashing sits behind a `fun interface Hasher` so tests inject a tiny-digest implementation and force collisions deterministically.

### D3 — Texture lifecycle is reference counting

Creating a skin that needs a new texture writes the file only after the skin entry is accepted (no orphans). Deleting the last skin referencing a texture deletes the file. Registry validation against the folder on load/refresh prunes skins whose texture vanished externally, cascading to their cards.

### D4 — Testability decisions

JUnit 5 (`useJUnitPlatform`), wired on the active stonecutter tree only (same `isActive` pattern as the detekt block) and run via `:<vcs>:test`. Tested pure pieces: ingest dedup, (texture, model) uniqueness, short-hash lengthening (injected hasher), refcount lifecycle, registry validation pruning, migration against fixture folders, categories (copy semantics, one-ref-per-category, custom names), and the delete-dialog decision extracted as a pure function `(view, categoryCount) → options + message args`. Out of unit-test scope: GUI rendering, watcher threads, Mojang HTTP.

### D5 — Migration is one-shot, versioned, and never destroys originals

Marker field in the new registry file. Per legacy png: hash pixels → write hash-named texture → registry entry (model from the legacy type store or detection, name from the legacy name store or base name); original file moved unchanged into `skins/User Files/`; `categories.json` remapped filename → card ref (no custom names); the persisted selected skin remapped to the skin id. A second load is a no-op.

### D6 — Drag semantics

Drag keeps rotate; the reorder gesture is removed (no gap animation). Dropping a card on a category tab copies the reference (source keeps it; no-op on view tabs; no duplicate ref in the target). The wheel keeps counting cards per category — a skin in two allocated categories occupies slots in both (confirmed).

## Risks / Trade-offs

- [Migration data loss] → originals preserved in `User Files/`, versioned marker, fixture-tested before wiring.
- [Same skin, different PNG encoding → dedup miss] → canonical pixel hashing covers resaves/metadata; exotic encodings may still slip — accepted, surfaced by the registry as a normal second texture.
- [GUI churn from entries → skins refactor] → facade pattern keeps the GUI calling familiar stores; churn lands in `reloadView`/`confirmAddSkin`/detail panel.
- [Chiseled new files] → per the established convention, inactive-version branches are written pre-commented; the core is version-agnostic, minimizing branching.
