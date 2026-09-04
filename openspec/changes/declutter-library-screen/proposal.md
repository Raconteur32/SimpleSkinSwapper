## Why

The library screen's top row (account field + "add from file" + "add from account") duplicates the add-skin overlay, which is now a strict superset (type switch, display name, rename, duplicate guard, invalid-account flash). Removing the row frees vertical space for the grid. Separately, the category-delete confirm/cancel buttons leak as visible, clickable ghost buttons at the top-left corner (and stay clickable at their last centered position after a confirm dialog closes — a stray mid-screen click can delete the selected category).

## What Changes

- **BREAKING (UI)**: remove the header import row (account field, add-from-file, add-from-account buttons). All imports go through the add-skin overlay. `contentTop()` rises by the freed row; the title keeps its top-left anchor.
- Fix the ghost confirm/cancel buttons: stop registering `band.confirmOverlayButton` / `band.cancelOverlayButton` as screen renderable widgets — they are already routed by `handleChromeClick` and rendered manually by the band's confirm overlay draw.
- Remove the dead code path (`addSkinFromFile`, `addSkinFromAccount`, `importSkinFile`, `initHeaderRow`, header widgets, `titleZoneLimit` clamp) and the now-unused lang keys (`add_from_file`, `add_from_account`, `account_name`) from `en_us.json` and `fr_fr.json`.
- Spec delta: `skin-library` — the add/delete requirement no longer mandates the header import row; the import scenario moves to the overlay.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `skin-library` — drop the header-import-row clause from the add/delete flows requirement; file and account import remain available through the add-skin overlay only.
