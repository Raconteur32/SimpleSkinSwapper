# Skin Library Delta

## ADDED Requirements

### Requirement: The library screen shows a vertical category tab strip with an All skins tab

The library screen SHALL display a vertical tab strip on the left side of the window. The strip SHALL show an "All skins" tab as the first tab — a tab like the others: it scrolls with the strip and is never pinned to the screen — listing every skin in the skins folder with no configuration band, followed by one tab per category in category order. Clicking a tab SHALL select it; right-clicking a tab SHALL also select it; hovering a tab SHALL show its name as a tooltip. The selected tab SHALL be visually distinct from the others and SHALL be clipped to the strip zone when scrolled out of it. The screen's chrome (tabs, page, cards, band) SHALL be dressed with the vanilla recipe-book sprites, with darkening baked into dedicated textures rather than applied at runtime over transparent regions.

#### Scenario: All skins is always first

- **WHEN** the library opens with any number of categories
- **THEN** the "All skins" tab appears before all category tabs in reading order and cannot be reordered or removed

#### Scenario: Selection by right click

- **WHEN** the user right-clicks a category tab
- **THEN** that category is selected, exactly as with a left click

#### Scenario: Tab strip overflows

- **WHEN** there are more categories than fit vertically in the strip
- **THEN** the strip scrolls (wheel over the strip) so every tab, All skins included, remains reachable; a selected tab scrolled out of the strip zone is clipped at the zone border

### Requirement: Category tabs can be reordered by dragging with edge auto-scroll

Dragging a category tab beyond a small movement threshold SHALL enter a reorder drag: the dragged tab follows the cursor and an insertion gap opens between the other tabs. While the cursor is held near the top or bottom of the strip during a drag, the strip SHALL auto-scroll continuously, faster the closer to the edge, so tabs beyond the visible range can be crossed. Releasing the tab SHALL insert it at the gap position and persist the new category order. Releasing a tab that was pressed but never moved past the threshold SHALL select it instead of reordering.

#### Scenario: Drag reorders tabs

- **WHEN** the user drags a category tab downward past other tabs and releases
- **THEN** the category moves below those tabs, the other tabs shift accordingly, and the order persists across a restart

#### Scenario: Auto-scroll during drag

- **WHEN** the user holds a dragged tab near the bottom edge of an overflowing strip
- **THEN** the strip scrolls down continuously while the drag continues, and the insertion point updates as tabs scroll past

#### Scenario: Click without movement selects

- **WHEN** the user presses a tab and releases it without moving the cursor beyond the threshold
- **THEN** the tab is selected and the category order is unchanged

### Requirement: The category content area is a responsive vertically scrolling grid

The selected tab's skins SHALL be laid out as a grid inside the panel to the right of the tab strip, in reading order left-to-right then top-to-bottom. The number of columns SHALL adapt to the available width within bounded limits, and the grid SHALL be centered horizontally in the panel. The grid SHALL scroll vertically (mouse wheel), revealing and hiding full rows. Cards whose bounds lie fully outside the visible area SHALL NOT be rendered and SHALL NOT be interactive.

#### Scenario: Reading order

- **WHEN** a category holds N skins and the grid shows several rows
- **THEN** the skins follow the category's list order across each row from left to right, then row by row downward

#### Scenario: Wide and narrow windows

- **WHEN** the library opens on different window sizes or GUI scales
- **THEN** the column count adjusts within the bounded limits, the grid stays centered, and every card remains fully visible and usable

#### Scenario: Off-screen cards are not rendered

- **WHEN** the user scrolls the grid so cards move fully outside the visible panel
- **THEN** those cards are not rendered and do not respond to the mouse, and the client does not crash

### Requirement: Card drag is split between rotate and reorder zones

Dragging the skin model on a card SHALL keep the existing drag-to-rotate behavior unchanged. Dragging the card by its dedicated handle or its frame SHALL reorder: the card follows the cursor above the other cards, an insertion gap shows where it will land, and on release the category's list order updates and persists. The insertion position SHALL be derived from the cursor position in reading order, refined to before/after the hovered cell. During a reorder drag, hovered rotation and the hover walk animation SHALL not apply to the dragged card.

#### Scenario: Rotating the model is unaffected

