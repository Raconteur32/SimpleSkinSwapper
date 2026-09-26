# Design: Konsist architecture tests

## Context

No test infrastructure exists (`compileTestKotlin` NO-SOURCE on all trees). The shared build script already applies to every version project, and `build` depends on `check` — tests added once run ×4. Rules source: the manual review checklist; layering facts: real imports (the code-atlas change will document them, but is not a prerequisite). Maintainer requirement: all supported versions covered whenever possible. See proposal.md — Why.

## Goals / Non-Goals

**Goals:**

- Architecture rules as tests that fail the build on violation, on **every** version tree.
- A rules ledger convention: one wave of rules = one commit, mirroring the detekt ledger discipline.

**Non-Goals:**

- Behavioral/unit testing (rendering, click routing, IO) — out of scope.
- Replacing the manual checklist: static rules move out of it, behavioral items stay.
- Enforcing rules on raw `src/` (commented branches are invisible there; per-tree generated sources are the point).

## Decisions

### D1 — Per-version test tasks scanning each generated tree

Each version project gets `konsist` + JUnit test deps; the test scope targets `versions/<v>/build/generated/stonecutter/main/kotlin`. Rationale: exactly the `detektAll` argument — old-version branches are live code only in their tree, so a rule violation hidden in a `//? if <26.3` branch must trip the 1.21.11/26.1.x/26.2 runs. Alternative (single root test run over raw `src/`): rejected by the maintainer's all-versions requirement; kept only as documented fallback.

### D2 — Spike-first, uncertainty recorded

Konsist in a Stonecutter multi-project is unproven: (a) does `KoScopeCreator.fromDirectory` reliably resolve the generated tree, (b) does Konsist 0.17.3's embedded Kotlin parser coexist with Kotlin 2.4.10 compilation, (c) JUnit wiring on the JDK 21/25 mixed toolchains. The change opens with a spike task: prove one trivial rule on the active tree, then extend per-tree. Fallback if per-tree scoping fails: root-level run over `src/main/kotlin` (loses old-branch visibility — documented regression, revisit Konsist version later).

### D3 — Wave-1 rules are contract-shaped, not style-shaped

Overlay lifecycle (`SkinOverlayPanel` implementors override `pruned()` + re-attach hook) and Gson confinement are declarations Konsist can see statically. Layering rules (`gui` → `library` → `data`, no back-edges) derive from actual imports at implementation time — if the real graph has legitimate back-edges, the rule encodes reality, not an ideal. Anything needing call-sequence or runtime knowledge stays manual.

### D4 — Tests live in the single shared `src/test/kotlin`

Same single-source-tree pattern as `src/main`: one test source compiled per version project. Rule code that must differ per version is Stonecutter-guarded like production code — wave 1 sees no need.

## Risks / Trade-offs

- [Konsist spike fails entirely] → fallback D2 still ships value (active-tree coverage); per-tree reopens when Konsist supports it.
- [Generated trees don't exist before a first build] → tests depend on `compileKotlin` output ordering; wire the scope creation after generation or document "build before test" (CI already builds first).
- [False positives annoy and get the gate bypassed] → each rule carries a justifying comment like the detekt suppressions; new rules land one wave per commit so a noisy rule is revertable in isolation.
- [Build time ×4] → Konsist parses ~7 k lines per tree in seconds; acceptable.

## Migration Plan

Additive (test deps + test sources). Rollback = revert the commit. No shipped-jar impact.

## Open Questions

- Exact layering edges for wave 1 (derived from imports during implementation — the atlas section, once written, becomes the reference).
- JUnit vs Kotest runner for Konsist — settle at the spike; Konsist docs default to JUnit 5.
