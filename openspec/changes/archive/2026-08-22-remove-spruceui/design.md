# Design: Remove SpruceUI

## Context

See proposal.md — Why. SpruceUI usage is confined to three files (`SkinCarouselScreen`, `SkinCard`, `EdgeSafeButtonWidget`). Notably, the code already treats `SpruceGuiGraphics` as a pass-through: all actual drawing goes through `graphics.vanilla()` (`GuiGraphicsExtractor`), and widget lifecycle uses the vanilla `Screen.addRenderableWidget` API. Event signatures (`MouseButtonEvent`) are already vanilla — SpruceUI 26.x adopted them. Vanilla equivalents used elsewhere in the mod (`SkinWheelScreen`, `SkinPreviewButton`) prove the pattern works on both 1.21.11 and 26.x targets.

## Goals / Non-Goals

**Goals:**
- Zero `dev.lambdaurora.spruceui` / `dev.yumi` imports or dependency coordinates anywhere.
- Identical screens and interactions across all four targets (1.21.11, 26.1.2, 26.2, and the planned 26.3).
- Build plumbing simplified (no bundled deps, no regex workaround for `SpruceGuiGraphics`).

**Non-Goals:**
- Any redesign of the carousel/cards (layout, colors, interactions stay pixel- and behavior-identical where feasible).
- Touching YACL usage (config screen is a separate dependency, published promptly per MC version).
- Behavior-level spec changes.

## Decisions

### D1 — Widget mapping (mechanical, per-usage)

| SpruceUI usage | Vanilla replacement |
|---|---|
| `SpruceScreen` | `Screen` |
| `SpruceButtonWidget(Position.of(x,y), w, h, msg) { onPress }` | `Button(x, y, w, h, msg, onPress, …)` — constructor form varies across targets; wrap in `//?` if needed |
| `SpruceTextFieldWidget` + `setPlaceholder` | `EditBox` + `setHint` — the hint is rendered greyed when the field is empty, which replaces the `getTextColor()` placeholder override entirely |
| `getTextColor()` override (error state) | assign `EditBox.textColor` when `accountFieldShowingError` is set/restored |
| `setText` / `text` / `setChangedListener` | `setValue` / `getValue` / `setResponder` (names per target; conditionals where they differ) |
| `SpruceContainerWidget` + `addChild()` + `position.move(x,y)` | `AbstractWidget` + `ContainerEventHandler` with an own `List<EdgeSafeButtonWidget>`; children are offset in `overridePosition` (vanilla children are absolutely positioned; spruceui's were anchored) |
| `SpruceGuiGraphics` params | `GuiGraphics` (26.x) / `GuiGraphics` (1.21.11 via existing `GuiGraphicsExtractor` replacement) — the `.vanilla()` calls become direct calls |

### D2 — `EdgeSafeButtonWidget` is ported to vanilla `Button`, not deleted

Implementation finding (verified against decompiled 26.2 classes): the widget does *not* exist because of a SpruceUI limitation. Vanilla `AbstractButton.extractDefaultLabel` routes through `AbstractWidget.extractScrollingStringOverContents` → `ActiveTextCollector.acceptScrollingWithDefaultCenter` — the same unclamped-scissor path whose label render state crashes the frame in MC 26.2 (RenderPass scissor validation) when a button straddles a screen edge. Deleting the widget and using plain vanilla `Button` would reintroduce the crash the class was created to fix (see its docstring).

The port keeps the class and its clamped-ScissorStack label rendering unchanged, re-based onto vanilla `Button` (same pattern as the existing `SkinPreviewButton`): override `extractContents`/`renderContents` → `extractDefaultSprite`/`renderDefaultSprite` + the existing clamped label draw. Label color (`active ? white : grey`) replaces the SpruceUI-provided color argument.

### D3 — Build cleanup is part of the change

Remove `include(modImplementation("dev.lambdaurora:spruceui:…"))` and the yumi line from `build.gradle.kts`, the `deps.spruceui`/`deps.yumi` keys from every `stonecutter.properties.toml` block, and the `maven.gegy.dev` repository (it only served SpruceUI/Yumi).

The `GuiGraphicsExtractor` replacement in `stonecutter.gradle.kts` is **kept as-is** (implementation finding): its word-boundary regex with a no-op direct direction is not primarily about `SpruceGuiGraphics` — it is required because `GuiGraphics` is a substring of `GuiGraphicsExtractor`. Any plain-string rule corrupts one direction regardless of SpruceUI (verified: `replace("GuiGraphicsExtractor", "GuiGraphics")` rewrites VCS tokens on 26.x targets; the inverse form would explode `GuiGraphicsExtractor` into `GuiGraphicsExtractorExtractor`). With SpruceUI gone the regex simply guards the substring problem.

### D4 — `support-mc-26-3` artifacts are updated in the same change

The 26.3 change was planned around SpruceUI/Yumi being bundled (design D3 dev-override, task 3.1 override file, follow-up 5.1). With both deps gone, those become obsolete. Update its `proposal.md`/`design.md`/`tasks.md` as part of this change's docs step so the two changes stay coherent; the 26.3 target then compiles against plain Fabric API + YACL only.

## Risks / Trade-offs

- [`EditBox` hint rendering differs subtly from the custom `PLACEHOLDER_COLOR` (0xFF707070) text trick] → Accepted: `EditBox.setHint` renders the hint in its own fixed style when the field is empty; minor visual delta, and the per-field `getTextColor` overrides are gone.
- [Child event routing on the ported `SkinCard` diverges from `SpruceContainerWidget`] → Routing is written out explicitly (Kotlin cannot call Java interface default methods via qualified super): click iterates child buttons, drag/release forward to the focused child while pressed, otherwise drive the preview rotation. Focus traversal/narration stays widget-level (children are mouse-driven), covered by the smoke test.
- [1.21.11 vs 26.x vanilla widget API deltas add new `//?` conditionals] → Bounded: the mod already maintains exactly this pattern for vanilla classes, and removing SpruceUI *eliminates* a second, externally-paced rename surface.
- [Some SpruceUI convenience is silently load-bearing (e.g. text-field keyboard handling)] → Full manual smoke test checklist per spec scenarios before considering the change done.

## Migration Plan

None user-facing. Rollback = revert. The published jar loses two embedded jars; users who somehow relied on our bundle providing SpruceUI to *other* mods were depending on unsupported behavior.
