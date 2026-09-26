# Code Atlas — a shared comprehension substrate for the codebase

## Why

The mod's quality gates (detekt, jscpd, build ×4 versions) enforce rules but produce no *understanding*: nothing shows the general code structure, the paths the logic takes, or where architecture keeps costing us. Both the maintainer and the AI agent need a shared, always-regenerable map — and an empirical signal (history-based hotspots) to decide what to simplify next. The verification pass of 2026-09-26 confirmed the gates are green; the gap is comprehension, not conformance.

## What Changes

- Add `docs/atlas.md`: a versioned, Mermaid-based map of the codebase, regenerable on demand and committed as dated snapshots — readable as text by the agent, rendered as graphs by GitHub:
  - package dependency graph (6 packages)
  - screen state / dispatch diagrams (library screen + overlays lifecycle, wheel)
  - guard matrix: which code zones are `//? if`-branched per Minecraft version
  - data flows: skins dir ↔ stores ↔ network (MineSkin uploader, `skinshuffle:skin_refresh`)
  - an open-questions section feeding future changes
- Add a small hotspot script (no new dependencies): git churn × current size × jscpd clones per file, the empirical "what should we question" table (first run already points at `gui/library`: `SkinLibraryScreen.kt` absorbed ~5 300 churn lines and is still the largest file).
- Document the regeneration workflow in `DEV.md` (when to refresh the atlas, how to answer agent-side questions with it).

Non-goals: no new enforcement tooling here. Konsist (architecture tests encoding the review checklist) is its own follow-up change; CodeQL/Joern stay optional spikes, only if path questions outgrow reading the atlas.

## Capabilities

### New Capabilities

(none — documentation and tooling only, no runtime behavior changes)

### Modified Capabilities

(none — `skip_specs: true`)

## Impact

- `docs/atlas.md` (new), `scripts/hotspots.sh` (new, location to confirm), `DEV.md` (regeneration note).
- No mod code, no build change, no dependency added. CI untouched (the atlas is a committed snapshot, deliberately not CI-enforced).
- Cost: half a day. Future payoff: faster onboarding for agent sessions, evidence-based refactor proposals, a stable answer to "where does the logic go through?".
