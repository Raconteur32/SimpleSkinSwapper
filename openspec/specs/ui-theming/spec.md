# UI Theming

## Purpose

The mod's own screens draw their decorative chrome exclusively from assets shipped in the mod's namespace, so a resource pack can retheme the mod's UI without overriding any vanilla asset, and vanilla asset changes cannot break the mod's rendering.

## Requirements

### Requirement: Mod screens draw chrome from mod-namespace assets only
The mod's screens (skin library, skin wheel, overlay panels) SHALL resolve every sprite and texture they draw from the `simpleskinswapper` namespace. No UI rendering code path SHALL reference an asset under the `minecraft` namespace.

#### Scenario: Resource pack rethemes the mod without touching vanilla
- **WHEN** a resource pack provides files only under `assets/simpleskinswapper/textures/gui/sprites/`
- **THEN** the mod's library screen chrome (tabs, cards, switch, dye swatches, heads) renders with the pack's versions
- **AND** no vanilla UI element (recipe book, buttons, items) is affected by that pack

#### Scenario: Vanilla asset rename does not break the mod
- **WHEN** any targeted Minecraft version renames or removes a vanilla sprite previously used by the mod (e.g. `recipe_book/overlay_recipe`)
- **THEN** the mod's screens still render their chrome from the mod-owned copies unchanged

### Requirement: Frame chrome uses three independently themable role sprites
The library screen SHALL draw its nine-slice frame chrome from three distinct mod-owned sprites, one per role: category tab strip entries (`library/tab`), hovered card highlight (`library/card_hover`), and the wide/slim switch knob (`library/switch_knob`). Each sprite SHALL be independently overridable by a resource pack.

#### Scenario: Pack overrides only one role
- **WHEN** a resource pack overrides `simpleskinswapper:library/card_hover` but not the other two frame sprites
- **THEN** only the card hover highlight changes
- **AND** tabs and the switch knob keep their shipped appearance

#### Scenario: Lit and unlit tab states share one sprite
- **WHEN** the tab strip renders selected, dragged, inactive, ghost and add-category entries
- **THEN** all of them are drawn from `simpleskinswapper:library/tab` (dimmed states via overlay, not a second asset)

### Requirement: Category dye swatches come from mod-owned sprites
The sixteen category color swatches SHALL be drawn from mod-owned sprites `simpleskinswapper:dye/<color>` (one per dye color), resolved through the standard GUI sprite atlas.

#### Scenario: All sixteen dye colors render
- **WHEN** the category palette shows its colors
- **THEN** each of the sixteen colors renders its mod-owned sprite through the GUI atlas
- **AND** the rendering path does not depend on the vanilla items atlas

### Requirement: Model switch heads are standalone head sprites
The wide/slim switch SHALL draw its two head icons from standalone mod-owned 8×8 sprites (`simpleskinswapper:library/head_wide`, `simpleskinswapper:library/head_slim`) rather than UV-cropped vanilla player skin textures.

#### Scenario: Head icons render identically
- **WHEN** the wide/slim switch is visible
- **THEN** each head icon shows the same face pixels as before the migration, drawn as a standard sprite blit with no manual UV cropping

### Requirement: Buttons hosted in vanilla screens keep vanilla sprites
Buttons the mod injects into vanilla screens (title screen, pause menu) and plain footer buttons of the library screen SHALL continue to use vanilla widget sprites. This requirement MAY be revisited per-screen later; vanilla-styled buttons inside vanilla screens are intentional, not an oversight.

#### Scenario: Title screen preview button follows vanilla theming
- **WHEN** a resource pack overrides vanilla `widget/button` sprites
- **THEN** the mod's preview button on the title and pause screens follows that pack, like every other button on those screens
