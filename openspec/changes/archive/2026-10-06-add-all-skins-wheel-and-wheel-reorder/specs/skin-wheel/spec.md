## MODIFIED Requirements

### Requirement: The wheel pages through the whole skin library in wheels of ten

The wheel SHALL be composed from wheel groups, each group contributing consecutive wheels of 10 (a group's last wheel may hold fewer). Each category with a wheel allocation of 1 or more SHALL contribute up to allocation × 10 skins — its list order truncated to that count; categories SHALL appear in category order and categories with allocation 0 SHALL contribute nothing. An all-skins group SHALL additionally contribute wheels of 10 built from the registry order, governed by the All skins wheel option: in Always the all-skins group SHALL come before every category group, built from the first max-all-skins-wheels × 10 registry skins when at least one category contributes a wheel, and from the whole registry without truncation otherwise; in Fallback it SHALL contribute only when no category contributes any wheel, built from the whole registry without truncation; in Never it SHALL contribute nothing. Skins beyond a group's truncation SHALL NOT appear on the wheel. When nothing contributes any wheel, the wheel SHALL stay empty (no sectors) rather than crashing.

#### Scenario: Wheels follow the category order and allocations

- **WHEN** categories are A (allocation 2, 14 skins), B (allocation 0), and C (allocation 1, 5 skins), in that order, and the All skins wheel option is Fallback
- **THEN** the wheel shows A's first 10 skins, then A's remaining 4, then C's 5 — and nothing from B or from uncategorized skins

#### Scenario: Allocation change reshapes the wheel

- **WHEN** a category's allocation is reduced from 2 to 1 in the library
- **THEN** its second wheel no longer appears and the total wheel count drops accordingly

#### Scenario: Fallback substitutes the all-skins group

- **WHEN** the All skins wheel option is Fallback, no category has allocation 1 or more, and the registry holds 23 skins
- **THEN** three all-skins wheels exist (10, 10, 3 skins) showing the whole registry in registry order

#### Scenario: Fallback never mixes with categories

- **WHEN** the All skins wheel option is Fallback and at least one category contributes a wheel
- **THEN** no all-skins wheel appears anywhere in the sequence

#### Scenario: Always prepends a capped all-skins group

- **WHEN** the All skins wheel option is Always with max all-skins wheels 2, the registry holds 47 skins, and categories A (allocation 1, 12 skins) and C (allocation 1, 5 skins) exist
- **THEN** the wheels are, in order: all-skins 1–10, all-skins 11–20, A's 10, A's remaining 2, C's 5 — the all-skins group truncated to 20 skins

#### Scenario: Always with a small registry or empty registry

- **WHEN** the All skins wheel option is Always with max all-skins wheels 2 and the registry holds 7 skins
- **THEN** exactly one all-skins wheel precedes the category wheels; and when the registry is empty, no all-skins wheel appears

#### Scenario: Always without category wheels is unbounded

- **WHEN** the All skins wheel option is Always and no category contributes any wheel while the registry holds 23 skins
- **THEN** three all-skins wheels exist (10, 10, 3 skins) — the cap does not apply because no category wheel is there to bury

#### Scenario: Empty wheel when nothing is allocated

- **WHEN** the All skins wheel option is Never and every category has allocation 0 or there are no categories, or the option is Fallback and the registry is empty under the same category conditions
- **THEN** the wheel renders without sectors and does not crash

#### Scenario: More than ten skins

- **WHEN** the allocated categories contribute 23 skins in total and the wheel is opened
- **THEN** three wheels exist (10, 10, 3 skins) and every contributed skin is reachable

#### Scenario: Ten skins or fewer

- **WHEN** the allocated categories contribute at most 10 skins
- **THEN** a single wheel exists and no side wheels are displayed

#### Scenario: Two wheels wrap

- **WHEN** the allocated categories contribute between 11 and 20 skins and the first wheel is active
- **THEN** both the left and right edge show the second wheel

### Requirement: Pagination feedback is displayed

The wheel SHALL show the page position as pagination dots or an equivalent counter. When the wheels come from more than one source group, each dot SHALL be colored after the group it represents: category dots after their category, and all-skins dots in a neutral color distinct from every category dye. Dots SHALL be clickable: clicking a dot SHALL slide the wheel to that group's first wheel through the normal sliding animation. Hovering a dot SHALL identify its group (tooltip), the All skins label for all-skins dots.

#### Scenario: Dots reflect categories

- **WHEN** categories A (2 wheels, red) and C (1 wheel, blue) feed the wheel
- **THEN** three dots are shown: two red followed by one blue

#### Scenario: Dots reflect the all-skins group

- **WHEN** the All skins wheel option is Always with max all-skins wheels 2 and category C (1 wheel, blue) follows
- **THEN** three dots are shown: two neutral all-skins dots followed by one blue dot, and hovering the first shows the All skins label

#### Scenario: Clicking a dot jumps to a category

- **WHEN** the wheel is on A's first wheel and the user clicks C's blue dot
- **THEN** the wheel slides directly to C's first wheel

#### Scenario: Few wheels show dots

- **WHEN** the wheel spans three wheels and the second is active
- **THEN** three dots are displayed with the middle one highlighted

#### Scenario: Many wheels show a counter

- **WHEN** the wheel spans twelve wheels
- **THEN** a counter such as "2/12" is displayed instead of dots

#### Scenario: Name hidden while sliding

- **WHEN** a slide between wheels is in progress
- **THEN** the hovered-skin name is not displayed

## ADDED Requirements

### Requirement: Wheel sectors can be reordered by dragging them outward

Pressing a filled sector of the centered wheel at rest and pulling it away from the wheel center SHALL stretch the sector radially outward along its own axis with visible elastic resistance: only the outward component of the movement stretches it, the sector SHALL NOT rotate to follow the cursor, and pulling toward the center SHALL not move it. Pulling outward beyond a small distance SHALL enter reorder mode for that skin: the sector settles pulled out of the wheel like a cut slice, while the other sectors of its group continuously reflow as if the skin had been removed from its slot. While the mode is active, the target slot SHALL be the displayed slot of the centered wheel whose angle around the wheel center is nearest to the mouse direction; the empty slot immediately after the wheel's last displayed skin SHALL also be targetable, dropping the skin at the end of that wheel, while the padding fillers beyond it SHALL never be targetable — the nearest landing slot SHALL remain the target when the mouse points at one. A click SHALL commit the move: the skin takes the target slot, the group's sectors settle into the new order, and the order persists — the category's card list for a category group, the registry order for the all-skins group. A press released before the pull distance is reached SHALL keep today's behavior and apply the hovered skin. A commit SHALL only be accepted while the wheel is at rest on the centered wheel.

#### Scenario: The sector stretches elastically before reorder

- **WHEN** the user presses a filled sector at rest and drags outward without reaching the pull distance
- **THEN** the sector stretches outward along its own axis with resistance, never rotating to follow the cursor

#### Scenario: Pulling toward the center does nothing

- **WHEN** the user presses a filled sector at rest and drags toward the wheel center
- **THEN** the sector does not move and no mode engages

#### Scenario: Dragging enters reorder mode

- **WHEN** the user pulls a filled sector of the centered wheel at rest outward beyond the pull distance
- **THEN** the sector settles pulled out of the wheel and reorder mode is active

#### Scenario: Click without drag still applies

- **WHEN** the user presses a filled sector and releases without reaching the pull distance
- **THEN** the hovered skin is applied exactly as before and no order changes

#### Scenario: The angle targets the nearest filled slot

- **WHEN** the mouse direction falls between two filled slots during reorder mode
- **THEN** the slot closer by angle around the wheel center is the target

#### Scenario: Padding fillers are not targets

- **WHEN** the mouse direction falls on a padding filler sector during reorder mode
- **THEN** the nearest landing slot remains the target and no filler can ever receive the skin

#### Scenario: The slot after the last skin accepts the drop

- **WHEN** the user pulls a skin from a full wheel of ten and aims at the vacated tenth slot
- **THEN** that slot is targetable and committing moves the skin to the last position of the wheel

#### Scenario: Reflow follows the target

- **WHEN** the target slot changes while the skin is pulled out
- **THEN** the other sectors of the group ease so the insertion gap opens at the target slot

#### Scenario: Committing inside a category group persists

- **WHEN** the user commits a sector from one wheel of category A to a filled slot of another wheel of category A
- **THEN** A's card list reflects the move — library position numbers and allocation markers follow — and the order survives a restart

#### Scenario: Committing in the all-skins group persists

- **WHEN** the user commits a sector between two all-skins wheels
- **THEN** the registry order reflects the move — the library All skins view follows — and the order survives a restart

### Requirement: Reorder mode follows same-group navigation and cancels otherwise

While reorder mode is active, scrolling SHALL keep sliding between wheels. Reaching another wheel of the same group SHALL keep the mode active: the dragged sector stays pulled out and the target may be any filled slot of any wheel of that group. Reaching a wheel of a different group SHALL cancel the mode: the dragged sector eases back into its slot and the layout returns to the pre-drag order without persisting anything. Releasing the open-wheel key during reorder mode SHALL cancel the mode and close the wheel without committing. A right click SHALL cancel the mode and keep the wheel open; the escape key SHALL cancel the mode before closing the screen. After any cancel, the displayed order SHALL be exactly the pre-drag order.

#### Scenario: Same-category scroll keeps the mode

- **WHEN** the user pulls a sector from category A's first wheel and scrolls to A's second wheel
- **THEN** reorder mode stays active and a filled slot of that wheel can be committed

#### Scenario: A different category cancels

- **WHEN** the view slides from a wheel of category A into a wheel of category B during reorder mode
- **THEN** the mode cancels, the sector eases back to its origin slot, and no order changed

#### Scenario: All-skins wheels form one group

- **WHEN** the All skins wheel option is Fallback with three all-skins wheels and the user scrolls between them during reorder mode
- **THEN** the mode stays active across all three wheels

#### Scenario: Releasing the open-wheel key cancels and closes

- **WHEN** the user releases the open-wheel key during reorder mode
- **THEN** the mode cancels without committing, the sector does not move, and the wheel closes

#### Scenario: Right click cancels and stays open

- **WHEN** the user right-clicks during reorder mode
- **THEN** the sector eases back to its origin slot, the mode ends, and the wheel stays open

#### Scenario: Escape cancels before closing

- **WHEN** the user presses escape during reorder mode
- **THEN** the mode cancels and the screen stays open; a subsequent escape closes the screen as usual

#### Scenario: Dot navigation obeys the group rules

- **WHEN** the user clicks the dot of another category's first wheel during reorder mode
- **THEN** the wheel slides there and the mode cancels exactly as when scrolling into another group
