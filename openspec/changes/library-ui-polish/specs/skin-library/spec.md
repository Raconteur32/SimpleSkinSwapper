## ADDED Requirements

### Requirement: A trailing add-category entry at the end of the tab strip creates categories

The tab strip SHALL end with an add-category entry, below the last category tab: it SHALL scroll with the strip like a tab, use the strip's background dressing, and be marked by a gray `#999999` outline with a centered "+" glyph. Hovering the entry SHALL brighten the outline. It SHALL NOT be rendered as a vanilla button. Clicking it SHALL immediately create a category, select it, and open its config band. The new category SHALL be named "New Category"; when that name is already taken by a live category, the name SHALL be incremented ("New Category 2", "New Category 3", …) until unused. Free-form renames of existing categories SHALL NOT be deduplicated. The screen footer buttons SHALL NOT overlap the entry's strip zone.

#### Scenario: Creating a category from the strip

- **WHEN** the user clicks the add-category entry at the end of the tab strip
- **THEN** a new category is created with the default name, its tab is selected, and its config band is open

#### Scenario: Entry appearance and hover

- **WHEN** the user views and hovers the add-category entry
- **THEN** it shows the strip background with a gray outline and centered "+", and the outline brightens while hovered

#### Scenario: Default name increment

- **WHEN** the user creates categories twice in a row without renaming the first
- **THEN** the first is named "New Category" and the second "New Category 2"
