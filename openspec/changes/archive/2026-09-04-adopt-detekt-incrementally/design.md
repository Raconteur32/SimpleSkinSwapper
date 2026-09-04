# Design: Adopt detekt incrementally

## Context

Stonecutter generates one project per Minecraft version (1.21.11 / 26.1.2 / 26.2 / 26.3) but sources live once in `src/main/kotlin`; the root `build` task compiles all versions. No static analysis exists today; CI (`.github/workflows/build.yml`) only runs the build. Known hotspots: three near-identical JSON map stores, `SkinLibraryScreen.kt` at 1 447 lines / ~70 functions, no tests. See proposal.md - Why.

## Goals / Non-Goals

**Goals:**
- Every adopted rule becomes a permanent regression gate: the repo is clean after each wave, CI fails on reintroduction.
- Each wave lands as one reviewable commit.
- Zero-config determinism: only explicitly enabled rules run.
- jscpd quantifies duplication to drive the future Extract Class change.

**Non-Goals:**
- Fixing all complexity debt now (belongs to the future `SkinLibraryScreen` refactoring change).
- Adopting `LongMethod` / `LargeClass` / `LongParameterList` / `MagicNumber` (same future change).
- detekt-formatting (ktlint wrapper) — IDE formatting already covers style.
- Unit tests for stores (separate change; noted as a natural follow-up).

## Decisions

### `build-upon-default-config: false`, empty active rule set at start
The shipped default config activates dozens of rules at once, flooding the first run with hundreds of violations. Starting from zero and adding rules one by one makes adoption deterministic: a rule not in `detekt.yml` cannot fail the build.
*Alternative considered*: default config + `detekt-baseline.xml`. Rejected — a baseline freezes existing violations as invisible debt; the ratchet keeps every rule's debt at zero.

### Detekt 1.23.8 CLI via a root `JavaExec` task, on the JDK 21 toolchain (updated during implementation)
The Gradle plugin was tried first but crashes in practice: it reads the Kotlin extension's JVM target 25 / language version and its embedded parser (`JavaVersion.parse`) throws on the JDK 25 runtime string the 26.x builds run on — even with task-level `jvmTarget`/`languageVersion` overrides. The sanctioned fallback won: the `detekt` task is a `JavaExec` running `detekt-cli:1.23.8` pinned to the JDK 21 toolchain foojay already provisions, registered inside `if (sc.current.isActive)` so `./gradlew detekt` analyzes `src/main/kotlin` once, pre-preprocessor (`//? if` guards are comments detekt ignores). Analysis only parses sources, so the toolchain version is irrelevant to findings.
*Alternative considered*: the detekt Gradle plugin. Rejected — crashes on the JDK 25 runtime (see above); revisit when detekt ships a parser that tolerates it.

### CI gate: `./gradlew detekt` as its own step in `build.yml`
Detached from the build so a lint failure is diagnosable at a glance.
*Alternative considered*: making `check` depend on detekt. Rejected for now — the local `build` (all 4 versions, slow) stays lint-free in its failure modes; the gate lives in CI where it belongs.

### Wave order: potential-bugs → naming → exceptions → low-noise style → soft complexity
Ordered by signal-to-noise: real bugs first (near-zero false positives), mechanical conventions next, then judgment calls. Complexity rules come last so their findings arrive when we are ready to act on them; the structuring rules (`LongMethod`…) are explicitly deferred to the refactoring change that removes their violations.
*Alternative considered*: ruleset-by-ruleset alphabetically. Rejected — order should follow value, not naming.

### jscpd via `npx`, as a refactoring input, not a CI gate
jscpd answers "what is cloned and where" — useful when planning Extract* refactors, useless as a pass/fail gate until a duplication budget exists. `npx jscpd src/main/kotlin --min-tokens 60` needs no install; its HTML report is generated ad hoc (not committed).
*Alternative considered*: PMD CPD, or a CI threshold from day one. PMD CPD is heavier to wire in for equal findings; a CI threshold is premature before the first report tells us what "normal" duplication is.

### Project skill rather than relying on generic review skills
`obra/superpowers` provides generic review methodology but knows nothing about stonecutter version forms, `//? if` guards, the baked-sprite tinting rule, or the overlay lifecycle. A `.opencode/skills/code-review/` skill encodes both the tool invocations (`./gradlew detekt`, jscpd before Extract* proposals) and the mod-specific conventions no external skill can know.
*Alternative considered*: installing superpowers. Rejected for now — revisit if broader process methodology is wanted later.

## Risks / Trade-offs

- [detekt plugin vs JDK 25 / Gradle version used by stonecutter] → pin a recent detekt (1.23.x+); if the Gradle integration clashes, fall back to the detekt CLI invoked from a Gradle `JavaExec` task.
- [A wave turns out noisier than expected] → waves are one commit each; a wave can be tuned (excludes, thresholds) or dropped before commit without entangling the next.
- [False positives during adoption get "fixed" into worse code] → policy per finding: fix in code, or exclude with a comment justifying it; blanket `exclude` of a rule is a decision recorded in `detekt.yml`, not a silent edit.
- [Skill drifts from actual rules] → the skill references `detekt.yml` as the source of truth for the rule list instead of duplicating it.

## Migration Plan

Each wave: enable rules → `./gradlew detekt` → fix/exclude findings → build still green on all 4 versions → commit. Rollback per commit (git revert) since waves are independent. jscpd report and skill land after the last wave.

## Open Questions

- Resolved during implementation: detekt 1.23.8, CLI via JavaExec (see Decisions).
- Whether `MagicNumber` can be adopted with pixel-value ignores during the future GUI refactor, or stays deferred with the structuring rules.
