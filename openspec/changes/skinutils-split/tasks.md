# Tasks: skinutils-split

## 1. Move the GPU service

- [x] 1.1 Create root `SkinTextureLoader` with `loadSkinTexture`, `loadSkinTextureAsync`, `remapTexture`, `stripAlpha`, `copyMirroredLimb` moved verbatim; delete them from `SkinUtils`; update the three callers (`StartupSkinSync`, `SkinEntry`, `SkinAddPanel`). Verify: `./gradlew build detektAll test` ×4 green.

## 2. Enforcement & docs

- [x] 2.1 Konsist: drop `gui.SkinUtils` from the `changeskin` allowlist — zero gui exceptions remain. Verify: `./gradlew test` ×4 green; a temporarily reintroduced `gui.*` import in `changeskin` fails the suite, then revert.
- [x] 2.2 Atlas: §1 back-edge closed (remove the dashed changeskin→gui edge), §5 #5 closed. Verify: "as of" header updated.
- [x] 3.1 Final commit. Verify: full gate green, diff limited to the move + map + atlas.
