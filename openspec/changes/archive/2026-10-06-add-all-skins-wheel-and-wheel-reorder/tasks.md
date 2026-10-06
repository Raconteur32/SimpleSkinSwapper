## 1. Config foundation

- [x] 1.1 Add `AllSkinsWheelMode` enum (NEVER, FALLBACK, ALWAYS) and two fields to `SimpleSkinSwapperConfig` — `allSkinsWheelMode` default FALLBACK, `maxAllSkinsWheels` default 2 clamped to 1..5 on load — and verify a fresh/legacy config file loads with the defaults (existing `applyDefaults`/`load` defensive path)
- [x] 1.2 Add the two options to `YaclConfigScreen` in the skin-wheel group (enum cycle like `ButtonSide`, int slider like `minCardWidth` with bounds 1..5) and add the `simpleskinswapper.config.*` labels + tooltips to `en_us.json` and `fr_fr.json`; verify the options render, cycle/slide, save, and reload with persisted values

## 2. Wheel groups and all-skins composition

- [x] 2.1 Introduce the wheel-owner sealed type (category | all-skins) and rewrite `buildWheels()`/composition as a group-based function per design D1: Always = all-skins (registry truncated to cap × 10) then categories; Fallback = categories, else whole registry; Never = categories only; verify each mode by launching with categories present/absent and checking the wheel sequence and pagination counter
- [x] 2.2 Extend pagination dots/tooltip for the all-skins group (neutral color distinct from dyes, "All skins" tooltip, dot click jumps to the group's first wheel) and verify with Always + one category that dots read neutral/neutral/blue and tooltips are correct
- [x] 2.3 Verify the empty state only appears when nothing contributes (Never without categories; Fallback with empty registry) and that `lastWheelPosition` clamping holds when the mode change shrinks the sequence

## 3. Radial reorder engine

- [x] 3.1 Create a version-free reorder engine (design D3): group flat list, dragged entry removed, angle-around-center → nearest filled slot mapping, insertion index derivation, eased angular targets for the remaining sectors; cover the mapping and insertion math with plain JVM unit tests (`./gradlew test`) including sparse wheels and filler exclusion
- [x] 3.2 Wire the dragged-sector rendering: radial outward offset of the sector mesh + live preview behind the existing version guards, neighbors easing to their reflowed angles; verify visually on all four stonecutter targets that the pulled-out slice and dynamic reflow render correctly

## 4. Input state machine

- [x] 4.1 Add the IDLE/PENDING/REORDER machine to `SkinWheelScreen`: press on a filled sector at rest → PENDING with the elastic radial stretch (outward-projection offset, dampened and capped, no angular follow, inward pull inert), release under the pull distance → apply (unchanged), pull past the distance → REORDER; verify the click-applies, elastic-stretch and drag-reorders paths do not interfere with pagination-dot presses
- [x] 4.2 Implement REORDER targeting (mouse angle retargets the slot, target highlight) and rest-gated click commit; verify the target follows the mouse around the wheel and a click lands the skin at the highlighted slot

## 5. Navigation, cancel and commit rules

- [x] 5.1 Implement the group rules: scrolling and dot clicks keep the mode within the same group and cancel with ease-back across groups; verify same-category cross-wheel placement and category→category cancel
- [x] 5.2 Implement cancellation paths: G release (cancel + close), right-click (cancel, stay open), escape (cancel first, close second); verify after each cancel the displayed order is exactly the pre-drag order
- [x] 5.3 Implement commits: category group → remove+insert in `category.cards` + `SkinCategories.save()`; all-skins group → `SkinRecords.moveBefore`/`moveAfter` with the target-slot pivot; verify in-game that library position numbers, allocation markers and the All skins view follow, and that orders survive a restart

## 6. Verification

- [x] 6.1 Extend the store-level JVM tests for wheel-commit semantics (category window reorder, registry relative move) and run the full suite via `./gradlew test`
- [x] 6.2 Run the detekt gate and the full build across all stonecutter targets, and walk the changed spec scenarios manually (Fallback substitution, Always cap, commit persistence, cancel paths) before requesting the code review
