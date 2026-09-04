# Tasks: Adopt detekt incrementally

## 1. Setup

- [x] 1.1 Add the detekt Gradle plugin to the root `build.gradle.kts` (recent 1.23.x), verify it runs against the stonecutter layout / JDK 25 (`./gradlew detekt` exits 0; fall back to CLI via `JavaExec` if the plugin clashes)
- [x] 1.2 Create `gradle/detekt/detekt.yml` with `build-upon-default-config: false`, `config.validation: true`, and no active rules
- [x] 1.3 Add a `detekt` step to `.github/workflows/build.yml`; push and confirm it is green

## 2. Wave 1 — potential-bugs

- [x] 2.1 Enable the `potential-bugs` ruleset (keeping only genuinely applicable rules active), run `./gradlew detekt`, fix every finding
- [x] 2.2 Verify `./gradlew build` s till passes on all 4 versions, commit "detekt: adopt potential-bugs rules"

## 3. Wave 2 — naming

- [x] 3.1 Enable the `naming` ruleset tuned to Kotlin conventions, fix every finding across the repo
- [x] 3.2 Build all 4 versions, commit "detekt: adopt naming rules"

## 4. Wave 3 — exceptions

- [x] 4.1 Enable `TooGenericExceptionCaught`, `SwallowedException`, `PrintStackTrace`, `RethrowCaughtException`; fix or per-site exclude with justification (stores / networking catch blocks are the likely hotspot)
- [x] 4.2 Build all 4 versions, commit "detekt: adopt exceptions rules"

## 5. Wave 4 — low-noise style

- [x] 5.1 Enable `UnusedPrivateMember`, `UnusedPrivateProperty`, `WildcardImport`, `UseCheckOrError`, `UseRequire`, `UtilityClassWithPublicConstructor`; fix every finding
- [x] 5.2 Build all 4 versions, commit "detekt: adopt style rules"

## 6. Wave 5 — soft complexity

- [x] 6.1 Enable `ComplexCondition`, `NestedBlockDepth`, `CyclomaticComplexMethod` (high threshold, e.g. 15); fix findings or record exclusions
- [x] 6.2 Build all 4 versions, commit "detekt: adopt complexity rules"

## 7. jscpd duplication report

- [x] 7.1 Run `npx jscpd src/main/kotlin --min-tokens 60 --reporters html,json`; read the report and note the clone clusters (expected: the three JSON stores, GUI drag/scroll logic, detail/add panel layout math)
- [x] 7.2 Summarize findings as input notes for the future `SkinLibraryScreen` Extract Class change (report itself stays uncommitted)

## 8. Project code-review skill

- [x] 8.1 Write `.opencode/skills/code-review/SKILL.md`: run `./gradlew detekt` before any commit; run jscpd before proposing Extract* refactors; mod conventions (26.x vs ≤1.21.11 forms, `//? if` guards, baked-sprite tinting rule, overlay lifecycle, 4-version build as verification)
- [x] 8.2 Reference `detekt.yml` as the rule source of truth (no duplicated rule list), and have the skill loaded for future apply sessions
