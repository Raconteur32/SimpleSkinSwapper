# Skin Wheel Delta

> Baseline: the paginated wheel from `paginated-skin-wheel` (wheels of ten, sliding between wheels, pagination feedback). This change redefines what the pages contain.

## MODIFIED Requirements

### Requirement: The wheel pages through the whole skin library in wheels of ten

The wheel SHALL be composed from the user's categories instead of the flat skin list: each category with a wheel allocation of 1 or more SHALL contribute up to allocation × 10 skins — its list order truncated to that count — as consecutive wheels of 10 (the last wheel of a category may hold fewer). Categories SHALL appear in category order, and categories with allocation 0 SHALL contribute nothing. Skins not in any allocated category SHALL NOT appear on the wheel. When no category contributes any wheel, the wheel SHALL stay empty (no sectors) rather than falling back to the flat list.

#### Scenario: Wheels follow the category order and allocations

- **WHEN** categories are A (allocation 2, 14 skins), B (allocation 0), and C (allocation 1, 5 skins), in that order
- **THEN** the wheel shows A's first 10 skins, then A's remaining 4, then C's 5 — and nothing from B or from uncategorized skins

#### Scenario: Allocation change reshapes the wheel

- **WHEN** a category's allocation is reduced from 2 to 1 in the library
- **THEN** its second wheel no longer appears and the total wheel count drops accordingly

#### Scenario: Empty wheel when nothing is allocated

- **WHEN** every category has allocation 0 or there are no categories
- **THEN** the wheel renders without sectors and does not crash

### Requirement: Pagination feedback is displayed

The wheel SHALL show the page position as pagination dots or an equivalent counter. When the wheels come from more than one category, each dot SHALL be colored after the category whose wheel it represents, and dots SHALL be clickable: clicking a category's dot SHALL slide the wheel to that category's first wheel through the normal sliding animation. Hovering a dot SHALL identify its category (tooltip).

#### Scenario: Dots reflect categories

- **WHEN** categories A (2 wheels, red) and C (1 wheel, blue) feed the wheel
- **THEN** three dots are shown: two red followed by one blue

#### Scenario: Clicking a dot jumps to a category

- **WHEN** the wheel is on A's first wheel and the user clicks C's blue dot
- **THEN** the wheel slides directly to C's first wheel
