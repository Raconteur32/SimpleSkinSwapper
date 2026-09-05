## Why

The library screen still carries vanilla-styled chrome that clashes with the recipe-book dressing: the category-creation "+" is a vanilla button pinned under the tab strip, the three footer buttons are centered while the card grid encroaches on their row, and creating a category shows the raw translation key `simpleskinswapper.screen.library.add_category` because the key is missing from every lang file. This change settles those details.

## What Changes

- Replace the vanilla "+" button with an add-category entry drawn inside the tab strip, after the last category tab: same background as the strip, gray `#999999` outline with a centered "+", brightening on hover, scrolling with the strip. Clicking it creates a category immediately, selects it and opens its config band.
- New categories get the default name "New Category", incremented ("New Category 2", "New Category 3", …) to avoid reusing a live category name; the missing lang keys are restored (fr: "Nouvelle catégorie").
- The three footer buttons (open folder, config, done) keep their 110px width but spread across the screen width, and the card grid bottom is lifted so the footer row owns its space.
- Adding a skin while a category is selected files it into that category (All skins adds stay unassigned).
- Tab strip rework settled through in-game iteration: per-tab panels replace the zone background and stack with a 2px overlap (selection always on top); tab height derives from the strip footprint so whole tabs tile it exactly (centered remainder, no half-shown tabs, no scroll drift — the wheel steps one slot); overlong tab names truncate with an ellipsis.
- The category band's wheel stepper shows the live count between [-] and [+].

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `skin-library`: adds the category-creation entry at the end of the tab strip (currently unspecified): appearance, immediate-create behavior, and the incremented "New Category" default name. Also flips the add-flow assignment rule — adding from a selected category now files the skin into it (was: never silently assigned).
