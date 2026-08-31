# Config Screen Delta

## ADDED Requirements

### Requirement: Menu preview animation option

The config screen SHALL offer an option controlling whether the skin previews shown on the title screen and in the pause menu play the limb walk animation (arms and legs). The option SHALL be independent of the per-server commands settings, SHALL be persisted in the existing JSON config on save, SHALL survive restarts, and SHALL default to enabled. The option's label SHALL identify its scope as the menu skin previews.

#### Scenario: Disabled option freezes menu previews

- **WHEN** the user disables the menu preview animation option, saves, and looks at the title screen or pause menu skin preview
- **THEN** the previewed skin holds a static neutral pose with no limb animation

#### Scenario: Enabled option animates menu previews

- **WHEN** the menu preview animation option is enabled and the user looks at the title screen or pause menu skin preview
- **THEN** the previewed skin plays the limb walk animation

#### Scenario: Option survives restart

- **WHEN** the user disables the option, saves, and restarts the game
- **THEN** the option still reads disabled and the menu previews stay static

#### Scenario: Fresh config defaults to enabled

- **WHEN** the config file contains no value for the menu preview animation option
- **THEN** the option displays as enabled and menu previews animate
