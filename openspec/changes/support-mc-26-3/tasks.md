# Tasks: Support Minecraft 26.3

## 1. Register the 26.3 target

- [x] 1.1 Add `"26.3" to "26.3"` to `versions(...)` in `settings.gradle.kts` (keep `vcsVersion = "26.2"`).
- [x] 1.2 Add a `["26.3"]` block to `stonecutter.properties.toml`:
  - `deps.minecraft = "26.3-snapshot-9"`, `deps.fabric_loader = "0.19.3"`, `deps.fabric_api = "0.158.0+26.3"`
  - `deps.modmenu = "20.0.1"`, `deps.yacl = "3.9.6+26.3-fabric"`
  - `mod.version = "0.9.0-26.3"`, `mod.mc_dep = "~26.3-"`, `mod.loader_dep = ">=0.19.2"`

## 2. Build and fix API deltas

- [x] 2.1 Run `./gradlew :26.3:build`; wrap any 26.3 API breakage in `//?` conditionals (or add a replacement in `stonecutter.gradle.kts` for pure renames), per DEV.md conventions.
- [x] 2.2 Run `./gradlew build` — all four targets (`1.21.11`, `26.1.2`, `26.2`, `26.3`) pass.

## 3. Dev runtime

- [x] 3.1 `./gradlew :26.3:runClient` — the client starts on 26.3-snapshot-9. (On this machine: needs `SDL_VIDEODRIVER=x11` — the new SDL windowing fails EGL init on native Wayland; runs on Vulkan via XWayland.)
- [x] 3.2 Smoke test in dev client: skin carousel opens and scrolls, skin wheel opens, config screen (YACL) opens and edits persist, menu buttons appear on title/pause screens. ModMenu runs in the 26.3 dev runtime since 21.0.0-beta.1 (2026-09-14 refresh).
- [x] 3.3 Verify the built jar's `fabric.mod.json` (in `versions/26.3/build/libs/`) has `"minecraft": "~26.3-"`.

## 4. Docs

- [x] 4.1 Update `README.md` supported-versions line to include 26.3 (snapshot).
- [x] 4.2 Update `DEV.md`: version table row for `26.3`.
- [x] 4.3 Update the version list in the `.github/workflows/build.yml` comment.

## 5. Follow-up at 26.3 release (not part of this change's completion)

- [x] 5.0 Pre-release refresh: `deps.minecraft` → `26.3-rc-3`, fabric_loader → `0.19.5`, fabric_api → `0.160.4+26.3`, ModMenu → `21.0.0-beta.1` (declares 26.3-rc-1; now `modLocalRuntime` on every tree — the `< 26.3` guard is gone), YACL unchanged (3.9.6+26.3 still latest upstream — the inert-slider bug has no fix to pull). API drift snapshot-9 → rc-3: `Util.OS.openFile` moved to `Blaze3D.openPath(Path)` (chisel `>=26.3`).
- [ ] 5.1 When 26.3 releases: set `deps.minecraft = "26.3"`, switch active/VCS version to `26.3` in a separate commit (`Set active project to 26.3` + `vcsVersion`), update DEV.md table note.
