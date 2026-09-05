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

## MODIFIED Requirements

### Requirement: Skin add and delete flows are available on the screen

Skins SHALL be addable through the add-skin overlay (see the add card requirement) while a category is selected or the All skins view is shown; no other import affordance SHALL be shown on the library screen. A skin SHALL be deletable through its detail overlay's two-step delete action. A skin added from the All skins view SHALL appear unassigned to any category; a skin added while a category is selected SHALL be assigned to that category (appended at the end of its list).

#### Scenario: Import lands in All skins

- **WHEN** the user adds a new skin file through the add-skin overlay from the All skins view
- **THEN** the skin file is added to the skins folder, appears in the All skins view, and is not assigned to any category

#### Scenario: Import from a category lands in that category

- **WHEN** the user adds a new skin file through the add-skin overlay while a category is selected
- **THEN** the skin file is added to the skins folder, appended to the selected category's list, and appears in the All skins view as a member of that category

#### Scenario: Delete through the detail overlay

- **WHEN** the user deletes a skin through the detail overlay's confirmation
- **THEN** the file is removed from the skins folder and every store entry follows

#### Scenario: No ghost confirm controls

- **WHEN** no category-delete confirmation is showing
- **THEN** no confirm or cancel button from that confirmation is visible or clickable anywhere on the screen

#### Scenario: Deleting an empty category

- **WHEN** the user clicks the delete control of an empty category's expanded config band
- **THEN** the delete confirmation opens instead of the add-skin overlay, and clicks in the rest of the empty card zone still open the add-skin overlay