- **WHEN** the user drags a card's skin model to rotate it, including vertically
- **THEN** the preview rotates as before and the card does not reorder

#### Scenario: Reorder by the handle

- **WHEN** the user drags a card by its handle and releases between two cards
- **THEN** the card moves to that position, other cards shift in reading order, and the order persists

#### Scenario: Reorder by the frame

- **WHEN** the user drags a card by its frame border rather than the model or handle
- **THEN** the card reorders exactly as when dragged by the handle

### Requirement: Cards can be moved between categories by dropping on tabs

While a card is being reordered, category tabs SHALL act as drop targets and highlight under the cursor. Dropping a card on a category tab SHALL move the skin into that category's list (appended at the end). Dragging from the All skins view onto a category tab SHALL assign the skin to that category; dragging from a category onto the All skins tab SHALL unassign it. The file SHALL NOT be deleted by any of these moves.

#### Scenario: Move a skin between categories

- **WHEN** the user drags a card from category A onto the tab of category B and releases
- **THEN** the skin is removed from A's list and appended to B's list

#### Scenario: Assign from All skins

- **WHEN** the user drags a card in the All skins view onto a category tab
- **THEN** the skin joins that category and remains visible in All skins

### Requirement: Cards show their position and wheel membership

Each card SHALL display its position in the current list (1-based). Cards whose position falls within the selected category's wheel allocation (the first allocation × 10 entries) SHALL carry a marker in the category color; cards beyond the allocation SHALL be visually distinct from allocated ones. The markers SHALL update when the allocation or the order changes.

#### Scenario: Allocated cards are marked

- **WHEN** a category with allocation 1 and 25 skins is displayed
- **THEN** cards 1 through 10 show the allocation marker in the category color and cards 11 through 25 do not

#### Scenario: Order change updates markers

- **WHEN** a card is dragged from position 12 to position 5 in the same category
- **THEN** position numbers and allocation markers update to the new order

### Requirement: A collapsible config band inside the card zone configures the selected category

When a category tab is selected, a collapsible band SHALL appear at the top of the card viewport, closed by default, aligned on the same inner margins as the cards. It SHALL scroll away with the content like a card row and its expansion SHALL push the grid down inside the viewport; the screen layout SHALL stay constant between the All skins view and category views. Expanding it SHALL show: the palette color swatches for the category, a stepper to change the wheel allocation (including 0), a field to rename the category, and a delete action. Deleting SHALL require an explicit confirmation prompt. At most one band SHALL be open at a time, and the All skins view SHALL show no band. Changes SHALL apply immediately to the cards and markers.

#### Scenario: Open and configure

- **WHEN** the user expands a category's band and picks another palette color
- **THEN** the category color changes immediately in the band, the tab, and the cards' allocation markers

#### Scenario: Allocation stepper includes zero

- **WHEN** the user steps a category's allocation down to 0
- **THEN** no card in that category shows an allocation marker

#### Scenario: Deletion requires confirmation

- **WHEN** the user triggers the delete action
- **THEN** a confirmation prompt appears, and the category is only deleted after confirming; its skins become uncategorized

#### Scenario: All skins has no config

- **WHEN** the All skins tab is selected
- **THEN** no config band is displayed above the grid

### Requirement: Skin add and delete flows are available on the screen

Skins SHALL be addable through the add-skin overlay (see the add card requirement) while a category is selected or the All skins view is shown; a header import row (file and account import) SHALL remain available alongside it. A skin SHALL be deletable through its detail overlay's two-step delete action. Added skins SHALL appear in the All skins view unassigned to any category.

#### Scenario: Import lands in All skins

- **WHEN** the user imports a new skin file while a category is selected
- **THEN** the skin file is added to the skins folder, appears in the All skins view, and is not silently assigned to a category

#### Scenario: Delete through the detail overlay

- **WHEN** the user deletes a skin through the detail overlay's confirmation
- **THEN** the file is removed from the skins folder and every store entry follows

### Requirement: The library adapts to GUI scale

The library screen SHALL remain fully usable at any GUI scale: the tab strip, grid, config band, and header/footer controls SHALL fit the logical window without overlapping or overflowing, with sensible minimum sizes for constrained layouts and unchanged preferred layout when space is sufficient.

#### Scenario: Small logical window

