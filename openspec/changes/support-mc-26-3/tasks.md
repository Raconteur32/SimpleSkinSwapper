# Tasks: Support Minecraft 26.3

## 1. Register the 26.3 target

- [ ] 1.1 Add `"26.3" to "26.3"` to `versions(...)` in `settings.gradle.kts` (keep `vcsVersion = "26.2"`).
- [ ] 1.2 Add a `["26.3"]` block to `stonecutter.properties.toml`:
  - `deps.minecraft = "26.3-snapshot-9"`, `deps.fabric_loader = "0.19.3"`, `deps.fabric_api = "0.158.0+26.3"`
  - `deps.modmenu = "20.0.1"`, `deps.yacl = "3.9.6+26.3-fabric"`
  - `mod.version = "0.9.0-26.3"`, `mod.mc_dep = "~26.3-"`, `mod.loader_dep = ">=0.19.2"`

## 2. Build and fix API deltas

- [ ] 2.1 Run `./gradlew :26.3:build`; wrap any 26.3 API breakage in `//?` conditionals (or add a replacement in `stonecutter.gradle.kts` for pure renames), per DEV.md conventions.
- [ ] 2.2 Run `./gradlew build` — all four targets (`1.21.11`, `26.1.2`, `26.2`, `26.3`) pass.

## 3. Dev runtime

- [ ] 3.1 `./gradlew :26.3:runClient` — the client starts on 26.3-snapshot-9.
- [ ] 3.2 Smoke test in dev client: skin carousel opens and scrolls, skin wheel opens, config screen (YACL) opens and edits persist, menu buttons appear on title/pause screens.
- [ ] 3.3 Verify the built jar's `fabric.mod.json` (in `versions/26.3/build/libs/`) has `"minecraft": "~26.3-"`.

## 4. Docs

- [ ] 4.1 Update `README.md` supported-versions line to include 26.3 (snapshot).
- [ ] 4.2 Update `DEV.md`: version table row for `26.3`.
- [ ] 4.3 Update the version list in the `.github/workflows/build.yml` comment.

## 5. Follow-up at 26.3 release (not part of this change's completion)

- [ ] 5.1 When 26.3 releases: set `deps.minecraft = "26.3"`, switch active/VCS version to `26.3` in a separate commit (`Set active project to 26.3` + `vcsVersion`), update DEV.md table note.
