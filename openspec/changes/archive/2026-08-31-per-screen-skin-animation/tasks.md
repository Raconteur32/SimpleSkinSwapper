## 1. Renderer API

- [x] 1.1 Change `SkinRenderer.buildRenderState` to take a `limbSwingIntensity: Float` parameter (drop the boolean + config default) and derive `walkAnimationSpeed = AMPLITUDE * intensity`, `walkAnimationPos = TOTAL_TICK_DELTA * SPEED_FACTOR` when intensity > 0; thread the parameter through `renderPlayer`, `renderPlayerFollowingMouse`, `renderPlayerRotatable`. Verify: `./gradlew compileKotlin` fails only on call sites still using the old signature.

## 2. Config rename

- [x] 2.1 Rename `enableMovingLegs` to `animateMenuPreview` in `SimpleSkinSwapperConfig` (default true) and update `YaclConfigScreen` tickbox binding; update EN/FR lang keys (`simpleskinswapper.config.animate_menu_preview[.description]`) with labels stating menu-preview scope and arms+legs coverage. Verify: JSON keys valid (`python3 -m json.tool`), config screen shows the renamed option.

## 3. Menu previews

- [x] 3.1 `SkinPreviewButton` passes `1.0f`/`0.0f` intensity read from `animateMenuPreview`. Verify: with option off, title/pause previews hold a static pose; with on, they animate.

## 4. Carousel hover animation

- [x] 4.1 `SkinCard`: add `hoverAnimFactor` eased toward 1/0 with nano-time delta (`k ≈ 10`, snap epsilon like `updateSpringBack`), computed from `isMouseOverCard(mouseX, mouseY)` each render pass; pass as intensity to `renderPlayerRotatable`. Verify: hover animates a card, others stay static; leaving a card settles limbs smoothly (~0.4 s); drag-rotate + spring-back unchanged.
- [x] 4.2 Feedback fix: lock the animation to the drag-rotated card — while a card is being drag-rotated its factor stays 1 wherever the cursor is, and other cards' factors ease to 0 (screen tracks the rotating card). Verify: during a drag the dragged skin keeps animating with the cursor outside the card, hovered cards stay static, hover animation resumes on release.

## 5. Wheel: live rendering + hover animation

- [x] 5.1 `SkinWheelScreen`: replace the hovered-live/others-baked logic in `drawSectorPreview` with live `renderPlayer` for every sector, passing an eased per-sector intensity driven by `selectedIndex == index`. Verify: all previews render live; hovered sector animates; previous sector settles smoothly when hover moves.
- [x] 5.2 Delete `SkinPreviewCache.kt`, `GuiRendererMixin.java`, and the mixin entry in `simpleskinswapper.mixins.json`; remove now-unused imports. Verify: grep finds no `SkinPreviewCache` references; `./gradlew compileKotlin` succeeds on all versions.

## 6. Verification

- [x] 6.1 Full build on all four versions (`./gradlew build`) succeeds with zero Kotlin warnings. Verify: BUILD SUCCESSFUL, no `w: file:` lines.
- [x] 6.2 Manual pass on 26.3 dev client: menu preview follows config option; carousel hover animation + smooth settle + drag; wheel always live, hover animates, click applies, no drag. Verify: behaviors observed in `runClient`.
