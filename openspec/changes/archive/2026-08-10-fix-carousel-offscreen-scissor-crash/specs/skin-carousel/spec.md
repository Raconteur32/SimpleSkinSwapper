# Skin Carousel (delta)

## Purpose

Displays the user's skin library as a horizontally scrollable row of cards and lets the user preview, reorder, apply, and delete skins from it.

## ADDED Requirements

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