- **WHEN** the library opens at a small logical resolution (large GUI scale)
- **THEN** tabs, cards, and controls shrink or scroll within their minimum sizes and remain operable without overlap

#### Scenario: Normal GUI scales unchanged

- **WHEN** the library opens at a logical size large enough for the preferred layout
- **THEN** tabs, cells, and controls take their preferred dimensions

### Requirement: Clicking a skin card opens a detail overlay

A plain click on a skin card SHALL open a detail overlay: the card animates scaling up into a large rectangle inset from every screen edge, with the base screen still visible around it. The overlay SHALL show the skin preview in bulk on the right of a thin separator, and management controls on the left: file rename, display name, a wide/slim switch, a delete action, and a replay action that applies the skin and closes the screen. Clicking outside the panel or pressing ESC SHALL close the overlay; closing SHALL commit a pending file rename.

#### Scenario: Open from a plain click

- **WHEN** the user clicks a card body without dragging
- **THEN** the card scales up into the large detail rectangle and the base screen remains visible around it

#### Scenario: File rename propagates

- **WHEN** the user renames the skin from the detail overlay and confirms
- **THEN** the skin file is renamed on disk and every per-file store (type, display name, category membership) follows the new name

#### Scenario: Display name replaces the file name

- **WHEN** the user sets a display name for the skin
- **THEN** the card preview shows the display name instead of the file name; clearing it restores the file name

#### Scenario: Wide/slim switch

- **WHEN** the user activates the switch in the detail overlay
- **THEN** the square knob slides over the newly selected side (Steve for wide, Alex for slim) and the choice persists for the skin file

#### Scenario: Delete with confirmation

- **WHEN** the user presses delete once then confirms
- **THEN** the skin file is deleted, its store entries are cleaned, and the overlay closes

#### Scenario: Replay applies and leaves

- **WHEN** the user activates the replay action in the detail overlay
- **THEN** the skin is applied as the player's skin, the screen closes, and the feedback message shows the outcome

### Requirement: A trailing add card opens an add-skin overlay

Every skin list SHALL end with an add card: the idle card frame with a bare "+" glyph, no preview and no name, scrolling and shifting with the grid like a real card. Clicking it SHALL open an add-skin overlay with the same shell as the detail overlay: the bulk preview on the right of a thin separator and the management controls on the left. The left column SHALL start with the two skin sources — a native PNG file picker and a Minecraft-name download with an invalid-account feedback — then offer the file name, the display name and a wide/slim switch that overrides the auto-detected model, and end with add and cancel actions splitting the column evenly. Add SHALL stay disabled until a skin has been staged and the target file name does not collide. An empty category SHALL hide the add card, show a balanced two-line message, and open the add-skin overlay when the card zone is clicked.

#### Scenario: Add from file with confirmation

- **WHEN** the user picks a PNG through the add overlay and confirms
- **WHEN** the file name is free and the add action is enabled
- **THEN** the skin is copied into the skins folder, its display name and model type are stored, and the grid shows the new card

#### Scenario: Add stays gated

- **WHEN** no skin has been staged, the target name collides, or the name is empty
- **THEN** the add action stays disabled until the state changes

#### Scenario: Empty category invites adding

- **WHEN** a category holds no skin
- **THEN** no add card is shown, a two-line message explains dragging from All skins or clicking, and clicking the card zone opens the add-skin overlay

### Requirement: Card previews animate on hover and settle back smoothly

Library card previews SHALL hold a static neutral pose by default. While the mouse hovers a card, that card's preview SHALL play the limb walk animation. When the hover ends, the animated limbs SHALL return to the neutral pose through a smooth eased transition. Drag-to-rotate SHALL remain available and independent of the hover animation; a card being reorder-dragged SHALL not trigger hover animations on the cards beneath it.

#### Scenario: Hover animates a single card

- **WHEN** the mouse moves over a card
- **THEN** that card's preview plays the limb walk animation while other cards stay static

#### Scenario: Leaving settles smoothly

- **WHEN** the mouse leaves a previously hovered card
- **THEN** its limbs ease back to the neutral pose instead of snapping

#### Scenario: Reorder drag suppresses hover animation

- **WHEN** a card is reorder-dragged over other cards
- **THEN** the cards beneath it do not start their hover animations
