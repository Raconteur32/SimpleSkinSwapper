# Gradle 10 compatibility

## Why

Every build prints "Deprecated Gradle features were used in this build, making it incompatible with Gradle 10." The 2026-09-26 audit (Problems Report, `--rerun-tasks` run) traced exactly **one** deprecation: `Task.project` invoked at execution time by `:processResources` (`expand("version" to project.version, ...)` inside `filesMatching` — the closure runs during execution). Gradle states it will fail with an error in Gradle 10. Wrapper today: 9.5.1.

## What Changes

- Fix `processResources`: use the script-level `modVersion` val (identical value, captured at configuration time) instead of `project.version` inside the execution-time closure.
- Upgrade the wrapper to the latest Gradle 10 release, then shake the whole tree: build ×4, detektAll, test, and stonecutter generation. Fix whatever our scripts break; plugin compatibility (loom 1.15, loom-back-compat 0.4.2, stonecutter 0.9.7, kotlin 2.4.10, foojay 1.0.0) is verified by the same run — if a plugin fails on Gradle 10, the wrapper bump waits in a pinned state and the finding is recorded here.
- Success signal for the baseline fix: the "incompatible with Gradle 10" summary line disappears from builds on 9.5.1.

## Capabilities

### New Capabilities

(none — build tooling)

### Modified Capabilities

(none — `skip_specs: true`)

## Impact

- `build.gradle.kts` (one line), `gradle/wrapper/gradle-wrapper.properties` (+ wrapper jar), possibly small script fixes surfaced by Gradle 10.
- Rollback per step: revert the wrapper commit independently of the deprecation fix.
- Cost: 1 h if plugins follow; otherwise pinned state with findings documented.
