# Tasks: warnings-as-errors

## 1. Zero-warning baseline

- [x] 1.1 Fix the `LOCATION_ITEMS` deprecation in `DyeIcons.kt`: prefer the non-deprecated accessor per version inside the existing `//? if` guards; `@Suppress("DEPRECATION")` with a version-specific justifying comment only where no replacement exists (design D2). Verify: `./gradlew compileKotlin --rerun-tasks 2>&1 | grep "^w:"` prints nothing.

## 2. Enforcement

- [x] 2.1 Set `allWarningsAsErrors = true` in the shared `kotlin { compilerOptions }` block (design D3). Verify: `./gradlew build detektAll test` green across all four trees.
- [x] 2.2 Prove the gate trips: temporarily introduce a deprecated call (e.g. the removed `LOCATION_ITEMS` usage) and watch compilation fail on the trees where it warns; revert. Verify: `:26.3:compileKotlin` red during injection, green after revert, zero diff left in `src/`.

## 3. Docs

- [x] 3.1 `DEV.md`: one line under Common tasks or Multi-version setup — Kotlin warnings are errors on all trees; a deprecation wave after a MC version bump is the intended drift alarm, triaged per warning (fix, or suppress with a comment).
- [x] 3.2 Full pass and commit. Verify: `./gradlew build detektAll test` green, commit contains build line + DyeIcons fix + DEV.md note only.
