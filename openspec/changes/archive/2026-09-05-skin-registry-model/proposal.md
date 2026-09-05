## Why

The library conflates "file" and "skin": files are user-named and user-editable, the same texture can exist twice under two names, and the store layer is tangled with Minecraft calls so none of it is unit-testable. The library needs a formal three-level model (texture / skin / card) that is deduplicating by construction, plus a unit-test core so regressions can be caught as the system evolves.

## What Changes

- **BREAKING**: skin identity moves from filename to (texture content hash, model wide|slim). The library migrates once on first load; original files are preserved untouched under `skins/User Files/`.
- Textures become mod-managed, hash-named files with a reference-counted lifecycle (created on first skin use, deleted when the last skin referencing them disappears). Short-hash filename collisions resolve by progressively lengthening the hash; identity comparisons always use the full hash.
- A skin registry holds unique (texture, model) pairs with a display name; category entries become card references (skin ref + optional per-category name), one reference max per category, multi-category membership allowed.
- New derived Uncategorized view (skins in zero categories); drag reorder inside views is disabled; dragging a card onto a category tab COPIES it; drag onto view tabs is not supported.
- Add flow deduplicates by texture value; the model is pre-filled automatically (Mojang texture metadata for account imports, pixel detection for file imports) and stays overridable; renaming edits display names only, never files; the detail panel gains a per-category custom-name field.
- Delete becomes a dynamic dialog (remove card here vs delete skin everywhere, with cross-category counts); externally deleted texture files prune their skins and cards.
- The core (registry, hashing, refcount, migration, delete decision) is designed Minecraft-free behind a single environment seam, with a JUnit 5 test suite bootstrapped.

## Capabilities

### New Capabilities

None — all behavior lands in the existing `skin-library` capability.

### Modified Capabilities

- `skin-library`: registry model, texture lifecycle, Uncategorized view, deduplicating ingest, delete semantics, drag semantics, migration.
