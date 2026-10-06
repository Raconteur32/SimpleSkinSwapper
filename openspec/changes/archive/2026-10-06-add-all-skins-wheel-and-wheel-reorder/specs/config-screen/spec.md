## ADDED Requirements

### Requirement: All skins wheel options

The config screen SHALL offer, in the skin-wheel group, an All skins wheel option selecting when the wheel shows an all-skins group — Never, Fallback, or Always, with Fallback as the default — and a max all-skins wheels option bounding how many all-skins wheels are shown when category wheels exist, defaulting to 2 and bounded between 1 and 5. Both options SHALL be persisted in the existing JSON config, survive restarts, and take effect on subsequent wheel openings.

#### Scenario: Fresh config shows the defaults

- **WHEN** the config file contains no value for these options and the config screen is opened
- **THEN** the All skins wheel option displays Fallback and the max all-skins wheels option displays 2

#### Scenario: Changing the mode persists

- **WHEN** the user sets the All skins wheel option to Always, saves, and reopens the config screen
- **THEN** the option still shows Always and the persisted config file contains that value

#### Scenario: Changing the bound persists within its limits

- **WHEN** the user sets the max all-skins wheels option to 4, saves, and restarts the game
- **THEN** the option still reads 4 and the wheel shows at most four all-skins wheels when category wheels exist

#### Scenario: Options take effect on the next opening

- **WHEN** the user changes either option and then opens the skin wheel
- **THEN** the wheel composition follows the new values
