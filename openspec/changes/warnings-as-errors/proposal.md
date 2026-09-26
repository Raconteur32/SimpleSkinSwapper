# Compiler warnings are errors — drift detection on all four trees

## Why

The 2026-09-26 warnings survey found exactly **one** Kotlin warning across all four version trees (`DyeIcons.kt` → deprecated `LOCATION_ITEMS` field, once per generated tree). With the codebase this clean, warnings can carry real signal again: a new Minecraft version that deprecates or retires an API we use becomes a *loud, per-tree* CI failure instead of a buried `w:` line — the cheapest possible drift alarm for the multi-version setup. Warning debt is kept at zero by construction.

## What Changes

- Fix the existing `LOCATION_ITEMS` deprecation in `DyeIcons.kt` (prefer the non-deprecated accessor per version inside the existing `//? if >=26.1` guards; `@Suppress` with a justifying comment only if no replacement exists on some version).
- Enable `allWarningsAsErrors = true` in the shared `kotlin { compilerOptions }` block — applies to main **and** test compilations of every version tree (×4), matching the per-tree philosophy of `detektAll` and the Konsist tests.
- Note in `DEV.md`: warnings are errors; a post-version-bump compile failing on deprecations is the intended drift alarm, triaged wave by wave.

Non-goals: `javac` warnings for the 5 Java mixins stay out of scope (Kotlin option only); Gradle's own deprecation notices are untouched; no detekt ledger change.

## Capabilities

### New Capabilities

(none — build policy, no runtime behavior change)

### Modified Capabilities

(none — `skip_specs: true`)

## Impact

- `build.gradle.kts` (one compiler option + comments), `DyeIcons.kt` (deprecation fix), `DEV.md` (note).
- Trade-off accepted (maintainer, 2026-09-26): a new MC version introducing a deprecation wave breaks CI until each warning is fixed or consciously suppressed with a comment — that friction *is* the feature.
- Cost: under an hour. Risk: none at runtime; worst case a future version needs a batch of suppressions, each individually justified.
