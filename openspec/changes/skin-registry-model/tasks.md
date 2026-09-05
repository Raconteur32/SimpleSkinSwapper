## Tasks

- [ ] 1. Bootstrap tests: JUnit 5 on the active tree (`useJUnitPlatform`, deps under `isActive`), `SkinLibraryEnv` seam, convert the store layer to env-receiving classes with production facades, one trivial green test; commit
- [ ] 2. Texture hashing and naming: canonical pixel hashing behind `Hasher`, short-hash file naming with progressive lengthening, plus tests (including a forced collision via injected hasher); commit
- [ ] 3. Skin registry: unique (texture, model) pairs, display names, persistence (`skins.json`), plus tests; commit
- [ ] 4. Cards: categories store card references (skin ref + optional per-category name), one ref per category, multi-membership, copy-add semantics at store level, plus tests; commit
- [ ] 5. Texture lifecycle: create-only-if-skin-accepted, delete-on-last-ref, registry validation pruning on load/refresh, plus tests; commit
- [ ] 6. Delete decision: extract the pure `(view, categoryCount) → options + message args` function with tests; commit
- [ ] 7. Migration: versioned one-shot (hash textures, registry entries, `User Files/` preservation, category remap, selected-skin remap) with fixture-folder tests; commit
- [ ] 8. Ingest wiring: add-from-file and add-from-account (Mojang metadata model, pixel fallback) through the deduplicating ingest, model pre-fill editable in the panel; commit
- [ ] 9. Views: Uncategorized derived view and tab, drag reorder removal, drop-on-tab copy wiring, no drop on view tabs; commit
- [ ] 10. GUI: dynamic delete dialog, detail panel global + per-category name fields (rename never touches files), legacy stores retired; full build + `detektAll` green; commit
