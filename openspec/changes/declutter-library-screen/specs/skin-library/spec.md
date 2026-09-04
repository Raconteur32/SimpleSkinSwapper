## MODIFIED Requirements

### Requirement: Skin add and delete flows are available on the screen

Skins SHALL be addable through the add-skin overlay (see the add card requirement) while a category is selected or the All skins view is shown; no other import affordance SHALL be shown on the library screen. A skin SHALL be deletable through its detail overlay's two-step delete action. Added skins SHALL appear in the All skins view unassigned to any category.

#### Scenario: Import lands in All skins

- **WHEN** the user adds a new skin file through the add-skin overlay while a category is selected
- **THEN** the skin file is added to the skins folder, appears in the All skins view, and is not silently assigned to a category

#### Scenario: Delete through the detail overlay

- **WHEN** the user deletes a skin through the detail overlay's confirmation
- **THEN** the file is removed from the skins folder and every store entry follows

#### Scenario: No ghost confirm controls

- **WHEN** no category-delete confirmation is showing
- **THEN** no confirm or cancel button from that confirmation is visible or clickable anywhere on the screen

#### Scenario: Deleting an empty category

- **WHEN** the user clicks the delete control of an empty category's expanded config band
- **THEN** the delete confirmation opens instead of the add-skin overlay, and clicks in the rest of the empty card zone still open the add-skin overlay
