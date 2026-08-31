# Skin Wheel Delta

## MODIFIED Requirements

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

## ADDED Requirements

### Requirement: The wheel pages through the whole skin library in wheels of ten

The wheel SHALL expose every skin in the library, partitioned into wheels of at most ten skins in library order, instead of skipping skins beyond the first ten. The number of wheels SHALL be the ceiling of the skin count divided by ten. Navigation SHALL be circular: the wheel left of the first wheel is the last wheel and the wheel right of the last wheel is the first.

#### Scenario: More than ten skins

- **WHEN** the library contains 23 skins and the wheel is opened
- **THEN** three wheels exist (10, 10, 3 skins) and every skin is reachable

#### Scenario: Ten skins or fewer

- **WHEN** the library contains at most 10 skins
- **THEN** a single wheel exists and no side wheels are displayed

#### Scenario: Two wheels wrap

- **WHEN** the library contains between 11 and 20 skins and the first wheel is active
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

While the wheel is at rest, the screen SHALL display the active position among the wheels below the wheel: a row of dots with the active one highlighted when there are at most nine wheels, otherwise a counter of the form "i/N". The hovered-skin name SHALL be shown only while the wheel is at rest.

#### Scenario: Few wheels show dots

- **WHEN** the library spans three wheels and the second is active
- **THEN** three dots are displayed with the middle one highlighted

#### Scenario: Many wheels show a counter

- **WHEN** the library spans twelve wheels
- **THEN** a counter such as "2/12" is displayed instead of dots

#### Scenario: Name hidden while sliding

- **WHEN** a slide between wheels is in progress
- **THEN** the hovered-skin name is not displayed
