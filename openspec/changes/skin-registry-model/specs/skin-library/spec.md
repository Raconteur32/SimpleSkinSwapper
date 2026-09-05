## ADDED Requirements

### Requirement: Skins are unique texture-model entries in a mod-managed registry

The library distinguishes three levels: a texture (a mod-managed file), a skin (the pair of one texture and one model, wide or slim, with a display name), and a card (a reference to a skin inside a category, optionally carrying a per-category name). The registry MUST NOT hold two skins with the same texture-model pair, and the skins folder MUST NOT hold two files with the same texture value.

#### Scenario: Importing a file whose texture already exists reuses it
- **WHEN** the user adds a skin from a file whose texture value is already present in the library
- **THEN** no new file is created and the new skin references the existing texture

#### Scenario: Creating a duplicate skin is refused
- **WHEN** the user creates a skin whose (texture, model) pair already exists in the registry
- **THEN** the creation fails without creating a texture or a card

#### Scenario: The last skin on a texture takes the file with it
- **WHEN** a skin is deleted and no other skin references its texture
- **THEN** the texture file is deleted from the skins folder

#### Scenario: A shared texture survives one of its skins
- **WHEN** a skin is deleted and another skin still references the same texture
- **THEN** the texture file remains

### Requirement: The model is pre-filled automatically at creation

The creation panel SHALL pre-select the model automatically and keep it user-editable: for an account import the model comes from the account's texture metadata, for a file import from pixel detection.

#### Scenario: Account import uses Mojang metadata
- **WHEN** the user imports a skin from an account name whose texture metadata declares a model
- **THEN** the creation panel opens with that model pre-selected

#### Scenario: File import falls back to detection
- **WHEN** the user imports a skin from a file
- **THEN** the creation panel opens with the pixel-detected model pre-selected

#### Scenario: The pre-filled model stays editable
- **WHEN** the user changes the pre-selected model before confirming
- **THEN** the skin is created with the chosen model

### Requirement: Texture files are mod-managed hash-named files

Texture files SHALL be named after a hash of their content; identity comparisons SHALL always use the full hash, never the short file-name hash. On a short-hash collision between different values the name SHALL be lengthened until unique. Files the mod did not create SHALL be ignored by the library, and a texture file removed externally SHALL remove its skins and their cards everywhere.

#### Scenario: Short-hash collision lengthens the name
- **WHEN** two different texture values hash to the same short file name
- **THEN** the second file is stored under a longer hash-derived name, uniquely

#### Scenario: A manually dropped file is ignored
- **WHEN** a png file not created by the mod appears in the skins folder
- **THEN** it never appears in any library view

#### Scenario: An external deletion prunes the library
- **WHEN** a texture file is deleted outside the mod while skins reference it
- **THEN** those skins and every card referencing them disappear from all views

### Requirement: Uncategorized gathers skins with no category

A built-in Uncategorized view SHALL show exactly the skins referenced by zero categories; All skins SHALL still show every skin.

#### Scenario: An unassigned skin appears in Uncategorized
- **WHEN** a skin is referenced by no category
- **THEN** it has a card in Uncategorized and in All skins only

#### Scenario: Assigning to a category leaves Uncategorized
- **WHEN** a card is created for a skin in a category
- **THEN** the skin no longer appears in Uncategorized

### Requirement: Legacy libraries migrate once to the registry model

On first load with legacy data, the library SHALL turn existing files into registry entries and hash-named textures, preserve the original files untouched in a dedicated sub-folder, remap category lists to card references, and remap the persisted selected skin to the new skin identity. The migration SHALL run once and be marked as done.

#### Scenario: A legacy library migrates without losing files
- **WHEN** the library opens with legacy user-named files and category data
- **THEN** textures, registry entries, remapped categories and the preserved original files all exist, and a second load does not re-migrate

### Requirement: Unit tests cover the library core

The library core (registry, hashing and naming, texture lifecycle, migration, delete decision) SHALL run under plain JVM unit tests against throwaway folders, without requiring the game.

#### Scenario: The core test suite runs without the game
- **WHEN** the unit test task is executed
- **THEN** the core behaviors (dedup, lifecycle, naming, migration, delete decision) are exercised and pass

## MODIFIED Requirements

### Requirement: Card drag is split between rotate and reorder zones

Dragging the skin model on a card SHALL keep the existing drag-to-rotate behavior unchanged. Dragging a card by its handle or frame SHALL NOT reorder it: cards keep their positions, no insertion gap shows, and the view order stays the default order. During any drag, hovered rotation and the hover walk animation SHALL not apply to the dragged card.

