## Why

Four GUI functions still carry `@Suppress("CyclomaticComplexMethod")` — deferred debt from `adopt-detekt-incrementally` and `extract-shared-structure` because each split needed its own in-game validation. They are the last suppressions in the codebase (besides the three justified `TooGenericExceptionCaught`), and they sit on the highest-traffic code paths (click routing, card layout, card/wheel rendering), where complexity is hardest to reason about.

## What Changes

- Split `SkinLibraryScreen.mouseClicked` into overlay routing + chrome (confirm overlay, tabs, band, empty-category) + super dispatch; retire its suppression.
- Split `SkinLibraryScreen.updateCardPositions` into card easing + add-card easing helpers sharing one clip/ease helper; retire its suppression.
- Split `SkinLibraryCard.extractWidgetRenderState` into clip guard, chrome, header and preview draw helpers; retire its suppression.
- Split `SkinWheelScreen.extractRenderState` into empty-state and pagination draw helpers; retire its suppression.
- No behavior change: pure mechanical extraction, expressions moved verbatim within the same file (stonecutter rewrite shapes like `.text(client.font,` preserved).

## Capabilities

### New Capabilities

None — internal refactoring only (`skip_specs: true`).

### Modified Capabilities

None.
