# Design: warnings-as-errors

## Context

Survey (2026-09-26, `compileKotlin --rerun-tasks` ×4 trees): one unique warning, `DyeIcons.kt` `LOCATION_ITEMS` deprecated, repeated once per generated tree. The shared build script already centralizes `kotlin { compilerOptions }` per version (JVM 21 / JVM 25 targets). See proposal.md — Why.

## Goals / Non-Goals

**Goals:**

- Zero-warning builds, enforced: any new warning fails compilation on every tree.
- The existing single warning resolved the right way (replacement API over suppression).

**Non-Goals:**

- javac warnings (5 mixins), Gradle infrastructure deprecations, detekt ledger changes.
- Selective per-diagnostic gating — decided against (D1).

## Decisions

### D1 — Full `allWarningsAsErrors`, not a selective diagnostic list

A curated `-Xwarning-level=...:error` list needs maintenance and silently rots (new warning categories default to "warning" and slip past). Full enforcement has no gaps: every warning is an error, and the only escape is an explicit, commented `@Suppress` — the same discipline as the detekt ledger and the Konsist rules. Alternative (selective list) rejected: its failure mode is invisible drift, exactly what this change exists to prevent.

### D2 — Suppressions are deliberate, commented, and per-site

Where no replacement API exists on some version, `@Suppress("DEPRECATION")` with a justifying comment naming the version(s) and reason (e.g. "no non-deprecated accessor on 1.21.11"). This keeps the suppression visible in review and in the per-tree generated source. Blanket module-level suppression is forbidden — it would silently disable the drift alarm.

### D3 — Scope: shared compilerOptions, main + test, all trees

One line in the shared `kotlin { compilerOptions }` block covers every version project's main and test compilations — no per-tree divergence, no way for one tree to rot while others stay clean. The Konsist test suite compiles under the same policy.

## Risks / Trade-offs

- [A future MC drop deprecates APIs en masse → CI breaks at version bump] → Accepted and intended (maintainer sign-off, proposal): the break is the alarm; triage is wave-by-wave like the detekt ledger.
- [Experimental/incubating Kotlin or library APIs may warn spuriously] → If one ever does, it gets an explicit commented suppression — visible, reviewable, never silent.

## Migration Plan

One build line + one warning fix. Rollback = revert the commit.

## Open Questions

(none — the LOCATION_ITEMS replacement to prefer is settled during implementation by checking each tree's mapped API.)
