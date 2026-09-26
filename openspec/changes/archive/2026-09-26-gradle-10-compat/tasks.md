# Tasks: gradle-10-compat

## 1. Zero-deprecation baseline on 9.5.1

- [x] 1.1 `processResources`: replace `project.version` with the script-level `modVersion` val in the `filesMatching` expand call. Verify: full `--rerun-tasks` build on 9.5.1 shows no "incompatible with Gradle 10" summary and the Problems Report is empty. CONFIRMED: summary gone; Gradle no longer generates the problems report at all (it is only written when problems exist).
- [x] 1.2 Commit the baseline fix.

## 2. Wrapper upgrade

- [x] 2.1 Bump the wrapper to the latest Gradle 10 release (`./gradlew wrapper --gradle-version <10.x>`); run the full gate (`build detektAll test --rerun-tasks`) and fix script breakage attributable to our code. Verify: full gate green on Gradle 10; record plugin verdicts (loom, stonecutter, loom-back-compat, kotlin, foojay) in this file. FINDING (2026-09-26): Gradle 10 is NOT released yet — no 10.x stable, no 10.x RC listed on services.gradle.org (current stable: 9.8.0). Wrapper bumped to 9.8.0 (latest 9.x) instead; full gate green on first run: loom 1.15.5, loom-back-compat 0.4.2, stonecutter 0.9.7, kotlin 2.4.10, foojay 1.0.0 all fine.
- [x] 2.2 If a third-party plugin blocks Gradle 10: pin back to 9.5.1, keep task 1, record the blocker and the plugin version needed here. Verify: repo builds green on the pinned wrapper, decision documented. N/A — no plugin blocker; the only blocker is the missing Gradle 10 release. Re-run this task when 10.0.0 ships (the repo is zero-deprecation, so it should be a one-line wrapper bump).

## 3. Wrap-up

- [x] 3.1 DEV.md note if the wrapper version policy changes; final commit. Verify: clean tree, gate green. N/A — no wrapper policy change to document (9.8.0 is a routine update).
