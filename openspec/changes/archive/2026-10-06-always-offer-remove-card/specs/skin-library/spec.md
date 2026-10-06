## REMOVED Requirements

### Requirement: Skin add and delete flows are available on the screen

**Reason**: Superseded — the category delete popup no longer collapses to a single definitive delete when the card is the skin's last location; "remove from category" and "delete everywhere" are distinct outcomes (the skin stays in the library vs. is deleted) and both stay available.

**Migration**: Re-added as "Skin add, remove and delete flows are available on the screen" — unchanged except the collapse clause and two scenarios.

## ADDED Requirements

### Requirement: Skin add, remove and delete flows are available on the screen

Skins SHALL be addable through the add-skin overlay while a category is selected or the All skins view is shown; no other import affordance SHALL be shown on the library screen. Adding SHALL deduplicate by texture value and land the new skin in the selected category (additively, copying semantics) or unassigned from All skins. Every deletion SHALL go through the shared confirmation popup: from the detail overlay's single delete control, or from the category delete control, all rendering the same popup component. From a category, the popup SHALL offer removing this card or deleting the skin everywhere, with the other-category count shown when the skin has other categories; from All skins, deleting everywhere with the occurrence count; from Uncategorized, deleting outright as it is referenced nowhere. Removing a card that is the skin's last category reference SHALL keep the skin in the library, unassigned from every category. Confirming the popup SHALL execute the deletion without committing any pending detail-panel edits, and canceling SHALL return to the prior state unchanged. Renaming SHALL edit display names only — the global skin name, plus a per-category name when opened from a category — never files. The detail overlay SHALL expose a single delete control; no instant remove-card button, no two-click arming, and no per-card delete affordance outside the detail overlay SHALL remain.

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

- **WHEN** the user deletes a card from any category view
- **THEN** the popup offers removing this card and deleting everywhere, stating the other categories when the skin has any

#### Scenario: Delete at the last location keeps the skin in the library

- **WHEN** the user removes a card from the category view where the skin has no other categories
- **THEN** the popup still offers both removal and full delete, removal leaves the skin uncategorized (still in All skins), and only the full delete removes the skin itself

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
