## Why

The library screen still carries vanilla-styled chrome that clashes with the recipe-book dressing: the category-creation "+" is a vanilla button pinned under the tab strip, the three footer buttons are centered while the card grid encroaches on their row, and creating a category shows the raw translation key `simpleskinswapper.screen.library.add_category` because the key is missing from every lang file. This change settles those details.

## What Changes

- Replace the vanilla "+" button with an add-category entry drawn inside the tab strip, after the last category tab: same background as the strip, gray `#999999` outline with a centered "+", brightening on hover, scrolling with the strip. Clicking it creates a category immediately, selects it and opens its config band.
- New categories get the default name "New Category", incremented ("New Category 2", "New Category 3", …) to avoid reusing a live category name; the missing lang keys are restored (fr: "Nouvelle catégorie").
- The three footer buttons (open folder, config, done) keep their 110px width but spread across the panel width, and the card grid bottom is lifted so the footer row owns its space.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `skin-library`: adds the category-creation entry at the end of the tab strip (currently unspecified): appearance, immediate-create behavior, and the incremented "New Category" default name.
