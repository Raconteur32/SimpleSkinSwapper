## 1. Dependency setup

- [x] 1.1 Add `deps.yacl` to each version block in `stonecutter.properties.toml` (`3.8.1+1.21.11-fabric`, `3.9.6+26.1-fabric`, `3.9.6+26.2-fabric`)
- [x] 1.2 In `build.gradle.kts`, read `deps.yacl` and add `modImplementation("dev.isxander:yet-another-config-lib:$yaclVersion")` plus the Xander Maven repository; declare `"yet_another_config_lib_v3": ">=3.8.1"` in `fabric.mod.json` `depends` (external dependency, not bundled — revised from jar-in-jar)
- [x] 1.3 Build all three Stonecutter versions to confirm YACL resolves, is on the dev runtime classpath, and is absent from the built jar

## 2. Model and conversion helpers

- [x] 2.1 Add a `ServerCommand(address, command)` model and map→list conversion (current server first, with an empty-command entry proposed when connected to an unconfigured server)
- [x] 2.2 Add list→map conversion for save (drop blank addresses and blank commands, last occurrence wins on duplicates)

## 3. YACL screen

- [x] 3.1 Implement the custom `ServerCommand` controller and its element widget (two text fields: address, command), verifying the API compiles on both YACL 3.8 and 3.9 and adding Stonecutter `//?` comments if they diverge
- [x] 3.2 Build the YACL screen: category + `ListOption<ServerCommand>` bound to the conversion helpers, save callback persisting via `SimpleSkinSwapperConfig.save()`
- [x] 3.3 Rewire `ModMenuCompat` to return the YACL screen and delete the old `gui/ConfigScreen.kt`
- [x] 3.4 Add a config button at the bottom of `SkinCarouselScreen`, to the right of the existing buttons, opening the YACL screen with the carousel as parent

## 4. Config cleanup

- [x] 4.1 Remove `SimpleSkinSwapperConfig.registerServerIfAbsent` and the `ClientPlayConnectionEvents.JOIN` hook in `SimpleSkinSwapperClient.kt`
- [x] 4.2 Update `en_us.json` and `fr_fr.json`: add the new `simpleskinswapper.config.*` keys (category, list label/description, field placeholders), remove obsolete `not_connected.*` keys

## 5. Verification

- [x] 5.1 Offline (title screen): list shows configured entries; add, edit, remove, and reorder work; save persists; restart client and confirm persistence
- [x] 5.2 Connected to a server: the current server is first in the list, proposed with an empty command when unconfigured; saving without filling it persists nothing for it
- [x] 5.3 Pruning: clear an existing entry's command, save, confirm it is gone from `simpleskinswapper.json`; confirm legacy empty entries are pruned on first save
- [x] 5.4 Regression: apply a skin on a configured server and confirm the command is still sent (SkinChangeManager path unchanged)
- [x] 5.5 Run the verification scenarios on at least one 26.x version and on 1.21.11