#### Scenario: Rotating the model is unaffected

- **WHEN** the user drags a card's skin model to rotate it, including vertically
- **THEN** the preview rotates as before and the card does not reorder

#### Scenario: Reorder by the handle

- **WHEN** the user drags a card by its handle and releases between two cards
- **THEN** no reorder happens: the card returns to its slot, the other cards keep their positions, and the view order is unchanged

#### Scenario: Reorder by the frame

- **WHEN** the user drags a card by its frame border rather than the model or handle
- **THEN** the card behaves exactly as when dragged by the handle: no reorder, positions unchanged

### Requirement: Cards can be moved between categories by dropping on tabs

While a card is being dragged, category tabs SHALL act as drop targets and highlight under the cursor. Dropping a card on a category tab SHALL copy the card into that category (appended at the end of its list) while the source keeps its own reference; a category SHALL NOT hold two references to the same skin. Dropping on a view tab (All skins or Uncategorized) SHALL do nothing.

#### Scenario: Move a skin between categories

- **WHEN** the user drags a card from category A onto the tab of category B and releases
- **THEN** B gains a card for that skin and A keeps its own card

#### Scenario: Assign from All skins

- **WHEN** the user drags a card in the All skins view onto a category tab
- **THEN** the skin joins that category with a card and remains visible in All skins

#### Scenario: Dropping on a view tab does nothing

- **WHEN** a card is dropped on the All skins or Uncategorized tab
- **THEN** no reference changes

#### Scenario: A category never duplicates a reference

- **WHEN** a card for a skin already referenced by the target category is copied there
- **THEN** the target still holds exactly one card for that skin

### Requirement: Skin add and delete flows are available on the screen

Skins SHALL be addable through the add-skin overlay while a category is selected or the All skins view is shown; no other import affordance SHALL be shown on the library screen. Adding SHALL deduplicate by texture value and land the new skin in the selected category (additively, copying semantics) or unassigned from All skins. Deleting SHALL happen through the detail overlay's dialog offering context-dependent choices: from a category, removing the card or deleting the skin everywhere with the other category count shown; from All skins, deleting everywhere with the occurrence count; from Uncategorized, deleting outright as it is referenced nowhere. Renaming SHALL edit display names only — the global skin name, plus a per-category name when opened from a category — never files.

#### Scenario: Import lands in All skins

- **WHEN** the user adds a new skin through the add-skin overlay from the All skins view
- **THEN** the skin is created in the registry (reusing the texture when its value already exists), appears in All skins and Uncategorized, and is not referenced by any category

#### Scenario: Import from a category lands in that category

- **WHEN** the user adds a new skin through the add-skin overlay while a category is selected
- **THEN** the skin is appended to the selected category's list with a card, and appears in All skins as a member of that category

#### Scenario: Import deduplicates by texture value

- **WHEN** the user adds a skin whose texture value already exists in the library
- **THEN** the created skin references the existing texture file and no file is copied

#### Scenario: Delete through the detail overlay

- **WHEN** the user deletes a skin through the detail overlay's confirmation
- **THEN** the skin is removed everywhere at the chosen level, its texture file is deleted when no other skin references it, and every store entry follows

#### Scenario: Delete from a category offers both levels

- **WHEN** the user deletes a card from a category view where the skin has other categories
- **THEN** the dialog offers removing this card and deleting everywhere, stating the skin also appears in the other categories

#### Scenario: Delete from All skins warns about occurrences

- **WHEN** the user deletes a skin from All skins that is referenced by categories
- **THEN** the dialog states it will remove the occurrences in those categories

#### Scenario: Delete from Uncategorized is final

- **WHEN** the user deletes a skin from Uncategorized
- **THEN** the dialog states the skin is referenced nowhere and deletes it everywhere

#### Scenario: Renaming never touches files

- **WHEN** the user renames a skin from the detail panel
- **THEN** the display name (global or per-category) is stored in the registry and the texture file name is unchanged

#### Scenario: No ghost confirm controls

- **WHEN** no confirmation is showing
- **THEN** no confirm or cancel button from a confirmation is visible or clickable anywhere on the screen

#### Scenario: Deleting an empty category

- **WHEN** the user clicks the delete control of an empty category's expanded config band
- **THEN** the delete confirmation opens instead of the add-skin overlay, and clicks in the rest of the empty card zone still open the add-skin overlay
