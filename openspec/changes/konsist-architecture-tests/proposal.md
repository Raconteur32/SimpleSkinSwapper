# Konsist architecture tests — encode the review checklist, run on every version

## Why

The mod has zero tests, and its architecture rules live in a manual review checklist (`.opencode/skills/code-review`) invisible to every gate. Konsist turns the statically-expressible rules into executable architecture tests. Requirement from the maintainer (2026-09-26): coverage must span **all four supported version trees** — same philosophy as `detektAll`, the only pass that sees old-version branches once they are live code (`//?` guards resolved per tree), not commented text.

## What Changes

- Introduce the repo's first test source sets: Konsist architecture tests, declared once in the shared build script, running in each version project (`1.21.11`, `26.1.2`, `26.2`, `26.3`) via the existing `check`/`build` task.
- Each version's tests scan **that version's generated tree** (`versions/<v>/build/generated/stonecutter/main/kotlin`) — per-version scope, ×4 coverage.
- Wave-1 rules (from the review checklist + actual layering):
  - overlay contract: every `SkinOverlayPanel` implementor overrides the lifecycle members (`pruned()`, re-attach hook)
  - confinement: Gson allowed only in the frozen-contract files (`MineSkinUploader`, inbound DTO parsing) — never spreads
  - layering direction: `gui` → `library` → `data`/`store` dependencies never point backwards (exact edges derived from real imports at implementation)
- Rules still enforced manually (behavioral, out of scope here): sprite-fill ban, EditBox focus propagation, commit-on-close contracts, wire-format contents, old-version rewrite compatibility.

## Capabilities

### New Capabilities

(none — verification tooling, no runtime behavior change)

### Modified Capabilities

(none — `skip_specs: true`)

## Impact

- Shared `build.gradle.kts` (test deps: Konsist 0.17.3, JUnit 5 runner), possibly `stonecutter.properties.toml` per-version test coordinates; new `src/test/kotlin` (single shared source, like `src/main`).
- No change to the shipped jar (testImplementation only). CI gains the tests through the existing `./gradlew build` step — no workflow edit expected.
- Known uncertainty (design D2): Konsist was never run in a Stonecutter multi-project setup — scope targeting, embedded-Kotlin compatibility and per-toolchain JUnit wiring are unproven; the change opens with a spike task and records a fallback if per-tree scoping fails.
