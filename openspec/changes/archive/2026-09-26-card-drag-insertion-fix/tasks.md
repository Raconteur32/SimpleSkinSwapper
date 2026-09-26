# Tasks: card-drag-insertion-fix

## 1. Fix

- [ ] 1.1 The three edits: `finishCardReorder` drops the `to > from` decrement; `insertionIndexAt` caps the half-right increment at `count`; `updateAddCardPosition` pins the "+" card (`slotFor(size, -1)`). Verify: `./gradlew build detektAll test` ×4 green.
- [ ] 1.2 Commit. Verify: single commit, no unrelated diff.

## 2. In-game validation (maintainer — blocking archive)

- [x] 2.1 On 26.3: two cards — drag A after B (must reorder without reaching the "+" card); drag B before A (unchanged); the "+" card must not move during either drag. Three-plus cards across two rows — forward and backward moves, drop over the "+" = move to last, drop on a category tab = copy. Verify: maintainer confirmation recorded here. CONFIRMED OK by maintainer in game (2026-09-26).
