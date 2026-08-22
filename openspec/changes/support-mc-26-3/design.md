# Design: Support Minecraft 26.3

## Context

See proposal.md — Why. Current state: three Stonecutter targets (`1.21.11`, `26.1.2`, `26.2`), with `26.2` as active/VCS version. Per-version coordinates live in `stonecutter.properties.toml`; `mod.mc_dep` is expanded into `fabric.mod.json` during `processResources`. Verified availability for `26.3-snapshot-9` (2026-08-22):

| Dep | Coordinate for 26.3 | Notes |
|---|---|---|
| Minecraft | `26.3-snapshot-9` | latest snapshot (Fabric meta) |
| Loader | `0.19.3` | same as 26.2 |
| Fabric API | `0.158.0+26.3` | published during the snapshot cycle |
| YACL | `3.9.6+26.3-fabric` | published |
| ModMenu | `20.0.1` | compileOnly + local runtime |

The `remove-spruceui` change (applied before this one) removed the only bundled, version-pinned dependencies (SpruceUI/Yumi). Every remaining dependency publishes for 26.3, so nothing blocks the 26.3 target — dev or published.

## Goals / Non-Goals

**Goals:**
- `./gradlew :26.3:build` and `:26.3:runClient` work against `26.3-snapshot-9`.
- The produced jar declares a Minecraft constraint that covers snapshots, pre-releases, and the 26.3 release.
- Zero changes to the other three targets' builds or behavior.

**Non-Goals:**
- Switching the active/VCS version (stays `26.2` until the 26.3 release).
- Tracking every new snapshot; `deps.minecraft` only moves forward if a later snapshot breaks the build.

## Decisions

### D1 — Version key is `26.3`, not `26.3-snapshot-9`

`settings.gradle.kts` registers `26.3`, and `deps.minecraft = "26.3-snapshot-9"` carries the snapshot coordinate. This is exactly the `26.2-rc-2 → 26.2` cycle the project already ran: at release, one line flips to `deps.minecraft = "26.3"` and nothing else changes. It also keeps Stitcher conditionals clean — `//? if >=26.2` etc. parse the key, and `26.3` compares sanely, whereas a `26.3-snapshot-9` key would need to be renamed (and every conditional re-verified) at release.

*Alternative considered:* key `26.3-snapshot-9` — rejected: rename churn at release and uglier version comparisons.

### D2 — `mod.mc_dep = "~26.3-"`

Fabric's semver: `26.3-snapshot-9` is a pre-release of `26.3`. The range `~26.3-` (>= 26.3 including pre-releases, < 26.4) therefore matches snapshot-9 today **and** the final `26.3` release — one constraint for the whole cycle. This mirrors the existing `26.2` block, which uses `~26.2-` for the same reason (commit history: "relax minecraft constraint to ~26.2- to include pre-releases").

### D4 — Active version stays `26.2`

Project policy (DEV.md): the latest *drop* is the active/VCS version. A snapshot is not a drop. The IDE keeps type-checking 26.2; the 26.3 target is validated by `./gradlew build` and CI, which build all targets anyway. At 26.3 release, a separate commit flips `stonecutter active "26.3"` / `vcsVersion` (and `Reset active project` discipline applies).

### D5 — Follow-up on release is tracked as an explicit task

When 26.3 releases: bump `deps.minecraft` to `26.3` and switch the active/VCS version. Documented in `tasks.md` as a follow-up section so it isn't forgotten, and in `DEV.md`'s "Adding support for a new Minecraft drop" checklist.

## Risks / Trade-offs

- [A later 26.3 snapshot breaks the build before release] → Accepted; we pin `26.3-snapshot-9` and bump deliberately rather than chasing snapshots.
- [26.3 snapshots changed a vanilla GUI API the mod uses] → The `:26.3:build` + dev client smoke test (carousel, wheel, config screens) catches this; deltas are wrapped in `//?` conditionals per project convention.

## Migration Plan

No user-facing migration. Rollback = revert the commit; other targets are untouched.
