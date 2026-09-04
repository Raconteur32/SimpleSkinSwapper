# Skin Wheel

## Purpose

The radial skin picker overlay: a hold-to-open wheel showing the user's skins as pie sectors with 3D previews, paginated through the whole library in wheels of ten, where aiming with the mouse and releasing applies the highlighted skin.

## Requirements

### Requirement: Hovered sector shows an animated preview

Every skin preview on the wheel SHALL be rendered as a live 3D entity render each frame. Previews of non-hovered sectors SHALL hold a static neutral pose. The preview of the currently hovered sector SHALL play the limb walk animation (arms and legs), and when the hover moves away or between sectors, the previously animated preview's limbs SHALL return to the neutral pose through a smooth eased transition rather than freezing instantly. At most one sector SHALL be hovered at any time.

#### Scenario: Hovering a sector animates its preview

- **WHEN** the user moves the mouse over a sector
- **THEN** that sector's preview plays the limb walk animation while all other previews stay static neutral

#### Scenario: Moving hover between sectors

- **WHEN** the user moves the mouse from one sector to another
- **THEN** the previously hovered preview's limbs ease back to the neutral pose and the newly hovered preview animates

#### Scenario: Mouse at rest renders all previews statically

- **WHEN** the wheel is open and the mouse hovers no sector
- **THEN** all visible previews are rendered live in a static neutral pose

### Requirement: Pie sectors are drawn as meshes, not per-column fills

Wheel pie sectors SHALL be drawn as triangle meshes (one mesh per sector, or equivalent batched geometry) rather than one GUI fill per pixel column. Adjacent sectors SHALL be separated by a constant-width gap: each sector's straight edges SHALL run parallel to the ideal radius lines, offset inward by half the gap width, so the separator reads as a hairline of constant thickness from the center to the rim instead of a wedge that widens outward. The hover highlight behavior SHALL remain unchanged.

#### Scenario: Wheel renders with full sector count

- **WHEN** the wheel is open with N sectors (1..10)
- **THEN** sector backgrounds are drawn with at most O(N) draw submissions per frame, independent of the wheel radius in pixels

#### Scenario: Hover highlight still works

- **WHEN** the user hovers a sector
- **THEN** that sector is drawn in the hover color and all other sectors keep the base color, exactly as before

#### Scenario: Sector gap is a constant-width line

- **WHEN** two adjacent sectors are displayed
- **THEN** the separation between them has the same width near the center and near the rim, following a straight line

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

#### Scenario: More than ten skins

- **WHEN** the allocated categories contribute 23 skins in total and the wheel is opened
- **THEN** three wheels exist (10, 10, 3 skins) and every contributed skin is reachable

#### Scenario: Ten skins or fewer

- **WHEN** the allocated categories contribute at most 10 skins
- **THEN** a single wheel exists and no side wheels are displayed

#### Scenario: Two wheels wrap

- **WHEN** the allocated categories contribute between 11 and 20 skins and the first wheel is active
- **THEN** both the left and right edge show the second wheel
### Requirement: Adjacent wheels peek at the screen edges and are not interactive

While a wheel is at rest, the previous and next wheels SHALL be rendered at the left and right screen edges — roughly half outside the screen, scaled down — as display-only previews. Side wheels SHALL NOT respond to hover, sector animation, or clicks; only the centered wheel is interactive.

#### Scenario: Neighbor wheels are visible but inert

- **WHEN** the wheel is at rest with more than one wheel in the library
- **THEN** the adjacent wheels are partially visible at the screen edges, scaled down, and hovering them produces no sector highlight or animation

#### Scenario: Single wheel has no neighbors

- **WHEN** only one wheel exists
- **THEN** no wheel is rendered at the screen edges

### Requirement: Scrolling slides between wheels with an interruptible animation

Scrolling one notch SHALL target the adjacent wheel in that direction and the view SHALL slide continuously — the incoming wheel growing toward the center while the outgoing wheel shrinks toward the opposite edge. The transition SHALL be driven by a continuous position easing toward an integer target, so a scroll in the opposite direction mid-slide reverses smoothly instead of snapping. The scroll target SHALL stay within two wheels of the currently rendered position (one active slide plus one queued wheel); notches that would exceed that lead SHALL be absorbed until the rendered position catches up. Sliding SHALL not be blocked by a transition in progress.

#### Scenario: One notch slides one wheel

- **WHEN** the user scrolls down one notch while a wheel is at rest
- **THEN** the right wheel slides to the center growing to full scale while the centered wheel slides out to the left edge

#### Scenario: Reversing mid-slide

- **WHEN** the user scrolls in the opposite direction while a slide is in progress
- **THEN** the view reverses smoothly back toward the origin wheel without snapping

#### Scenario: Rapid scrolling glides with a bounded lead

- **WHEN** the user keeps scrolling in the same direction while a slide is in progress
- **THEN** the target rides at most two wheels ahead of the rendered position, producing a continuous glide, and notches beyond that lead are absorbed until the rendered position catches up

### Requirement: Previews outside the viewport are culled

A sector preview whose projected rectangle lies fully outside the screen SHALL NOT be submitted for rendering. Previews partially outside the screen (side wheels) SHALL have their rectangle clamped to the viewport so that no scissor rectangle with a negative origin or zero size is ever submitted.

#### Scenario: Side wheel previews half off-screen

- **WHEN** a neighbor wheel peeks at the screen edge
- **THEN** only its on-screen previews are submitted, with rectangles clamped to the viewport, and the client does not crash

### Requirement: Pagination feedback is displayed

The wheel SHALL show the page position as pagination dots or an equivalent counter. When the wheels come from more than one category, each dot SHALL be colored after the category whose wheel it represents, and dots SHALL be clickable: clicking a category's dot SHALL slide the wheel to that category's first wheel through the normal sliding animation. Hovering a dot SHALL identify its category (tooltip).

#### Scenario: Dots reflect categories

- **WHEN** categories A (2 wheels, red) and C (1 wheel, blue) feed the wheel
- **THEN** three dots are shown: two red followed by one blue

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
