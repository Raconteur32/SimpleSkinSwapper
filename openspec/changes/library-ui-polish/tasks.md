## Tasks

- [x] 1. Tab strip: draw the add-category entry (strip sprite, `#999999` outline, centered "+", hover brighten) after the last tab, scrolling with the strip; extend the strip hit-test for it; wire click to create-select-open-band with the incremented default name; remove the vanilla "+" button; commit
- [x] 2. Footer: lift `gridBottom` to reserve the footer band; spread the three 110px buttons across the panel width (left/center/right); commit
- [x] 3. Lang keys: restore `simpleskinswapper.screen.library.add_category` as "New Category" / "Nouvelle catégorie"; increment logic at the creation call site (landed with task 1 via nextDefaultCategoryName); `./gradlew build detektAll` green; commit
- [ ] 4. In-game test on 26.3 (entry look + hover, creation flow, increment, footer layout, GUI scale auto); commit any polish fallout
