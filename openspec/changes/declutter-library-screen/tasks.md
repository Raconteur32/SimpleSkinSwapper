## Tasks

- [x] 1. Remove the two `addRenderableWidget` calls for `band.confirmOverlayButton` / `band.cancelOverlayButton` in `initBandAndFooter`; build + detekt; commit
- [x] 2. Remove the header import row: `initHeaderRow`, the three header widgets, `addSkinFromFile`/`addSkinFromAccount`/`importSkinFile`, `titleZoneLimit`; raise `contentTop()`; strip the unused lang keys (en + fr); build + detekt; commit
- [x] 3. Full 4-version build; flag the in-game pass: header gone + grid height, delete-confirm flow (confirm and cancel routes), no ghost buttons before/after the dialog, keyboard navigation intact
