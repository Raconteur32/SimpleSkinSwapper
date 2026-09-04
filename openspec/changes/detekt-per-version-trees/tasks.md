## Tasks

- [x] 1. Register the root `detektAll` task: JavaExec (detekt-cli 1.23.8, JDK 21 toolchain) over each `versions/<v>/build/generated/stonecutter/main/kotlin` with `gradle/detekt/detekt.yml`; pin the generation dependency empirically; fail loudly on a missing tree; build + local run over the 4 trees; record the findings inventory; commit
      RESULT: detektAll green over all 4 generated trees on first run — findings inventory EMPTY (generation pinned via dependsOn(":<v>:stonecutterGenerate"), verified triggering it).
- [x] 2. Fix (or suppress with justification) the findings surfaced by the version trees — NO-OP: inventory empty (see task 1 result), nothing to fix
- [ ] 3. Wire `detektAll` into `.github/workflows/build.yml` after the multi-version build; full `./gradlew build detektAll` green; update tasks and flag anything version-specific learned for the code-review skill
