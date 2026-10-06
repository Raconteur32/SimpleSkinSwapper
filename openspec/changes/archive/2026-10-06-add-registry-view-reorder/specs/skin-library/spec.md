## REMOVED Requirements

### Requirement: The whole card body is the reorder grab

**Reason**: Superseded — its core behavior (whole-body grab, no handle, click-opens-detail) is preserved by the new "Card drags reorder the current view" requirement, and its derived-view exemption (dragging SHALL NOT reorder in All skins/Uncategorized) is deliberately reversed by this change.

**Migration**: The grab/detail/no-rotation behavior moves unchanged into "Card drags reorder the current view"; dragging in derived views now reorders the registry order per "Derived views follow and reorder the registry order".

## ADDED Requirements

### Requirement: Card drags reorder the current view

Pressing anywhere on a card and moving SHALL start the card's reorder drag: the card follows the cursor, an insertion gap shows where it will land, and on release the current view's order updates and persists — the category's card list in a category view, the registry order in the All skins and Uncategorized views. There SHALL be no dedicated grab handle or rotation zone on the card. A press released without real movement SHALL open the detail overlay instead. During any drag, the hover walk animation SHALL not apply to the dragged card nor to the cards beneath it.

#### Scenario: Press and move reorders

- **WHEN** the user presses a card and moves beyond a small threshold in a category view
- **THEN** the card follows the cursor as a reorder drag, an insertion gap shows, and releasing between two cards moves the card there and persists the order

#### Scenario: Press and release opens the detail

- **WHEN** the user presses a card and releases without real movement
- **THEN** the detail overlay opens for that card

#### Scenario: No rotation on the card

- **WHEN** the user drags on a card's preview area
- **THEN** the card reorders instead of rotating, and only the detail overlay's preview rotates

#### Scenario: Dragging in a derived view reorders the registry order

- **WHEN** the user presses a card in the All skins or Uncategorized view and moves beyond a small threshold
- **THEN** the card follows the cursor as a reorder drag, an insertion gap shows, and releasing between two cards updates the registry order so the card lands at that displayed position, persisted across restarts

### Requirement: Derived views follow and reorder the registry order

The All skins view SHALL display skins in registry order and the Uncategorized view SHALL display the same order filtered to the skins referenced by zero categories. Reordering in either view SHALL update the registry order itself and persist it; it SHALL NOT change any category's membership. Because a derived view shows only a subset of the registry, a reorder SHALL be applied relative to the displayed cards: a card released between two displayed cards SHALL land immediately before the displayed card at the insertion position, and a card released past the last displayed card SHALL land immediately after the last displayed card of that view — the relative order of the skins not displayed SHALL only change as a side effect of the dragged card's move.

#### Scenario: Reordering in All skins persists

- **WHEN** the user drags a card between two cards in the All skins view
- **THEN** the registry order places the dragged skin at that displayed position and the order survives a restart

#### Scenario: Uncategorized reorder is relative to the filtered view

- **WHEN** the registry holds four skins of which two are uncategorized, and the user drags the second uncategorized card to the first displayed position in Uncategorized
- **THEN** the dragged skin comes immediately before the other uncategorized skin in the registry order, regardless of how many categorized skins separate them in the full list

#### Scenario: Drop past the last displayed card is relative

- **WHEN** the user releases a dragged card beyond the last card of a derived view
- **THEN** the dragged skin lands immediately after the last skin displayed in that view, and skins displayed after it before the drag remain after it

#### Scenario: Derived-view reorder does not touch category membership

- **WHEN** a card is reordered in the All skins or Uncategorized view
- **THEN** no category gains or loses a card reference and the category views keep their own order
