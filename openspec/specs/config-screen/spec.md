# Config Screen

## Purpose

Provides the mod's configuration screen: an always-accessible, editable list of per-server skin commands and menu button placement options, persisted in the existing JSON config format.

## Requirements

### Requirement: Server list is editable at all times

The config screen SHALL display every configured server entry (address + command) and SHALL allow the user to edit, add, remove, and reorder entries whether or not the client is connected to a server.

#### Scenario: Opened while offline

- **WHEN** the user opens the config screen from the title screen (not connected to any server)
- **THEN** all configured server entries are displayed and can be edited, added, removed, and reordered

#### Scenario: Opened while connected

- **WHEN** the user opens the config screen while connected to a multiplayer server
- **THEN** all configured server entries are displayed and can be edited, added, removed, and reordered

#### Scenario: Add a new server while offline

- **WHEN** the user adds a list entry, fills in an address and a command, and saves
- **THEN** the new entry is persisted in the config

### Requirement: Current server is presented first

When the client is connected to a multiplayer server, the config screen SHALL present that server's entry as the first entry of the list. If the current server has no configured command, the screen SHALL propose the entry with an empty command.

#### Scenario: Connected server already configured

- **WHEN** the user opens the config screen while connected to a server that has a configured command
- **THEN** that server's entry, with its command filled in, is the first entry of the list

#### Scenario: Connected server not yet configured

- **WHEN** the user opens the config screen while connected to a server that has no configured command
- **THEN** an entry for that server with an empty command is proposed as the first entry of the list

### Requirement: Empty commands are not persisted

On save, the screen SHALL exclude from the persisted config every entry whose address is blank or whose command is blank. Entries with duplicate addresses SHALL be resolved so the config contains a single command per address, the last occurrence in the list winning.

#### Scenario: Entry left empty is not saved

- **WHEN** the user saves with an entry whose command is blank
- **THEN** that entry is absent from the persisted config

#### Scenario: Proposed current-server entry untouched

- **WHEN** the user opens the screen while connected to an unconfigured server and saves without filling its command
- **THEN** no entry for that server is persisted

#### Scenario: Clearing a command deletes the entry

- **WHEN** the user empties the command of an existing configured entry and saves
- **THEN** that server's entry is removed from the persisted config

#### Scenario: Duplicate addresses

- **WHEN** the user saves with two entries sharing the same non-blank address
- **THEN** the persisted config contains one entry for that address, with the command of the last occurrence in the list

### Requirement: JSON save format is unchanged

The config SHALL remain persisted as the same JSON file mapping server addresses to command strings, readable by previous versions of the mod, with no data migration. Entries with empty commands inherited from older configs SHALL be pruned on the first save.

#### Scenario: Config written by an older version loads

- **WHEN** the config file contains entries written by a previous version of the mod
- **THEN** the screen loads and displays all entries that have a non-empty command

#### Scenario: Legacy empty entries are pruned

- **WHEN** the config file contains entries with empty commands and the user saves from the config screen
- **THEN** those entries no longer appear in the written file

### Requirement: Config screen is accessible from the carousel screen

The skin carousel screen SHALL provide a button, placed to the right of the existing bottom buttons, that opens the config screen. Closing the config screen SHALL return to the carousel screen.

#### Scenario: Open config from the carousel

- **WHEN** the user clicks the config button at the bottom of the carousel screen
- **THEN** the config screen opens with the full server list

#### Scenario: Return to the carousel

- **WHEN** the user closes the config screen that was opened from the carousel
- **THEN** the carousel screen is displayed again

### Requirement: Visited servers are not auto-registered

Connecting to a server SHALL NOT create or modify any entry in the persisted config.

#### Scenario: Connect to a never-configured server

- **WHEN** the client connects to a server that has no configured command
- **THEN** the persisted config file is not modified

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
