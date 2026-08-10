# Tasks: migrate-to-stonecutter

## 1. Kotlin DSL conversion (on `master`)

- [x] 1.1 Convert `settings.gradle` → `settings.gradle.kts` (pluginManagement repositories unchanged)
- [x] 1.2 Convert `build.gradle` → `build.gradle.kts` (deps, loom run config, processResources expansion, publishing, Java 25 config)
- [x] 1.3 Verify: `./gradlew build` produces an equivalent jar and `runClient` launches; commit as a standalone "build: migrate to Kotlin DSL" commit

## 2. Mojang mappings for 1.21.11 (on the `1.21.11` branch)

- [x] 2.1 Switch the 1.21.11 build from `yarn_mappings` to official Mojang mappings (loom `mappings loom.officialMojangMappings()`)
- [x] 2.2 Apply the Yarn→Mojang symbol rename across the 1.21.11 sources until it compiles
- [x] 2.3 Verify: 1.21.11 jar builds and runs; commit as a standalone "refactor: migrate 1.21.11 to Mojang mappings" commit

## 3. Kotlin source conversion (per branch, before any Stonecutter merge)

- [x] 3.1 Verify the available Kotlin version supports the Java 25 bytecode target; add the Kotlin Gradle plugin + `fabric-language-kotlin` to the Kotlin-DSL build
- [x] 3.2 On `master`: convert sources to Kotlin one file at a time (converter as scaffolding + immediate human review; `SkinRenderer` and other delicate files fully manual), keeping the 5 mixin/accessor classes in Java; build stays green after each file
- [x] 3.3 On the `1.21.11` branch (post-mojmap): same conversion, same rules
- [x] 3.4 Declare `fabric-language-kotlin` in `fabric.mod.json` on both branches
- [x] 3.5 Verify: both branches build and run (wheel, carousel, config screen); commit as standalone "refactor: convert sources to Kotlin" commits, one per branch

## 4. Stonecutter restructure (on a working branch off `master`)

- [x] 4.1 Add the Stonecutter plugin to `settings.gradle.kts` and register version keys `1.21.11`, `26.1.2`, `26.2`
- [x] 4.2 Create `versions/<key>/gradle.properties` for each version (minecraft/loader/fabric versions, spruceui/yumi/modmenu/language-kotlin coordinates, Java toolchain 21 vs 25); slim the root `gradle.properties` to shared values
- [x] 4.3 Adapt `build.gradle.kts` to the Stonecutter model (shared script consuming per-version properties, per-version `fabric.mod.json` constraint expansion)
- [x] 4.4 Merge the 26.1 source state into the shared `src/`, wrapping the 26.1↔26.2 deltas (screen open/close calls, `fabric.mod.json` constraint) in `//?` conditionals; verify `:26.1.2:build` and `:26.2:build`
- [x] 4.5 Merge the (Mojang-mapped, Kotlin) 1.21.11 source state into the shared `src/`, wrapping the real API deltas (rendering pipeline in `SkinRenderer`, scissor stack, picture-in-picture registration, any mixin target differences) in `//?` conditionals; verify `:1.21.11:build`
- [x] 4.6 Set 26.2 as the active/VCS version and commit the source in that state
- [x] 4.7 Verify: all-versions build (`chiseledBuild` or per-version `build`) produces all three jars; spot-check each jar in a client (wheel, carousel, config screen, multiplayer refresh)

## 5. Cutover

- [x] 5.1 Start tracking `openspec/` in git on the main branch
- [x] 5.2 Merge the working branch into `master`; freeze `1.21.11` and `26.1` branches (no deletion, no further commits)
- [x] 5.3 Add CI that builds every version subproject on push (IDE only checks the active version)
- [x] 5.4 Update `README.md` (supported versions / build instructions, new `fabric-language-kotlin` dependency) and `.gitignore` if needed
