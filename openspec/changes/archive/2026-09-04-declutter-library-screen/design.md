## Context

Two independent problems on `SkinLibraryScreen`, fixed together so one in-game pass covers both. The spec `skin-library` currently mandates the header import row ("SHALL remain available"), which is why this change carries a delta instead of `skip_specs`.

Constraints carried from prior changes: detekt gate (`./gradlew detekt`) plus 4-version build per commit; stonecutter text rewrite matches the literal `.text(client.font,` — new/edited draw code keeps that shape; wire formats untouched here (no networking code involved).

## Goals / Non-Goals

**Goals:**
- Grid gains the header row's vertical space (~20px); title anchor unchanged.
- Ghost confirm/cancel buttons gone in every state (before, during, after a confirm dialog).
- Dead header code path and lang keys fully removed; detekt and builds stay green.

**Non-Goals:**
- No redesign of the add-skin overlay (it already covers all import paths).
- No changes to the file watcher import path (`addImportedEntry` stays).
- No band/confirm-dialog redesign — only the widget registration leak is fixed.

## Decisions

### D1 — Ghost buttons: fix by deregistering, not by visibility toggling

`band.confirmOverlayButton` / `band.cancelOverlayButton` are registered via `addRenderableWidget` (SkinLibraryScreen `initBandAndFooter`), so vanilla renders them every frame at (0,0) — stacked, Cancel on top — and never hides them. After a confirm dialog closes they keep their last centered position, clickable: a stray mid-screen click can fire `confirmCategoryDelete()`.

Fix: delete the two `addRenderableWidget` calls. Their only other consumers are already correct: `handleChromeClick` routes clicks while `confirmingDelete`, and `CategoryBand.drawConfirmOverlay` repositions and renders them manually. No visibility state machine to maintain; the buttons simply never render outside the overlay draw. Risk check: `refreshWidgets()` does not manage them (correct under the new model); no other file references them outside the band and the screen's confirm block.

### D2 — Header removal: overlay is the single import path

Capability parity was verified: the overlay already offers file import, account fetch (same `AccountSkinFetcher.fetch` + invalid flash), plus type switch, display name, rename and duplicate guard. What disappears is only the one-click-shorter path. Removal scope: `initHeaderRow`, `accountField`/`addFromFileButton`/`addFromAccountButton`, `addSkinFromFile`/`addSkinFromAccount`/`importSkinFile`, the `titleZoneLimit` clamp, and lang keys `add_from_file`/`add_from_account`/`account_name` (en + fr).

Layout: `contentTop()` drops from `HEADER_Y + HEADER_HEIGHT + 4` to the title-zone bottom; `gridTop` and everything derived from it follow automatically. Focus traversal references to `accountField` (keyPressed/children ordering) are removed with the field.

### D3 — Spec delta shape

MODIFIED requirement only (no ADDED/REMOVED): the add/delete requirement loses the header clause, the import scenario re-points to the overlay, and a "No ghost confirm controls" scenario pins the bug fix as observable behavior.

## Risks / Trade-offs

- [Quick import path lost] → accepted by the user; the overlay is one extra click and strictly more capable.
- [Confirm/cancel click routing regression] → in-game check: open delete confirmation, confirm and cancel both work; then verify nothing is clickable where the box used to be.
- [Focus/key handling dangling references after field removal] → compiler catches direct uses; in-game check of keyboard navigation on the library screen.
