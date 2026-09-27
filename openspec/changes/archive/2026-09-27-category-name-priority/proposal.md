# Category name priority

## Why

Confirmed bug: opening the detail overlay on a skin whose card already carries a category name, inside that category's view, then closing it (ESC or click-away, no edits) silently overwrites the skin's **global** display name with the category name. Root cause: `SkinEntry.displayNameOverride` doubles as "global name" and "name shown in the current view", so the panel seeds the global-name field with the category name and commits it back to the registry on close.

## What Changes

- Split the two name roles in the GUI layer instead of overloading `displayNameOverride`: what a card displays becomes an explicit resolution — category name (when set, in that category's view) → global display name → file base name — rather than `reloadView` pre-writing the category name into the entry's display override.
- The detail panel seeds the global-name field from the registry record, never from the view-resolved override; each name field previews only the name it edits.
- Opening and closing the detail overlay without edits commits nothing: no rename lands on a pure open/close cycle.
- Storage unchanged — the registry global name and the per-category card names remain two independent persisted values; **no data migration**.

## Capabilities

### New Capabilities

- (none)

### Modified Capabilities

- `skin-library`: the renaming requirement gains the display-resolution rule (category name takes priority over the global display name inside its category's view only) and the guarantee that a detail-panel open/close cycle without edits changes no stored name.

## Impact

- `gui/SkinEntry.kt` — name fields and display resolution.
- `gui/library/SkinDetailPanel.kt` — field seeding (`refreshFields`, `rebind`) and per-field preview responders.
- `gui/library/SkinLibraryScreen.kt` — `reloadView` stops writing card names into entries; `commitEntryNames` semantics.
- `gui/SkinWheelScreen.kt` — consumes entries' display names; follows the resolution change.
- Tests under `src/test/kotlin/.../gui` and any arch tests touching the library GUI.
