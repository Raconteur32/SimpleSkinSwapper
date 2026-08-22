# Support Minecraft 26.3 (snapshot cycle → release)

## Why

Minecraft 26.3 is in its snapshot cycle (latest: `26.3-snapshot-9`). Adding a Stonecutter target now lets us catch API deltas early and ship day-one when 26.3 releases, instead of scrambling after the fact. Fabric API (`0.158.0+26.3`) and YACL (`3.9.6+26.3-fabric`) already publish for 26.3.

## What Changes

- New Stonecutter version key `26.3` in `settings.gradle.kts` and `stonecutter.properties.toml`, built against `26.3-snapshot-9` initially; `deps.minecraft` bumps to `26.3` at release (single-line change, same pattern as the `26.2-rc-2 → 26.2` cycle).
- `mod.mc_dep = "~26.3-"` in the new version block — the trailing `-` includes snapshots/pre-releases and the final release, so the published jar stays loadable when 26.3 ships.
- Any new 26.3 API deltas wrapped in `//?` conditionals (or a replacement entry if pure renames).
- Docs: `README.md` supported-versions line and `DEV.md` version table.

The active/VCS version stays `26.2` (project policy: latest *drop* is active; a snapshot is not a drop). It switches to `26.3` at release, as a separate commit.

## Capabilities

No spec-level behavior changes — this is build tooling (new build target, dependency coordinates). Same category as the archived `migrate-to-stonecutter` change, which also set `skip_specs: true`.

## Impact

- **Build config**: `settings.gradle.kts`, `stonecutter.properties.toml`, possibly `stonecutter.gradle.kts` (new replacements if 26.3 renames symbols).
- **Source**: only if 26.3 snapshots changed APIs the mod uses (`//?` conditionals in `src/`).
- **Docs**: `README.md`, `DEV.md`, `.github/workflows/build.yml` comment.
