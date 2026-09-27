## ADDED Requirements

### Requirement: Display names are view-resolved and independently stored

The library SHALL store the global skin display name and per-category card names as two independent values, and SHALL resolve the name a skin displays per view: inside a category's view, the card's category name takes priority when set, followed by the global display name, followed by the file base name; in the derived views (All skins, Uncategorized) and the wheel, the global display name applies, followed by the file base name. Editing one of the two names SHALL leave the other stored value untouched, and opening and closing the detail overlay without edits SHALL change no stored name.

#### Scenario: Category name takes priority in its category view

- **WHEN** a skin has both a global display name and a category name in some category, and that category's view is open
- **THEN** the skin's card shows the category name, while the All skins and Uncategorized views still show the global display name

#### Scenario: Global name applies when no category name is set

- **WHEN** a skin's card in a category carries no category name
- **THEN** the card shows the skin's global display name (or file base name when unset), like the derived views do

#### Scenario: Editing one name leaves the other intact

- **WHEN** the user renames only the category name from the detail overlay in a category view
- **THEN** the category name is stored for that card and the skin's global display name is unchanged (and vice versa)

#### Scenario: Closing the detail overlay without edits commits nothing

- **WHEN** the user opens the detail overlay on a skin whose card already carries a category name, inside that category's view, and closes it without editing any field
- **THEN** neither the global display name nor the category name changes anywhere in the library
