# Tasks: Konsist architecture tests

## 1. Spike (uncertainty D2 — do first)

- [x] 1.1 Add Konsist 0.17.3 + JUnit 5 test deps to the shared build script; write one trivial rule ("no `java.sql` imports") in the shared `src/test/kotlin`, scope = active tree. Verify: `./gradlew :26.3:test` runs it and passes on the first try of the toolchain.
- [x] 1.2 Extend the scope to a second tree (e.g. `1.21.11` generated sources) and confirm the per-tree resolution works both ways. Verify: `./gradlew test` (all version projects) executes the suite ×4. If either step fails: record findings in this file, apply fallback (root scope over `src/`), and skip 2.3.

## 2. Wave-1 rules

- [x] 2.1 Overlay contract rule: every `SkinOverlayPanel` implementor overrides `pruned()` and the re-attach hook. Verify: passes on all trees; trips when a violation is temporarily injected into a `//? if <26.3` branch (prove the old-tree visibility claim).
- [x] 2.2 Gson confinement rule: Gson imports allowed only in the frozen-contract files (`MineSkinUploader`, inbound DTOs). Verify: passes everywhere; derive the allowed list from actual imports, not intent.
- [x] 2.3 Layering direction rules: `gui` → `library` → `data`/stores, no back-edges (edges derived from real imports; if a back-edge is legitimate, encode reality and note it). Verify: passes on all four trees.
- [x] 2.4 Rules carry justifying comments; one wave = one commit. Verify: each rule file documents its checklist origin.

## 3. Wiring & docs

- [x] 3.1 Update the review checklist skill: statically-encoded items marked as Konsist-enforced (pointer to the test files), behavioral items stay manual. Verify: no rule exists in both places with different wording.
- [x] 3.2 Full pass: `./gradlew build detektAll` green with tests enabled on all trees; check CI file needs no edit (`build` already runs `check`). Verify: local run + next push green.
