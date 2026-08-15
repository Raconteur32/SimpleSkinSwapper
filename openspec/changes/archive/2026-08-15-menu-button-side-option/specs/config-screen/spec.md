# Config Screen (delta)

## ADDED Requirements

### Requirement: Menu button side options

The config screen SHALL provide a category, distinct from the per-server commands category, containing two independent options: the skin-preview button side on the title screen and on the pause menu. Each option SHALL offer exactly two values — right and left — with right as the default. The chosen values SHALL be persisted in the existing JSON config on save and SHALL survive restarts.

#### Scenario: Options are visible in their own category

- **WHEN** the user opens the config screen
- **THEN** a category separate from Servers shows the title-screen side and pause-menu side options with their current values

#### Scenario: Saving persists the sides

- **WHEN** the user sets the title screen side to left, saves, and reopens the config screen
- **THEN** the title screen side still shows left, and the persisted config file contains that value

#### Scenario: Config written by an older version

- **WHEN** the config file contains no side values (written before this change)
- **THEN** both options display right as their value, and saving does not alter any other config content
