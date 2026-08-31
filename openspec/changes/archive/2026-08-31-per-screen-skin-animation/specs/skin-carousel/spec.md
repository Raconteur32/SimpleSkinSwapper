# Skin Carousel Delta

## ADDED Requirements

### Requirement: Card previews animate on hover and settle back smoothly

Carousel card previews SHALL hold a static neutral pose by default. While the mouse hovers a card, that card's preview SHALL play the limb walk animation (arms and legs). When the hover ends, the animated limbs SHALL return to the neutral pose through a smooth eased transition rather than freezing instantly. Drag-to-rotate behavior SHALL remain available and unchanged.

#### Scenario: Idle cards are static

- **WHEN** the carousel is open and the mouse hovers no card
- **THEN** every visible card preview holds a static neutral pose

#### Scenario: Hovering a card animates it

- **WHEN** the user moves the mouse over a card
- **THEN** that card's preview plays the limb walk animation while other cards stay static

#### Scenario: Leaving a card settles smoothly

- **WHEN** the mouse leaves a previously hovered card
- **THEN** the card's limbs ease back to the neutral pose over a short time instead of snapping or freezing mid-pose

#### Scenario: Drag-to-rotate still works

- **WHEN** the user drags a card's preview to rotate it and releases
- **THEN** the preview rotates around the model as before and springs back to the initial orientation, independently of the hover animation

#### Scenario: The dragged card stays animated while dragging

- **WHEN** the user drag-rotates a card's preview and the cursor moves outside that card, or over other cards
- **THEN** the dragged card keeps playing the limb walk animation, the other cards stay static, and normal hover animation resumes for all cards after the drag is released
