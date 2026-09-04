## Tasks

- [x] 1. Tab strip: draw the add-category entry (strip sprite, `#999999` outline, centered "+", hover brighten) after the last tab, scrolling with the strip; extend the strip hit-test for it; wire click to create-select-open-band with the incremented default name; remove the vanilla "+" button; commit
- [x] 2. Footer: lift `gridBottom` to reserve the footer band; spread the three 110px buttons across the panel width (left/center/right); commit
- [x] 3. Lang keys: restore `simpleskinswapper.screen.library.add_category` as "New Category" / "Nouvelle catégorie"; increment logic at the creation call site (landed with task 1 via nextDefaultCategoryName); `./gradlew build detektAll` green; commit
- [x] 4. In-game test on 26.3 (entry look + hover, creation flow, increment, footer layout, GUI scale auto); commit any polish fallout
      FALLBACK ROUND 1 (user-tested): scroll max is now a whole-tab multiple (no half-hidden tab at the limit) and stripBottom tracks gridBottom (FOOTER_BAND had desynced them); footer buttons spread across the FULL screen width; wheel count now drawn between [-] and [+] (missed from the original capture — restored); DEFAULT_HEX derives from the white dye entry (was off-palette by derivation, drew the fallback square on the tab).
