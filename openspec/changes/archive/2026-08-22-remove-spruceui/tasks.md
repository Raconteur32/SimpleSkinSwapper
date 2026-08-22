# Tasks: Remove SpruceUI

## 1. Port SkinCarouselScreen to vanilla widgets

- [x] 1.1 Base class `SpruceScreen` → `Screen`; `SpruceGuiGraphics` params → vanilla graphics type per target (existing `//? if >=26.1` pattern for `extractRenderState`/`render`).
- [x] 1.2 Replace the 7 `SpruceButtonWidget` constructions with vanilla `Button` (`Button.builder(...).bounds(x,y,w,h).build()` — identical form on all targets).
- [x] 1.3 Replace `searchField`: `EditBox` with `setHint` (drops the `getTextColor` placeholder override), `setValue(searchQuery)`, `setResponder(this::onSearchChanged)`.
- [x] 1.4 Replace `accountField`: `EditBox` with `setHint`; error state via `setTextColor(ERROR_COLOR)` set/restored where `accountFieldShowingError` flips; responder toggles `addFromAccountButton.active`.
- [x] 1.5 Replace `graphics.vanilla().centeredText/fill` calls with direct calls; drop `Position.of` usages.

## 2. Port SkinCard to vanilla container

- [x] 2.1 Base class `SpruceContainerWidget` → `AbstractWidget` + `ContainerEventHandler` (own child list; `overridePosition` offsets children — vanilla children are absolutely positioned, spruceui's were anchored).
- [x] 2.2 Replace the 6 `EdgeSafeButtonWidget` constructions with the re-based vanilla `Button` subclass (same onPress handlers; `active`/`visible` field semantics verified against vanilla `AbstractWidget`).
- [x] 2.3 Adapt rendering overrides (background folded into `extractWidgetRenderState`/`renderWidget`, children rendered via `extractRenderState`/`render` loop), keeping scissored name text and rotatable preview identical.
- [x] 2.4 Event routing written out explicitly (Kotlin cannot call Java interface default methods via qualified super): click → children then preview rotation; drag/release → focused child while pressed, else preview rotation. Focus/narration stay widget-level.

## 3. Re-base EdgeSafeButtonWidget and clean the build

- [x] 3.1 ~~Delete `gui/EdgeSafeButtonWidget.kt`~~ → **revised during implementation** (design D2): vanilla `Button`'s default label path uses the same unclamped scissor the class protects against — the class is re-based onto vanilla `Button` (override `extractContents`/`renderContents`), label rendering unchanged.
- [x] 3.2 Remove `include(modImplementation("dev.lambdaurora:spruceui:…"))` and the `dev.yumi.mc.core:yumi-mc-foundation` line from `build.gradle.kts`; remove the now-unused version properties.
- [x] 3.3 Remove `deps.spruceui` and `deps.yumi` from every block in `stonecutter.properties.toml`.
- [x] 3.4 Remove the `maven.gegy.dev` repository (it only served SpruceUI/Yumi).
- [x] 3.5 ~~Simplify the `GuiGraphicsExtractor` regex~~ → **revised during implementation** (design D3): not possible — the word-boundary regex + no-op direct direction is required because `GuiGraphics` is a substring of `GuiGraphicsExtractor`, independent of SpruceUI. Rule kept as-is (comment updated).

## 4. Verify on all targets

- [x] 4.1 `./gradlew build` passes for all targets (1.21.11, 26.1.2, 26.2).
- [x] 4.2 `runClient` smoke test on 26.2 against the `skin-carousel` spec: scroll (wheel + scrollbar drag), off-screen cards not rendered/interactive, reorder arrows, delete with confirm, search filter (reorder disabled while filtering), add-from-file, add-from-account (success + invalid-account error color), 3D preview drag-to-rotate.
- [x] 4.3 Smoke test `menu-button`, `skin-wheel`, `config-screen` specs (regression check — untouched but same runtime).
- [x] 4.4 Grep confirms zero `spruceui`/`lambdaurora`/`yumi` references in `src/`, `build.gradle.kts`, `stonecutter.properties.toml`.

## 5. Docs and cross-change coherence

- [x] 5.1 Update `README.md` (no SpruceUI mention found) and `DEV.md` (dependency list + replacement-rule guidance updated).
- [x] 5.2 Update `openspec/changes/support-mc-26-3` artifacts: removed design D3 (dev-override), task 3.1 (override file) and follow-up 5.1 (SpruceUI/Yumi bump); the 26.3 target then only needs Fabric API + YACL + ModMenu coordinates.
