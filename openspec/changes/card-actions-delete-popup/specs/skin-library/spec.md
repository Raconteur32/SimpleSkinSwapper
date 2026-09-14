# skin-library delta

## REMOVED Requirements

### Requirement: Card drag is split between rotate and reorder zones

The rotate/reorder zone split is retired: the whole card body reorders, and rotation moves to the detail overlay (replaced by "The whole card body is the reorder grab").

## ADDED Requirements

### Requirement: The whole card body is the reorder grab

Pressing anywhere on a card and moving SHALL start the card's reorder drag: the card follows the cursor, an insertion gap shows where it will land, and on release in a category view the category's card order updates and persists. There SHALL be no dedicated grab handle or rotation zone on the card. A press released without real movement SHALL open the detail overlay instead. In the All skins and Uncategorized views dragging SHALL NOT reorder — the order stays the default order and no insertion gap shows. During any drag, the hover walk animation SHALL not apply to the dragged card nor to the cards beneath it.

#### Scenario: Press and move reorders

- **WHEN** the user presses a card and moves beyond a small threshold in a category view
- **THEN** the card follows the cursor as a reorder drag, an insertion gap shows, and releasing between two cards moves the card there and persists the order

#### Scenario: Press and release opens the detail

- **WHEN** the user presses a card and releases without real movement
- **THEN** the detail overlay opens for that card

#### Scenario: No rotation on the card

- **WHEN** the user drags on a card's preview area
- **THEN** the card reorders instead of rotating, and only the detail overlay's preview rotates

#### Scenario: Dragging in a derived view does not reorder

- **WHEN** the user drags a card in the All skins or Uncategorized view
- **THEN** no insertion gap shows, the other cards keep their positions, and the view order is unchanged

## MODIFIED Requirements

### Requirement: Card previews animate on hover and settle back smoothly

Library card previews SHALL hold a static neutral pose by default. While the mouse hovers a card, that card's preview SHALL play the limb walk animation. When the hover ends, the animated limbs SHALL return to the neutral pose through a smooth eased transition. Card previews SHALL NOT rotate on the card — rotation SHALL be available only in the detail overlay; a card being reorder-dragged SHALL not trigger hover animations on the cards beneath it.

#### Scenario: Hover animates a single card

- **WHEN** the mouse moves over a card
- **THEN** that card's preview plays the limb walk animation while other cards stay static

#### Scenario: Leaving settles smoothly

- **WHEN** the mouse leaves a previously hovered card
- **THEN** its limbs ease back to the neutral pose instead of snapping

#### Scenario: Reorder drag suppresses hover animation

- **WHEN** a card is reorder-dragged over other cards
- **THEN** the cards beneath it do not start their hover animations

### Requirement: Skin add and delete flows are available on the screen

Skins SHALL be addable through the add-skin overlay while a category is selected or the All skins view is shown; no other import affordance SHALL be shown on the library screen. Adding SHALL deduplicate by texture value and land the new skin in the selected category (additively, copying semantics) or unassigned from All skins. Every deletion SHALL go through the shared confirmation popup: from the detail overlay's single delete control, or from the category delete control, all rendering the same popup component. From a category, the popup SHALL offer removing this card or deleting the skin everywhere, with the other-category count shown, collapsing to a single definitive delete when the card is the skin's last location; from All skins, deleting everywhere with the occurrence count; from Uncategorized, deleting outright as it is referenced nowhere. Confirming the popup SHALL execute the deletion without committing any pending detail-panel edits, and canceling SHALL return to the prior state unchanged. Renaming SHALL edit display names only — the global skin name, plus a per-category name when opened from a category — never files. The detail overlay SHALL expose a single delete control; no instant remove-card button, no two-click arming, and no per-card delete affordance outside the detail overlay SHALL remain.

#### Scenario: Import lands in All skins

- **WHEN** the user adds a new skin through the add-skin overlay from the All skins view
- **THEN** the skin is created in the registry (reusing the texture when its value already exists), appears in All skins and Uncategorized, and is not referenced by any category

#### Scenario: Import from a category lands in that category

- **WHEN** the user adds a new skin through the add-skin overlay while a category is selected
- **THEN** the skin is appended to the selected category's list with a card, and appears in All skins as a member of that category

#### Scenario: Import deduplicates by texture value

- **WHEN** the user adds a skin whose texture value already exists in the library
- **THEN** the created skin references the existing texture file and no file is copied

#### Scenario: Delete always confirms through the shared popup

- **WHEN** the user triggers a deletion from the detail overlay's delete control or from a category's delete control
- **THEN** the same popup component opens with the context-dependent message and choices, and the deletion only happens on confirm

#### Scenario: Delete through the detail overlay

- **WHEN** the user confirms the deletion of a skin through the detail overlay's popup
- **THEN** the skin is removed everywhere at the chosen level, its texture file is deleted when no other skin references it, and every store entry follows

#### Scenario: Delete from a category offers both levels

- **WHEN** the user deletes a card from a category view where the skin has other categories
- **THEN** the popup offers removing this card and deleting everywhere, stating the skin also appears in the other categories

#### Scenario: Delete at the last location collapses to one choice

- **WHEN** the user deletes a card from a category view where the skin has no other categories
- **THEN** the popup offers a single definitive delete (plus cancel) instead of two equivalent choices

#### Scenario: Delete from All skins warns about occurrences

- **WHEN** the user deletes a skin from All skins that is referenced by categories
- **THEN** the popup states it will remove the occurrences in those categories

#### Scenario: Delete from Uncategorized is final

- **WHEN** the user deletes a skin from Uncategorized
- **THEN** the popup states the skin is referenced nowhere and deletes it everywhere on confirm

#### Scenario: Confirm discards pending panel edits

- **WHEN** the user has unsaved name edits or a pending model switch in the detail overlay and confirms the delete popup
- **THEN** the deletion executes, the panel closes without committing those edits, and no rename or switch lands

#### Scenario: Cancel keeps the panel state

- **WHEN** the user cancels the delete popup opened from the detail overlay
- **THEN** the popup closes and the panel remains open with its pending edits intact

#### Scenario: Category deletion uses the same component

- **WHEN** the user deletes a category through its config band
- **THEN** the confirmation is rendered by the same popup component as skin deletions, with its own message and buttons

#### Scenario: Renaming never touches files

- **WHEN** the user renames a skin from the detail panel
- **THEN** the display name (global or per-category) is stored in the registry and the texture file name is unchanged

#### Scenario: No ghost confirm controls

- **WHEN** no confirmation is showing
- **THEN** no confirm or cancel button from a confirmation is visible or clickable anywhere on the screen

#### Scenario: Deleting an empty category

- **WHEN** the user clicks the delete control of an empty category's expanded config band
- **THEN** the delete confirmation opens instead of the add-skin overlay, and clicks in the rest of the empty card zone still open the add-skin overlay
