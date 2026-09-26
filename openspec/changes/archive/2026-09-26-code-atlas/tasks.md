# Tasks: Code Atlas

## 1. Hotspot signal

- [x] 1.1 Write the hotspot script (churn per file from `git log --numstat`, joined with current `wc -l` and `npx jscpd --min-tokens 60` clone output; dependency-free bash, final location per design D3 open question). Verify: running it prints the ranked table and reproduces the `gui/library` cluster as top hotspot.

## 2. Atlas sections

- [x] 2.1 Create `docs/atlas.md` with the "as of" header (commit + date) and the package dependency graph (6 packages, mermaid). Verify: GitHub/mermaid syntax renders; agent can re-derive the edges from imports.
- [x] 2.2 Screen state/dispatch diagrams: library screen (tabs → views → cards → overlays lifecycle: `init()` re-attach, `pruned()` purge, commit-on-close), wheel (pages, animation state). Cross-reference archived changes (D4). Verify: diagrams match actual dispatch code paths in `SkinLibraryScreen.kt` / `SkinWheelScreen.kt`.
- [x] 2.3 Guard matrix: file/zone × `//? if` branches extracted by grep from `src/` (design D5). Verify: counts match `grep -c "//? if"`.
- [x] 2.4 Data-flow section: skins dir ↔ stores ↔ registry ↔ networking (MineSkin uploader frozen contract, `skinshuffle:skin_refresh`), with the frozen-format callouts from the review checklist. Verify: every arrow corresponds to a named class.
- [x] 2.5 Open-questions section seeded from the first hotspot run (e.g. `SkinLibraryScreen.kt` size post-split) and anything the diagrams expose. Verify: each question names evidence, no solution baked in.

## 3. Workflow wiring

- [x] 3.1 `DEV.md`: add an "Atlas" note — what it is, when to regenerate (end of structural changes / on demand), how to run the hotspot script. Verify: a fresh agent session can regenerate from DEV.md alone.
- [x] 3.2 Full pass: run `./gradlew build detektAll` (unchanged code, gate must stay green), commit atlas + script. Verify: gates green, docs-only diff.
