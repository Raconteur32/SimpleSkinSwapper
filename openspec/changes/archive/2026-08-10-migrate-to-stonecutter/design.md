# Design: migrate-to-stonecutter

## Context

See proposal.md — Why. Current state that shapes the approach:

- Branch-per-version: `1.21.11` (Yarn, Java 21, loom 1.15, Gradle 9.2.1), `26.1` (Mojang names, Java 25), `master` = 26.2 (Mojang names, Java 25). Feature commits are manually replayed on each branch.
- Measured deltas between versions:
  - `26.1` → `26.2`: ~7 call sites, essentially one API move (`client.setScreen(...)` → `client.gui.setScreen(...)`), plus dependency bumps and `fabric.mod.json` constraints.
  - `1.21.11` → `26.1`: dominated by the Yarn→Mojang symbol rename (`DrawContext`→`GuiGraphicsExtractor`, `PlayerEntityRenderState`→`AvatarRenderState`, …), plus a handful of real API deltas concentrated in the rendering code (`SkinRenderer`, scissor stack, picture-in-picture GUI element registration).
- Bundled dependencies (spruceui, yumi-mc-foundation) and compileOnly modmenu exist in per-version coordinates already (`+26.1`, `+26.2` classifiers).
- `openspec/` is currently untracked in git.
- Build surface is small: `settings.gradle` (10 lines), `build.gradle` (~105 lines), no exotic build logic.

## Goals / Non-Goals

**Goals:**

- One branch, one shared `src/`, one command that builds all supported version jars.
- Supporting a new Minecraft drop = add a `versions/<drop>/` folder, register it, wrap API deltas in `//?` conditionals. No branch porting.
- All supported targets (1.21.11, 26.1.x, 26.2) built from the same source with per-version dependencies.
- Every step independently verifiable (one variable changes at a time).

**Non-Goals:**

- No runtime behavior change of the mod on any version (byte-level jar differences may exist; features must not).
- No support for `1.21.8` (stale at 0.4.0, out of scope per proposal).
- No loader expansion (no NeoForge/Architectury) — Stonecutter here manages Minecraft versions only.
- No redesign of the mod's code beyond what the mappings rename and the source merge require.

## Decisions

### D1 — Convert Groovy → Kotlin DSL as a separate, prior step

Convert `settings.gradle` and `build.gradle` to Kotlin DSL and verify the build produces an equivalent jar before touching anything else.

- **Why:** Stonecutter's configuration DSL, documentation, and community examples are Kotlin-first; driving its Kotlin-native DSL from Groovy is clumsy and unsupported in practice. The build surface is tiny (~115 lines total), so the conversion is cheap.
- **Why separately:** a behavior-neutral checkpoint (`build` + `runClient` green, same output) means any failure during the Stonecutter restructuring is attributable to the restructuring, not the syntax change.
- **Alternative rejected:** big-bang (convert + restructure together) — failures become impossible to isolate. Staying on Groovy — fighting the tool's ecosystem forever.

### D2 — Migrate 1.21.11 to Mojang mappings before merging sources

Rename the `1.21.11` codebase from Yarn to official Mojang names as its own mechanical commit on the `1.21.11` branch.

- **Why:** a shared Stonecutter source compiles one symbol name per call site. With Yarn on 1.21.11 and Mojang names on 26.x, nearly every Minecraft class reference would need a `//?` conditional — on a GUI-heavy mod that is unmanageable noise. After unifying naming, only *real* API deltas remain as conditionals.
- **Alternatives rejected:** Stonecutter with Yarn kept on 1.21.11 (comment explosion); dropping 1.21.11 (user requirement: long-term support).

### D3 — Stonecutter with Stitcher comment conditionals

Adopt Stonecutter: per-version subprojects named by version key (`1.21.11`, `26.1.2`, `26.2`), shared `src/`, per-version `versions/<key>/gradle.properties` carrying `minecraft_version`, `loader_version`, `fabric_version`, bundled-dep coordinates, and Java toolchain. API deltas expressed as Stitcher comments (`//? if >=26.2 { … //?} else { … }`), which keep the file valid Java for the active version by block-commenting the inactive branches.

- **Why this over ReplayMod Preprocessor / JCP:** Stitcher's block-comment wrapping keeps disabled code readable (one `/* */` around a whole method vs `//$$` on every line); active maintenance (0.9 branch) and caching of transformed files.
- **Why over keeping branches:** eliminates the cherry-pick workflow measured in the git history (same commits replayed 3×).
- **Active version policy:** the latest drop (26.2) is the active/VCS version — the IDE works on it, and the committed source is in its state. Switching the active version rewrites comment wrappings; such switches are committed separately from real changes.

### D4 — Single main branch; `openspec/` tracked there

The Stonecutter restructure lands on a working branch merged into `master`, which becomes the single development branch. `1.21.11` and `26.1` branches are frozen as historical release references. `openspec/` starts being tracked on the main branch at the same time; OpenSpec commands are only ever run with the main branch checked out.

- **Why:** spec divergence across branches would recreate the cherry-pick problem for documentation. One branch = one spec source of truth.

### D5 — Convert the mod's Java sources to Kotlin, per version branch, before the Stonecutter merge

