# Menu Button

## Purpose

Injects a skin-preview entry button into the title screen and the pause menu, anchored to the vanilla bottom button row with a live preview of the selected skin rendered above it, opening the skin carousel on click.

## Requirements

### Requirement: Button is injected into the title screen and pause menu

The mod SHALL add a skin-preview button to the title screen and to the pause menu, placed on the same row as the vanilla bottom action buttons. Clicking the button SHALL open the skin carousel, and closing the carousel SHALL return to the screen it was opened from. If the vanilla anchor button cannot be found (e.g. another mod removed it), no button SHALL be injected and the screen SHALL remain otherwise unaffected.

#### Scenario: Title screen shows the button

- **WHEN** the title screen opens
- **THEN** the skin-preview button is present on the Options/Quit row

#### Scenario: Pause menu shows the button

- **WHEN** the pause menu opens
- **THEN** the skin-preview button is present beside the Disconnect (or Return to Menu) button

#### Scenario: Button opens the carousel

- **WHEN** the user clicks the skin-preview button on either screen
- **THEN** the skin carousel opens, and closing it returns to that screen

#### Scenario: Anchor button missing

- **WHEN** the screen's vanilla anchor button is absent
- **THEN** no button is injected and the screen renders normally

### Requirement: Selected skin is previewed above the button

The button SHALL render a preview of the currently selected skin directly above itself, horizontally centered on the button, with the model orientation following the mouse cursor. When no skin is selected, the preview SHALL be hidden and the button SHALL remain fully functional.

#### Scenario: Skin selected

- **WHEN** a skin is selected and the title screen or pause menu is open
- **THEN** the skin preview renders above the button and turns to follow the mouse

#### Scenario: No skin selected

- **WHEN** no skin is selected
- **THEN** the button renders without a preview and still opens the carousel on click

### Requirement: Button side is configurable per screen

The side of the button SHALL be configurable independently for the title screen and for the pause menu, with two values: right (default) or left. Right places the button to the right of the vanilla anchor button; left mirrors that placement to the left of the same button row. A side change SHALL take effect the next time the affected screen opens, without restarting the game.

#### Scenario: Default configuration

- **WHEN** the configuration has no side set (fresh install or config written by an older version)
- **THEN** the button appears on the right on both screens, exactly as before this change

#### Scenario: Title screen set to left

- **WHEN** the title screen side is set to left and the title screen opens
- **THEN** the button appears on the left of the Options/Quit row, with its preview above it, while the pause menu placement is unaffected

#### Scenario: Pause menu set to left

- **WHEN** the pause menu side is set to left and the pause menu opens
- **THEN** the button appears to the left of the Disconnect (or Return to Menu) button, with its preview above it, while the title screen placement is unaffected

#### Scenario: Change applies without restart

- **WHEN** the user changes a side option, saves, and reopens the affected screen
- **THEN** the button appears on the newly configured side

### Requirement: Neighboring vanilla icons make room for the button

On the title screen, the vanilla icon adjacent to the button SHALL be shifted outward so it does not overlap the injected button: the accessibility icon shifts to the right of the button when the button is on the right, and the language icon shifts to the left of the button when the button is on the left.

#### Scenario: Right placement shifts the accessibility icon

- **WHEN** the title screen side is right and the title screen opens
- **THEN** the accessibility icon sits immediately to the right of the injected button, with no overlap

#### Scenario: Left placement shifts the language icon

- **WHEN** the title screen side is left and the title screen opens
- **THEN** the language icon sits immediately to the left of the injected button, with no overlap
