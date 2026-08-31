# Config Screen Delta

## ADDED Requirements

### Requirement: Remember last opened wheel option

The config screen SHALL offer an option controlling whether the skin wheel reopens at the position where it was last used in the current session. The option SHALL be persisted in the existing JSON config on save, SHALL survive restarts, and SHALL default to disabled.

#### Scenario: Option disabled returns to the first wheel

- **WHEN** the option is disabled, the user navigates to the third wheel, closes the wheel, and reopens it
- **THEN** the wheel opens on the first wheel

#### Scenario: Option enabled restores the last position

- **WHEN** the option is enabled, the user navigates to the third wheel, closes the wheel, and reopens it in the same session
- **THEN** the wheel opens on the third wheel

#### Scenario: Fresh config defaults to disabled

- **WHEN** the config file contains no value for this option
- **THEN** the option displays as disabled and the wheel opens on the first wheel