Convert the codebase to Kotlin twice, independently: on `master` (26.2) and on the `1.21.11` branch (after its Mojang-mappings rename). The Stonecutter source merge (D3) then combines sources that are already Kotlin on both sides.

- **Why not after the merge:** Stitcher keeps inactive version branches as `/* */` block comments inside the shared files. A Java→Kotlin converter does not touch comment contents, so converting after the merge would leave fossilized Java inside the comment blocks — which then breaks the `.kt` file the next time Stonecutter switches the active version and unwraps them.
- **How:** IntelliJ's Java→Kotlin converter as scaffolding only, one file at a time, each followed by a human review pass (hunt `!!`, replace Java idioms with idiomatic Kotlin, verify the doc comments still tell the truth). Fully manual conversion for `SkinRenderer` and similarly delicate files. Never a bulk whole-directory conversion.
- **Mixins stay in Java** (the 5 mixin/accessor classes): Mixin on Kotlin classes works but has known bytecode-level pitfalls (final-by-default classes, synthetic methods, companion objects); keeping them in Java is a common, low-regret pattern. Java/Kotlin interop in one source set makes this seamless.
- **User-facing consequence:** the mod gains a `fabric-language-kotlin` dependency (standard practice; declared in `fabric.mod.json`, per-version coordinates where needed).
- **Kotlin toolchain check:** confirm the available Kotlin version supports the Java 25 bytecode target for the 26.x versions before starting; 1.21.11 (Java 21 target) is unaffected.
- **Alternative rejected:** converting after the Stonecutter merge (broken comment branches, much harder review against conditional-laden files); staying in Java (user preference is Kotlin; the conversion window is cheapest exactly here, while every branch is still single-version).

### D6 — Unify the toolchain, keep per-version Java

Use one loom (1.15) and one Gradle (9.2.1) across all version subprojects — the 1.21.11 branch already builds with these, so no risk there. Java stays per-version (21 for 1.21.11, 25 for 26.x) via each version's properties.

### D7 — CI builds every version

The IDE only type-checks the active version; code inside inactive `/* */` branches is invisible to the compiler. Therefore CI must run the all-versions build (`chiseledBuild` or per-version `build`) on every push.

## Risks / Trade-offs

- [Errors in non-active versions surface late] → CI builds all versions; locally run the all-versions build before committing cross-version changes.
- [Comment noise in rendering-heavy files (`SkinRenderer`, screens)] → mitigated by D2 (naming unified); remaining deltas wrapped as whole-method or whole-file conditionals rather than line-by-line.
- [Per-version availability of bundled deps (spruceui, yumi, modmenu) for a given drop] → verify coordinates exist before registering a new version folder; they already exist for all current targets.
- [Migration disrupts in-flight work on version branches] → perform the restructure at a quiet point (no pending feature branch), freeze version branches first.
- [Per-version `fabric.mod.json` constraints (`~26.1` vs `~26.2-`)] → expand from per-version properties during `processResources`.
- [Converter-produced Kotlin is un-idiomatic or wrong on nullability] → one-file-at-a-time conversion with immediate human review; manual conversion for delicate files (`SkinRenderer`); mixins stay in Java (D5).
- [Kotlin compiler lag on the Java 25 target for 26.x versions] → verify Kotlin version support before starting the conversion (D5); fallback is delaying Kotlin for the 26.x side only, not blocking the Stonecutter migration.
- [Stonecutter upstream churn (0.9 in active development, docs site scraper-hostile)] → pin the plugin version; rely on the Codeberg repo README/wiki mirrored content and local experimentation.

## Migration Plan

Sequenced, each step a standalone checkpoint (details in tasks.md):

1. **Kotlin DSL conversion** on `master`. Checkpoint: `build` + `runClient` green, equivalent jar.
2. **Mojmap rename** on the `1.21.11` branch. Checkpoint: 1.21.11 builds against Mojang mappings.
3. **Kotlin source conversion**, per branch (D5): convert `master` and the mojmap'd `1.21.11` branch independently, mixins stay in Java. Checkpoint: both branches build and run as Kotlin sources (with Java mixins).
4. **Stonecutter restructure** on a working branch off `master`: settings + `versions/`, merge the 1.21.11 and 26.1 Kotlin sources into the shared `src/` with `//?` conditionals, per-version `fabric.mod.json` expansion, track `openspec/`. Checkpoint: all-versions build produces the three jars; spot-check each jar in a client.
5. **Cutover:** merge to `master`, freeze old branches, add CI for all-versions build.

**Rollback:** until step 4 merges, the existing branches are untouched and remain the working system; abandoning the working branch restores the status quo. After cutover, rollback = revert the merge and resume branch-per-version (frozen branches are intact).

## Open Questions

- **Drop-support policy:** how long do we keep building an old drop (e.g., 26.1) once its successor ships? Deferrable — it only adds/removes a version folder, no impact on the approach.
- **Exact conditional inventory** for 1.21.11↔26.x after the mappings rename — will be enumerated during step 3; the delta analysis done during exploration (screens, `SkinRenderer`, scissor/picture-in-picture APIs) bounds it to a small set of files.
