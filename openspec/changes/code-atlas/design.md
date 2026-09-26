# Design: Code Atlas

## Context

56 Kotlin files / ~7 400 lines + 5 Java mixins, 6 packages (`changeskin`, `config`, `data`, `gui{,config,library}`, `library`, `networking`), four Stonecutter trees. Enforcement exists (detekt ledger, jscpd, build ×4); comprehension does not. First hotspot run (git churn × size) already isolates the `gui/library` cluster. See proposal.md — Why.

## Goals / Non-Goals

**Goals:**

- One artifact (`docs/atlas.md`) answering: what depends on what, which paths the screen logic takes, which code is version-branched, where history says the cost concentrates.
- Dual audience: the agent reads Mermaid as text; the maintainer sees rendered graphs (GitHub renders Mermaid natively in markdown).
- Regenerable on demand — the atlas is refreshed by the agent during sessions, never hand-edited into staleness.

**Non-Goals:**

- No CI enforcement of atlas freshness (docs would churn on every commit).
- No new analysis tooling (CodeQL, Joern, Konsist) — optional follow-ups, not foundations.
- No refactoring in this change; the atlas *proposes* questions, it does not act on them.

## Decisions

### D1 — Mermaid in committed markdown, not an external tool's export

Alternatives: IntelliJ diagrams (human-only, stale, not agent-readable), Joern/GraphML exports (heavy pipeline, Kotlin frontend young), CodeScene-class SaaS (closed, overkill for 7 k lines). Mermaid text is diffable, greppable by the agent, renders on GitHub, and regenerates from a simple agent pass over the source — one artifact, both audiences, zero dependencies.

### D2 — Committed dated snapshots, regenerated explicitly

The atlas carries an "as of commit/date" header. Refresh is deliberate (end of a structural change, or when a question arises), never automatic. Rationale: a stale-but-dated map beats an always-green map nobody audits; the git history *is* the archive of previous states.

### D3 — Hotspots as a dependency-free script over git + jscpd

`scripts/hotspots.sh` (name tbd in tasks): `git log --numstat` churn per file, joined with current `wc -l` and the existing `npx jscpd` clone output. No new tool, no JVM cost; the signal that justified `split-gui-dispatchers` and `extract-shared-structure` becomes reproducible in one command. Alternative considered (CodeScene-style churn×complexity SaaS): rejected — closed loop, and detekt already exposes per-file complexity when needed.

### D4 — The atlas links code to the OpenSpec decision journal

Each diagram section references the archived changes that explain its "why" (e.g. overlay lifecycle ← `card-actions-delete-popup`, `library-ui-polish`). The map answers "what/where"; the journal answers "why" — one hop between them instead of re-deriving intent from source.

### D5 — The guard matrix is extracted from source, by grep, not by Stonecutter

A table (file/zone × version branches) built from `//? if` occurrences in `src/`. It describes the *VCS-side* source (newest-form + commented branches); per-tree reality stays validated by `detektAll`. Good enough for orientation; a full generated matrix would need the build, defeating the "no tooling" goal.

## Risks / Trade-offs

- [Atlas rots quietly after big refactors] → regeneration is a named step in DEV.md's workflow and a natural closing task of any structural change; the dated header makes staleness visible at a glance.
- [Mermaid graphs grow unreadable as the codebase grows] → keep one diagram per concern (deps / screens / guards / data); split a section rather than a hairball. 6 packages and 3 screens are far below the pain threshold.
- [Hotspot churn overstates moved code (a file rewrite counts fully)] → read churn next to net growth and clones; the table ranks candidates, judgement stays human.

## Migration Plan

Pure addition (docs + script + DEV.md note). Rollback = deleting the files. No code, no build impact.

## Open Questions

- Script home: `scripts/hotspots.sh` vs a Gradle task alongside `detektAll` — settle at implementation; the script keeps working either way.
- Whether the atlas should live as one file or a `docs/atlas/` directory — start with one file, split on first pain.
