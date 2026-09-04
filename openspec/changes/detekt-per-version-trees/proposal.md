## Why

The detekt gate analyzes `src/main/kotlin` only — a single tree written in the newest-version flavor, where older-version variants live inside commented `//? /*...*/` blocks and stonecutter rewrites (`.text` → `.drawString`, `render` vs `extractRenderState`) never happen. Detekt is therefore blind to the code that actually ships on 1.21.11 / 26.1.2 / 26.2: a version-specific complexity blowup, a swallowed exception or dead code in an old-version branch would pass the gate unnoticed.

## What Changes

- Add a root-level `detektAll` verification task that runs the detekt CLI (same `gradle/detekt/detekt.yml` ledger, same JDK 21 JavaExec) over the four stonecutter-generated trees under `versions/<v>/build/generated/stonecutter/main/kotlin` (1.21.11, 26.1.2, 26.2, 26.3), in addition to the existing single-tree `detekt` task.
- Wire `detektAll` into the CI gate alongside `detekt`.
- Fix whatever the version trees surface, one commit per finding group, same discipline as `adopt-detekt-incrementally`.

## Capabilities

### New Capabilities

None — tooling only (`skip_specs: true`).

### Modified Capabilities

None.
