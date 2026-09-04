# Skin Categories

## Purpose

Persist the user's skin categories: each category holds a name, a palette color, a wheel allocation and its own ordered skin list, so the library and the wheel can be organized and composed per group.

## Requirements

### Requirement: Categories persist in a single store with per-category ordered skin lists

Categories SHALL be persisted as a single JSON store containing one entry per category, each entry holding its name, color, wheel allocation, and its own ordered list of skin file names. The store SHALL be written through whenever a category is created, renamed, recolored, reallocated, reordered, or its skin list changes. The per-category lists SHALL be the ordering source of truth for the category; the directory listing of the skins folder SHALL NOT determine the order inside a category.

#### Scenario: First launch before any category exists

- **WHEN** the mod loads and no category store file exists
- **THEN** the category list is empty, no file is created until a change is made, and all skins remain visible in the library's "All skins" view

#### Scenario: Reorder is persisted

- **WHEN** the user reorders skins within a category or reorders categories themselves
- **THEN** the new order is written to the store immediately and survives a game restart

#### Scenario: Referenced skin file disappears

- **WHEN** a skin file referenced by a category list no longer exists in the skins folder
- **THEN** the entry is skipped without errors, and the category's other entries and their order are preserved

### Requirement: A category holds a name, a palette color, and a wheel allocation

Each category SHALL have a non-empty name, a color taken from a fixed palette of pastel and vivid color pairs across ten hues, and a wheel allocation (an integer, 0 or more). Colors SHALL NOT be freely chosen: the library SHALL only offer palette colors.

#### Scenario: Color choice is constrained to the palette

- **WHEN** the user sets a category color
- **THEN** the chosen color is one of the offered palette colors, pastel or vivid

#### Scenario: Allocation is a plain count

- **WHEN** the user changes a category's wheel allocation
- **THEN** the stored allocation is a non-negative integer, including 0

### Requirement: Removing a skin from a category never deletes the skin file

Unassigning a skin from a category, or deleting a category, SHALL only affect category membership and order. The skin file itself SHALL remain in the skins folder and SHALL remain reachable through the "All skins" view.

#### Scenario: Category deletion

- **WHEN** the user deletes a category after confirmation
- **THEN** the category disappears from the store and its skins become uncategorized, still present in the skins folder

### Requirement: The store format allows a skin in several categories

The category store format SHALL NOT prevent the same skin file from appearing in the ordered list of more than one category, so that shared membership can be enabled later without a format migration.

#### Scenario: Same file referenced twice

- **WHEN** a skin file is present in two categories' lists
- **THEN** the store persists both memberships without error or deduplication
