# skin-library delta

## ADDED Requirements

### Requirement: The skin wheel always renders at least five slots

Each rendered skin wheel SHALL always present at least five sectors, regardless of how many skins it holds. Sectors beyond the wheel's real skins SHALL be rendered as empty filler: visually dimmed, without a skin preview, not hover-highlighted, not selectable, and excluded from the selection hit-test and tooltips. A wheel SHALL never render with fewer than five sectors. A category with a wheel allocation but no skins SHALL contribute no wheel at all. Scrolling between wheels and selecting a real slot by mouse angle at rest SHALL work unchanged.

#### Scenario: A sparse wheel keeps five slots

- **WHEN** a wheel holds fewer than five skins
- **THEN** it renders exactly five sectors: the real skins' sectors plus dimmed, inert filler sectors

#### Scenario: Filler slots are inert

- **WHEN** the user rests the mouse over a filler sector of a wheel at rest
- **THEN** nothing is selected or highlighted and no tooltip shows; clicking does nothing

#### Scenario: Full wheels are unchanged

- **WHEN** a wheel holds five or more skins
- **THEN** it renders one sector per skin exactly as before (up to the allocation's ten per wheel)

#### Scenario: Empty wheels stay hidden

- **WHEN** a category has a wheel allocation but no skins
- **THEN** that category contributes no wheel to the screen

## MODIFIED Requirements

### Requirement: The library screen shows a vertical category tab strip with an All skins tab

The library screen SHALL display a vertical tab strip on the left side of the window. The strip SHALL show an "All skins" tab as the first tab — a tab like the others: it scrolls with the strip and is never pinned to the screen — listing every skin in the skins folder with no configuration band, followed by one tab per category in category order. The strip SHALL always lay out at least five slots: "All skins" and "Uncategorized" in the first two positions and at least three category slots. Category slots without a real category SHALL render as ghost placeholders — visually recessed tabs that are not clickable, not selectable, not hover-highlighted, without tooltip, and never drop targets for card drags. The add-category entry SHALL sit directly after the last category slot — the sixth position when the three minimum category slots are shown — moving down as real categories fill the slots. Clicking a tab SHALL select it; right-clicking a tab SHALL also select it; hovering a tab SHALL show its name as a tooltip. The selected tab SHALL be visually distinct from the others and SHALL be clipped to the strip zone when scrolled out of it. The screen's chrome (tabs, page, cards, band) SHALL be dressed with the vanilla recipe-book sprites, with darkening baked into dedicated textures rather than applied at runtime over transparent regions.

#### Scenario: All skins is always first

- **WHEN** the library opens with any number of categories
- **THEN** the "All skins" tab appears before all category tabs in reading order and cannot be reordered or removed

#### Scenario: Selection by right click

- **WHEN** the user right-clicks a category tab
- **THEN** that category is selected, exactly as with a left click

#### Scenario: Tab strip overflows

- **WHEN** there are more categories than fit vertically in the strip
- **THEN** the strip scrolls (wheel over the strip) so every tab, All skins included, remains reachable; a selected tab scrolled out of the strip zone is clipped at the zone border

#### Scenario: Minimum five slots with no category

- **WHEN** the library opens with zero categories
- **THEN** the strip shows All skins, Uncategorized, three ghost category placeholder slots, and the add-category entry in the sixth position

#### Scenario: Placeholders shrink as categories grow

- **WHEN** one or two categories exist
- **THEN** the real category tabs occupy the first category slots and ghost placeholders fill the remaining slots up to three, with the add-category entry right after the last slot

#### Scenario: Placeholders are inert

- **WHEN** the user clicks, hovers, or drags a card over a ghost placeholder slot
- **THEN** nothing is selected or highlighted, no tooltip shows, and the drop is not accepted
