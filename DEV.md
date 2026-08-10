# Development

## Multi-version setup

All supported Minecraft versions are built from a single source tree using [Stonecutter](https://codeberg.org/stonecutter/stonecutter) (with [loom-back-compat](https://codeberg.org/KikuGie/loom-back-compat) selecting the right Fabric Loom variant per version: remap for 1.21.x, no-remap for the unobfuscated 26.x jars).

| Version key | Minecraft | Java | Notes |
|-------------|-----------|------|-------|
| `1.21.11`   | 1.21.11   | 21   | Mojang mappings applied by loom |
| `26.1.2`    | 26.1.2    | 25   | unobfuscated |
| `26.2`      | 26.2      | 25   | active/VCS version |

## Common tasks

- `./gradlew build` — build **all** versions; jars land in `versions/<version>/build/libs/`
- `./gradlew :<version>:build` — build one version (e.g. `:1.21.11:build`)
- `./gradlew :<version>:runClient` — run a client for one version (run dir: `versions/<version>/run/`)
- `Set active project to <version>` (Gradle task) — switch the IDE/committed source to another version; commit such switches separately from real changes
- `Reset active project` — restore the source to the VCS version state before committing

## Where things live

- `src/` — the single shared source tree (Kotlin; mixins in Java)
- `stonecutter.properties.toml` — per-version dependency coordinates (Minecraft, loader, Fabric API, spruceui, yumi, modmenu) and the per-version `fabric.mod.json` constraints
- `stonecutter.gradle.kts` — Stonecutter configuration: active version and textual **replacements** for pure symbol renames between versions
- `build.gradle.kts` — the shared build script applied to every version project

## Version-specific code

Two mechanisms, used deliberately:

1. **`//? if` comment conditionals** (Stitcher) for structural deltas — different method signatures, arity changes, per-class override names:
   ```kotlin
   //? if >=26.2 {
   minecraft?.gui?.setScreen(parent)
   //?} else {
   /*minecraft?.setScreen(parent)
   *///?}
   ```
   The source is always valid Kotlin for the *active* version; other versions' code stays wrapped in `/* */` until Stonecutter processes it.

2. **Replacements** in `stonecutter.gradle.kts` for pure symbol renames (e.g. `GuiGraphicsExtractor` ↔ `GuiGraphics` on 1.21.11). Prefer word-boundary regexes over plain strings — plain string replacement can hit unrelated identifiers (e.g. spruceui's `SpruceGuiGraphics`).

CI builds every version on push (`.github/workflows/build.yml`) — the IDE only type-checks the active version, so cross-version breakage is caught there or by a local `./gradlew build`.

## Adding support for a new Minecraft drop

1. Add the version to `stonecutter { create(...) }` in `settings.gradle.kts`
2. Add its section to `stonecutter.properties.toml` (dependency coordinates, `mod.version`, `mod.mc_dep`, `mod.loader_dep`)
3. `./gradlew :<new>:build`, then wrap any new API deltas in `//?` conditionals (or add a replacement for pure renames)
