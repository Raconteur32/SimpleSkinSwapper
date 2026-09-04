## Context

Exploration established the blind spot: `./gradlew detekt` (build.gradle.kts, `if (sc.current.isActive)`) always points at `src/main/kotlin`, so the gate sees exactly one flavor of each stonecutter-branched construct. The generated trees already exist after any build (`versions/<v>/build/generated/stonecutter/main/kotlin`) with directives resolved and rewrites applied — verified in `versions/1.21.11/.../SkinLibraryCard.kt` (`override fun render(...)` live) and `CategoryBand.kt` (`.drawString(`). The existing detekt setup (JavaExec + detekt-cli 1.23.8 on the JDK 21 toolchain, `build-upon-default-config: false`) carries no type-resolution rules, so running the CLI over a generated tree needs no classpath — the same invocation with a different `--input` works as-is.

Constraints: JDK 25 runtime crashes the detekt Gradle plugin (JavaExec on toolchain 21 is the established fix); CI gate lives in `.github/workflows/build.yml` with `detekt` before the multi-version build; one commit per finding group; suppression comments must justify themselves.

## Goals / Non-Goals

**Goals:**
- `./gradlew detektAll` lints the 4 generated trees with the identical rule ledger.
- CI runs it; a version-only regression can no longer slip through.
- Findings surfaced by the new trees are fixed (or suppressed with justification) so the gate lands green.

**Non-Goals:**
- No rule-ledger changes (detekt.yml stays as-is; no new rules).
- No type-resolution runs (no classpath wiring for generated trees).
- No replacement of the existing `detekt` task — it stays as the fast single-tree pass.
- No detekt on stonecutter's intermediate or cache dirs — only the final per-version Kotlin trees.

## Decisions

### D1 — New root task `detektAll`, not a widening of `detekt`

`detekt` keeps its fast single-input contract (`./gradlew detekt` during development). `detektAll` registers on the root (or active) project, enumerates the four generated trees explicitly (the version list is static in `settings.gradle.kts`), and runs the CLI once per tree via the same JavaExec/JDK 21 setup. Explicit enumeration over globs: a version added to stonecutter without adding it to `detektAll` should be a visible one-line diff, mirroring `versions(...)`.

Sequencing: the generated trees are produced by stonecutter during the version builds, so `detektAll` depends on the generation having happened — implementation must pin the real task dependency (candidate: depend on the stonecutter generate/build tasks of the version projects; discovered empirically in task 1, recorded here if it differs from "runs after build").

### D2 — Findings follow the adopt-detekt-incrementally discipline

Expected finding profile is narrow: version-specific exception handling (SDL/EGL paths live only in 26.x branches), `UnusedPrivate*` divergences (symbols referenced from a single version branch), and complexity deltas inside `//?`-branched functions. Each finding group gets its own commit: fix where the fix is real (e.g. narrowing a catch), suppress with a justifying comment where the version split makes the shape legitimate (mirroring the mixin-anchor precedent). Thresholds are not raised to make findings disappear.

### D3 — CI order: build first, then `detektAll`

The generated trees must exist before linting them. CI already runs the multi-version build; `detektAll` slots after it (and the fast `detekt` stays before, failing quickly on the common case). Local usage: run `./gradlew build detektAll` or `detektAll` after any build — the task fails loudly if a tree is missing rather than silently skipping it.

## Risks / Trade-offs

- [Generated-tree quirks trip rules the source never would] → expected and desired; handled finding-by-findings per D2, not by config exceptions. If a stonecutter artifact (e.g. directive leftovers) proves to be systematic noise, the fix is a documented `--exclude` path pattern in `detektAll`, not rule edits.
- [Stale trees from an old build linted] → `detektAll` depends on generation (D1), and CI runs post-build; the fail-loudly behavior on missing trees covers the local stale case partially — acceptable, documented.
- [Gate cost] ~4 × 3 s CLI runs, negligible next to the 4-version build.
