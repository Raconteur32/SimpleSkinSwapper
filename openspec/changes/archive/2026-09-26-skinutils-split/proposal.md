# Split SkinUtils — the GPU texture service leaves gui

## Why

Atlas open question #5: `changeskin → gui.SkinUtils` is the last `gui` import from the core — but it is not a UI dependency. `StartupSkinSync` calls `loadSkinTextureAsync`, which registers a GPU texture (`DynamicTexture` via `Minecraft.textureManager`): rendering *infrastructure*, not interface. Meanwhile `SkinUtils` also carries two unrelated responsibilities (pure model detection, a geometry helper). The mixed bag is why the map shows the core depending on "the GUI".

## What Changes

- New root-package object **`SkinTextureLoader`**: the five GPU functions move verbatim — `loadSkinTexture`, `loadSkinTextureAsync`, and the private `remapTexture`/`stripAlpha`/`copyMirroredLimb`.
- `SkinUtils` keeps the two non-GPU helpers (`inRect`, `detectSkinType`) — GUI-only consumers, stays in `gui`.
- Callers updated: `StartupSkinSync` (changeskin), `SkinEntry`, `SkinAddPanel` (gui).
- Konsist layering map: the `changeskin` allowlist drops `gui.SkinUtils` — **zero gui exceptions remain**; any future `gui` import from the core fails the suite.
- Atlas refresh: §1 back-edge closed, §5 #5 closed.

Non-goal: dissolving `SkinUtils` entirely (its two remaining helpers are GUI-scoped and stay); a `render` package to group `SkinRenderer`/`SkinPreviewCache` later — noted as possible follow-up if more rendering infrastructure emerges.

## Capabilities

### New Capabilities

(none — pure refactor, no runtime behavior change)

### Modified Capabilities

(none — `skip_specs: true`)

## Impact

- One new file (~90 lines moved verbatim), three import updates, one Konsist map line, atlas rows.
- Chosen over a `render` package for the same reason `SkinType` went to the root (layering-cleanup D1): one object does not justify a new package; revisit if rendering infrastructure accumulates.
- Cost: ~1 h. Risk: near zero — code moves verbatim; gates ×4 + a quick visual check (startup preview, add-staging preview) at convenience.
