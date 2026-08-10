# Skin Carousel

## Purpose

Displays the user's skin library as a horizontally scrollable row of cards and lets the user preview, reorder, apply, and delete skins from it.

## Requirements

### Requirement: Off-screen carousel cards are not rendered

The carousel SHALL NOT render cards whose bounds lie fully outside the visible screen area, whether past the left edge or the right edge. Off-screen cards SHALL NOT submit any GUI render state (including button label text with scissor rectangles) and SHALL NOT be interactive while fully off-screen.

#### Scenario: More cards than fit on screen

- **WHEN** the carousel opens with more skins than fit horizontally on screen
- **THEN** cards positioned fully beyond the right screen edge are not rendered and the client does not crash

#### Scenario: Scrolled to the right

- **WHEN** the user scrolls the carousel so that one or more cards move fully past the left screen edge
- **THEN** those cards are not rendered and the client does not crash

#### Scenario: Partially visible cards still render

- **WHEN** a card intersects the visible screen area by at least one pixel
- **THEN** the card renders normally, including its preview and name

#### Scenario: Buttons clipped at screen edges

- **WHEN** a card's button straddles a screen edge while the card scrolls
- **THEN** the button keeps rendering, with its label clipped to the visible area, and the client does not crash (MC 26.2 rejects scissor rectangles with a negative origin or zero size; card button labels therefore render through the clamped scissor path)

#### Scenario: Off-screen cards are not clickable

- **WHEN** a card is fully off-screen
- **THEN** it does not respond to mouse hover, clicks, or keyboard focus

### Requirement: Card visibility updates every frame with scroll position

Card visibility SHALL be re-evaluated whenever the carousel assigns card positions, so that scrolling continuously reveals and hides cards without stale visibility state.

#### Scenario: Smooth scroll reveals cards

- **WHEN** the user scrolls the carousel by any amount (wheel, scrollbar drag)
- **THEN** cards entering the visible area become rendered and interactive in the same frame, and cards leaving it become hidden

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
