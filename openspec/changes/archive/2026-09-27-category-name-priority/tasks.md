# Tasks — category-name-priority

## 1. Name model split

- [x] 1.1 Replace `SkinEntry.displayNameOverride` with `globalName: String?` and `categoryName: String?`; `displayName` resolves `categoryName ?: globalName ?: baseName`; `fromRecord` seeds `globalName` only. Update the 5 usage sites (`rg displayNameOverride`): `reloadView` sets `globalName` from the record in every view and `categoryName` from the card in category views; card rendering keeps reading `displayName`. Verify: `rg displayNameOverride src/` returns nothing and `./gradlew compileKotlin` passes.
- [x] 1.2 Confirm no writer sets `categoryName` outside the category branch of `reloadView` (wheel screen and derived views keep it null). Verify: `rg categoryName src/main/kotlin` shows only the entry fields, `reloadView`, and the panel (task 2).

## 2. Detail panel seeding and previews

- [x] 2.1 In `SkinDetailPanel`, seed the display field from `entry.globalName` and the category field from `entry.categoryName` in both `refreshFields` and `rebind`; make each responder write back only its own slot, dropping the registry fallback in the category responder. Verify: `./gradlew compileKotlin` passes and no `SkinRecords` lookup remains in the responders.
- [x] 2.2 Check the commit path stays the only writer on close/apply: a no-op open/close calls `commitEntryNames` with the two seeded values (identical re-write, no visible change). Verify: code read-through against the delta spec's "Closing the detail overlay without edits commits nothing" scenario.

## 3. Unit coverage of the resolution chain

- [x] 3.1 Add a headless JUnit test for the name resolution: category name wins over global name, global name wins over file base name, null categoryName falls through (plain `SkinEntry(File)` constructor with a tiny PNG, like the existing library tests' `tinyPng`). If the Minecraft-typed field blocks headless class loading, extract the chain into a pure helper and test that instead. Verify: `./gradlew test` passes with the new test.

## 4. In-game verification (delta spec scenarios)

- [x] 4.1 Walk the four delta scenarios in game: category name shows only in its category view; global name (or base name) applies when no category name is set; editing only the category name leaves the global name intact and vice versa. Verify: each scenario observed in game.
- [x] 4.2 Re-run the confirmed-bug repro: open the detail overlay on a skin whose card already has a category name, in that category's view, close with ESC without editing, and confirm the All skins view still shows the original global name. Verify: repro no longer reproduces.

## 5. Quality gates

- [x] 5.1 Run the full build with detekt and the konsist architecture tests. Verify: `./gradlew build` passes clean.
