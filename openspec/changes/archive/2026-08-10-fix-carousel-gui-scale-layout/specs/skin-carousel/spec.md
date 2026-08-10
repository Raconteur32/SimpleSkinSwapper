# Skin Carousel (delta)

## ADDED Requirements

### Requirement: Carousel layout adapts to GUI scale

The carousel screen SHALL remain fully usable and correctly laid out at any GUI scale: the top row (search field, add buttons, account field) SHALL NOT overflow the right screen edge, cards SHALL NOT overlap the top row, and the top row SHALL be vertically centered between the top band and the cards with a minimum margin of 4px on each side. When space is constrained, the search and account fields SHALL shrink proportionally to their preferred widths (200/120) and SHALL NOT become narrower than 40px. When space is sufficient, preferred dimensions (field widths 200/120, card height = 2/3 of screen height) SHALL be preserved so the layout at common GUI scales is unchanged.

#### Scenario: Top row fits at small logical widths

- **WHEN** the carousel opens at a logical width too small for the preferred row widths (e.g. 480px at GUI scale 4 on a 1080p window)
- **THEN** the whole top row fits between the left and right screen margins, with the search and account fields reduced proportionally

#### Scenario: Fields keep a usable minimum width

- **WHEN** the available space for the flexible fields is very small
- **THEN** each field keeps at least 40px of width

#### Scenario: Cards never overlap the top row

- **WHEN** the logical screen height is small enough that natural-size cards would reach the top row (below ~282px)
- **THEN** card height is reduced so the cards' top edge stays below the top row, and the client renders without overlap

#### Scenario: Minimum margin around the top row

- **WHEN** the space between the top band and the cards is tight
- **THEN** the top row stays vertically centered in that space with at least 4px of margin above and below it

#### Scenario: Normal GUI scales unchanged

- **WHEN** the carousel opens at a logical size large enough for the preferred layout (e.g. 960x540)
- **THEN** the search field is 200px wide, the account field is 120px wide, and cards take their natural height
