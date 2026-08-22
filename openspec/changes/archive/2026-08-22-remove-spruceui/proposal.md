# Remove SpruceUI (and Yumi) dependency

## Why

SpruceUI is a bundled, per-MC-version-pinned dependency (`~26.x-` in its `fabric.mod.json`) that blocks every new snapshot cycle until its author publishes — exactly the blocker identified for 26.3. Yet the mod only uses it as a basic widget toolkit: everything SpruceUI provides (`SpruceScreen`, `SpruceButtonWidget`, `SpruceTextFieldWidget`, `SpruceContainerWidget`) has a direct vanilla equivalent, and all rendering already escapes to `graphics.vanilla()`. Yumi is bundled only as SpruceUI's runtime dependency and is never imported in our code.

## What Changes

- Port `SkinCarouselScreen` from `SpruceScreen`/`SpruceTextFieldWidget`/`SpruceButtonWidget` to vanilla `Screen`/`EditBox`/`Button` (placeholder via `EditBox.setHint`, error color via `EditBox.textColor`, listeners via `setResponder`).
- Port `SkinCard` from `SpruceContainerWidget` to vanilla `AbstractWidget` + `ContainerEventHandler` (`addChild` → own child list + explicit event routing, `position.move()` → `setX/setY` with child offsetting).
- Re-base `EdgeSafeButtonWidget` onto vanilla `Button` (it is **not** deletable: vanilla's default label path uses the same unclamped scissor that crashes in 26.2 — the class protects against vanilla behavior, not SpruceUI's).
- Remove `deps.spruceui` and `deps.yumi` from `build.gradle.kts` (`include(modImplementation(...))` × 2), `stonecutter.properties.toml` (all version blocks), and the `maven.gegy.dev` repo if unused afterwards.
- Simplify `stonecutter.gradle.kts`: drop the regex-boundary workaround protecting `SpruceGuiGraphics` from the `GuiGraphicsExtractor` replacement (no SpruceUI symbols remain to protect).
- Keep `support-mc-26-3` coherent: once this lands, its dev-override strategy (D3) and follow-up task (5.1) become obsolete — its artifacts are updated accordingly.

No user-visible behavior change intended: same screens, same widgets, same interactions.

## Capabilities

No spec-level behavior changes — this replaces the widget toolkit behind identical screens. `skip_specs: true`, same category as `migrate-to-stonecutter` and `support-mc-26-3`. Existing specs (`skin-carousel`, `menu-button`, `skin-wheel`, `config-screen`) describe behavior that must remain true; they serve as acceptance criteria.

## Impact

- **Source**: `gui/SkinCarouselScreen.kt`, `gui/SkinCard.kt`, `gui/EdgeSafeButtonWidget.kt` (re-based onto vanilla `Button`).
- **Build**: `build.gradle.kts`, `stonecutter.properties.toml`, `stonecutter.gradle.kts`.
- **Planning**: `openspec/changes/support-mc-26-3/` artifacts simplified (no more bundled-dep override).
- **Behavioral acceptance**: all four existing specs must still hold — especially `skin-carousel` (off-screen culling, scrolling, reorder, delete) and `config-screen`.
