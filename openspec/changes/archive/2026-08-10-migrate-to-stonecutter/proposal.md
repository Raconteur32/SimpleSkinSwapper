# Proposal: migrate-to-stonecutter

## Why

Multi-version support is currently maintained with a branch-per-version model (`1.21.11`, `26.1`, `master` = 26.2): every feature and fix commit is manually replayed on each branch. This is error-prone, slows down feature work, and will get worse as Mojang's new year-numbered drops (26.1, 26.2, …) arrive at a faster cadence. A single-branch, single-source model with per-version build variants (Stonecutter) removes the cherry-pick tax and makes supporting a new drop a matter of adding a version folder instead of porting a branch.

## What Changes

- **Migrate build scripts from Groovy DSL to Kotlin DSL** (`settings.gradle` → `settings.gradle.kts`, `build.gradle` → `build.gradle.kts`). Behavior-neutral prerequisite: same jar output, same `runClient`. Stonecutter's DSL, docs, and community are Kotlin-first.
- **Migrate the 1.21.11 target from Yarn to Mojang (official) mappings.** Mechanical symbol rename across the codebase so all supported versions share one naming scheme. Without this, the shared Stonecutter source would need conditionals around nearly every Minecraft symbol reference.
- **Convert the mod's Java sources to Kotlin, per version branch, before merging sources.** Conversion happens on each single-version branch (`master` and the mojmap'd `1.21.11`) so the Stonecutter merge combines sources that are already Kotlin. Mixin classes stay in Java. **User-facing:** the mod gains a `fabric-language-kotlin` dependency.
- **Adopt Stonecutter for multi-version builds:**
  - Single shared `src/` consumed by per-version Gradle subprojects (`:1.21.11`, `:26.1.2`, `:26.2`).
  - Per-version properties (Minecraft, loader, Fabric API, bundled deps such as spruceui/yumi, Java toolchain) move to `versions/<version>/gradle.properties`.
  - Real API deltas between versions are expressed with Stitcher comment conditionals (`//? if >=26.2 { … //?} else { … }`).
- **Collapse the version branches into one main branch.** The `1.21.11` and `26.1` branches become frozen release references; all new work happens on the single branch. **BREAKING** for the development workflow only — no user-facing change.
- **Drop the `1.21.8` branch** (stale at mod version 0.4.0, confirmed out of scope).
- **Start tracking `openspec/` in git** on the main branch (currently untracked), so planning artifacts are versioned with the project.

## Capabilities

### New Capabilities

None — this change is build tooling and repository restructuring only.

### Modified Capabilities

None — no spec-level behavior of the mod changes. The skin wheel, carousel, multiplayer refresh, and configuration behave identically on every supported Minecraft version before and after the migration. `skip_specs: true` is set in `.openspec.yaml`.

## Impact

- **Build system:** `settings.gradle.kts`, `build.gradle.kts`, Kotlin Gradle plugin, new `versions/` directory, Stonecutter + Stitcher Gradle plugins, split of `gradle.properties` (shared vs per-version).
- **Source tree:** one shared `src/` in Kotlin (mixins remain Java); 1.21.11 code renamed to Mojang mappings; Stitcher `//?` conditionals introduced where Minecraft APIs genuinely differ (GUI screen handling, rendering pipeline, …). `fabric.mod.json` version constraints expanded per version.
- **Dependencies:** per-version dependency coordinates (spruceui, yumi-mc-foundation, modmenu, Fabric API, loader, language-kotlin) managed in `versions/<v>/gradle.properties`. **Users must install Fabric Language Kotlin** alongside the mod.
- **Git topology:** version branches retired; `openspec/` becomes tracked. Development, OpenSpec changes, and CI all live on the single main branch.
- **Verification:** `chiseledBuild` (or equivalent) builds all version jars; CI should build every version since the IDE only type-checks the active version.
- **End users:** no change to the config file format or the runtime behavior of any released jar; the only visible change is the new required Fabric Language Kotlin dependency.
