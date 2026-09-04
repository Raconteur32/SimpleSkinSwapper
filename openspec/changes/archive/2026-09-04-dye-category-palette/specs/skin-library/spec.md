## MODIFIED Requirements

### Requirement: A collapsible config band inside the card zone configures the selected category

When a category tab is selected, a collapsible band SHALL appear at the top of the card viewport, closed by default, aligned on the same inner margins as the cards. It SHALL scroll away with the content like a card row and its expansion SHALL push the grid down inside the viewport; the screen layout SHALL stay constant between the All skins view and category views. Expanding it SHALL show: the 16 Minecraft dye colors as dye item icons laid out in an 8×2 grid, a stepper to change the wheel allocation (including 0), a field to rename the category, and a delete action. The category's tab SHALL display its dye's item icon instead of a flat color square. The selected dye SHALL be highlighted. Hovering a dye icon SHALL show the vanilla dye item name as tooltip. A category whose stored color matches no dye SHALL show no selection highlight. Deleting SHALL require an explicit confirmation prompt. At most one band SHALL be open at a time, and the All skins view SHALL show no band. Changes SHALL apply immediately to the cards and markers.

#### Scenario: Open and configure

- **WHEN** the user expands a category's band and picks another dye icon
- **THEN** the category color changes immediately in the band, the tab, and the cards' allocation markers

#### Scenario: Dye tooltip

- **WHEN** the user hovers a dye icon in the expanded band
- **THEN** the tooltip shows the dye's vanilla item name

#### Scenario: Legacy color without dye match

- **WHEN** a category whose stored color predates the dye palette is displayed with its band expanded
- **THEN** no dye icon is highlighted, the category keeps rendering with its stored color, and picking any dye icon updates it immediately

#### Scenario: Allocation stepper includes zero

- **WHEN** the user steps a category's allocation down to 0
- **THEN** no card in that category shows an allocation marker

#### Scenario: Deletion requires confirmation

- **WHEN** the user triggers the delete action
- **THEN** a confirmation prompt appears, and the category is only deleted after confirming; its skins become uncategorized

#### Scenario: All skins has no config

- **WHEN** the All skins tab is selected
- **THEN** no config band is displayed above the grid
