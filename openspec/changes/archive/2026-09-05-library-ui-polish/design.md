## Context

The category "+" is a vanilla `Button` at `(STRIP_X, height-24)` (SkinLibraryScreen.kt init) — visually alien and positionally tied to the footer row. The footer holds three 110px vanilla buttons (open folder, config, done) centered on the panel at `footerY = height-24`, while the card grid's `gridBottom` reaches into that row. The default category name uses `Component.translatable("simpleskinswapper.screen.library.add_category")` and the key is absent from en_us/fr_fr, so players see the raw key. The tab strip already draws tabs itself (recipe-book sprites, scroll, hover) and routes clicks through its own hit-test (`tabs.tabAt`), so a non-widget strip entry has an established pattern to follow. Category creation currently lives behind `SkinCategoriesStore.addCategory(name, colorHex)` and nothing looks categories up by name (all index-based), so a duplicate-name guard is a pure nicety.

## Goals / Non-Goals

**Goals:**
- Category creation looks native to the strip and lives at its end.
- The footer owns its row: grid stops above it, three buttons spread across the panel.
- "New Category" default, incremented, with real lang keys.

**Non-Goals:**
- No dedup enforcement on user renames (names are labels, not keys).
- No restyle of the footer buttons themselves (vanilla buttons stay).
- No change to the add-skin flow or the band.

## Decisions

### D1 — The add-category entry is drawn by the tab strip, not a widget

Same approach as the deletion confirm buttons: rendered by the strip's own draw pass with the strip's sprite background, outlined `#999999` (brightened on hover), "+" centered; hit-tested by extending the strip's hit-test (a query like `addEntryAt(x, y)` alongside `tabAt`) rather than `addRenderableWidget`. This kills the vanilla look and keeps the entry scrolling/clipping with the strip for free. Click routes to the existing create-select-open-band flow.

### D2 — The footer row is reserved before laying out the grid

Lift `gridBottom` above a fixed footer band (footer height + margin) so the grid can never encroach, then spread the three 110px buttons across the panel width (space-between: open folder left, config center, done right). Buttons keep vanilla styling and 20px height; the GUI-scale requirement's "no overlap" clause stays satisfied by construction rather than by the current coincidental centering.

### D3 — Default name via restored lang key + index-free increment

Restore `simpleskinswapper.screen.library.add_category` as "New Category" (fr: "Nouvelle catégorie"). The increment scans live category names for the smallest suffix: base name when free, else "New Category 2", "New Category 3", … Increment happens at the creation call site (screen), not in the store — the store keeps its dumb `addCategory(name, colorHex)` contract. Renames stay unpoliced per the spec.

## Risks / Trade-offs

- [Hit-test divergence between draw and click geometry] → single source: both derive from the same per-tab metrics the strip already uses; the entry is just the next slot.
- [Tight footer at small GUI scales] → the reserved band is fixed-height (20px buttons + margins); the GUI-scale requirement already mandates minimums — verify in game at the auto scale.
- ["New Category" collisions via rename then create] → increment scans current live names at creation time; a later rename into "New Category" can still duplicate, accepted (labels, not keys).
