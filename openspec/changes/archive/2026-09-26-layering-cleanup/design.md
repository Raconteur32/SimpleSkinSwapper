# Design: layering-cleanup

## Context

Verified 2026-09-26 by reading the source: `SkinType` consumers = 4 `changeskin` files + 5 `gui.library` files; `LibraryFacades.kt` imports only `data`/`library` and holds three objects (`LibraryServices` internal wiring, `SkinRecords`, `SkinLifecycle` facades); its only same-package reference is `SkinCategoryPalette.dyeNameForColor(hex)` — a pure lookup (hex → vanilla dye name via `DyeColor`), used by the legacy-color migration resolver. See proposal.md — Why.

## Goals / Non-Goals

**Goals:**

- `changeskin` and `gui.library` stop importing GUI-package wiring/value types, enforced by a tightened Konsist map.
- Every move keeps the four compile trees and the ×4 Konsist run green at each commit boundary.

**Non-Goals:**

- Splitting `SkinUtils` (domain vs GPU rendering) — stays the single documented `gui` exception for `changeskin`.
- No public-behavior change, no store format change, no wire format touched.

## Decisions

### D1 — `SkinType` moves to the root package, not a new `domain` package

Root already hosts cross-cutting symbols (`SimpleSkinSwapper`, `PlayerMessaging` helpers) and the Konsist rules treat root-level imports as always-allowed — a one-enum package adds structure without value. Nine files change an import line, nothing else.

### D2 — `LibraryFacades.kt` moves wholesale to `library`

The file is core: registry/card-store/namer/lifecycle/migrator wiring with zero GUI imports (verified). Its facade objects keep their `@JvmStatic` API so consumer call-sites only change the import. Consumers: `StartupSkinSync` (`changeskin`), `SkinWheelScreen`, `SkinDetailPanel`, `SkinCategories`, `SkinLibraryScreen` (gui*) — all become forward edges (`gui → library`, `changeskin → library`), already allowed.

### D3 — `dyeNameForColor` moves to `library`, `SkinCategoryPalette` delegates

The resolver is a pure MC-common lookup; keeping it in `gui/library` would recreate the back-edge we are removing. It lands next to the migration code it serves (as `LegacyColorResolver` or a companion on `LibraryMigrator` — implementation picks the smaller diff); `SkinCategoryPalette` (gui, still owns colors-for-rendering) delegates to it. Fallback if the function turns out rendering-entangled on reading: inject the resolver lambda into `LibraryServices.migrator` from GUI init instead of moving it (one `var`, set once).

### D4 — The Konsist map tightens in the same change, to the new reality

`changeskin` allowlist after this change: `config.`, `data.`, `library.`, `networking.`, `gui.SkinUtils` — one GUI exception, documented as the GPU-upload case. Removing the other three entries the same commit is what makes the refactor verifiable: an old import left behind fails `test` ×4.

## Risks / Trade-offs

- [Same-package references hidden without imports (Kotlin allows them)] → the 2026-09-26 read found exactly one (`dyeNameForColor`); any missed one surfaces as a compile error on `build` ×4 — cheap to catch, none can slip to runtime.
- [`@JvmStatic` callers from Java (mixins) break on package move] → mixins touch player/network/menu hooks, not the facades; compile ×4 verifies.
- [Churn noise in blame] → pure moves are one commit per step (D1, D2+D3), reviewable in isolation.

## Migration Plan

Three commits inside the change: (1) `SkinType` move, (2) facades + resolver move, (3) Konsist map tightening + atlas refresh. Each leaves the full gate green; rollback = revert the individual commit.

## Open Questions

(none — pre-verifications done 2026-09-26; implementation picks the exact landing spot for the resolver per D3's smaller-diff rule.)
