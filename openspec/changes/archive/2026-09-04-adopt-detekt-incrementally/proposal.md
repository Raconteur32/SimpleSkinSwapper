# Proposal: Adopt detekt incrementally

## Why

The codebase (~6 400 lines of Kotlin, 56% GUI) has no static analysis: duplication (three near-identical JSON stores) and a 1 447-line screen class go unnoticed, and nothing objectively guards quality as agents write code. Enabling every detekt rule at once would surface hundreds of violations, so adoption must be gradual to stay reviewable.

## What Changes

- Add the detekt Gradle plugin to the root project, analyzing `src/main/kotlin` once (pre-preprocessor), with a config that starts from zero active rules (`build-upon-default-config: false`).
- Add a `detekt` step to CI so every adopted rule becomes a permanent regression gate.
- Adopt detekt rules in waves, one commit per wave, fixing the entire repository after each wave (ratchet: repo stays clean at every step):
  1. `potential-bugs` ruleset (real bugs, near-zero false positives)
  2. `naming` ruleset (Kotlin conventions)
  3. `exceptions` ruleset (`TooGenericExceptionCaught`, `SwallowedException`…)
  4. Low-noise style rules (`UnusedPrivateMember`, `WildcardImport`, `UseCheckOrError`, `UseRequire`…)
  5. Soft complexity rules (`ComplexCondition`, `NestedBlockDepth`, `CyclomaticComplexMethod` with a high threshold)
- Introduce jscpd (via `npx`, no install) as a refactoring-input tool: measure current duplication (the three JSON stores, GUI drag/scroll/layout clones) into a report that feeds a future Extract Class change on `SkinLibraryScreen.kt`.
- Add a project code-review skill (`.opencode/skills/code-review/`) that codifies: run `./gradlew detekt` before commit, run jscpd before proposing Extract* refactors, and the mod-specific conventions (stonecutter version forms, `//? if` guards, baked-sprite tinting rule, overlay lifecycle, 4-version build verification).

Deliberately deferred: `LongMethod`, `LargeClass`, `LongParameterList`, `MagicNumber` belong to the future `SkinLibraryScreen` refactoring change — enabling them first would flood the build with the violations that refactor is meant to remove.

## Capabilities

### New Capabilities

(none — pure tooling and process change, no spec-level behavior change)

### Modified Capabilities

(none)

## Impact

- **Build**: root `build.gradle.kts` (detekt plugin), `gradle/detekt/detekt.yml` (config), `.github/workflows/build.yml` (new step).
- **Code**: touch-ups across the repository after each rule wave; no behavior change intended — every fix is lint-driven.
- **Docs/process**: new `.opencode/skills/code-review/SKILL.md`; jscpd duplication report stored as an ad-hoc artifact, not committed long-term.
- **Workflow**: after this change, `./gradlew detekt` must pass before any commit; contributors and agents follow the new code-review skill.
