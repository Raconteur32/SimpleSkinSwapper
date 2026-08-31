# Skin Wheel Delta

## MODIFIED Requirements

### Requirement: Hovered sector shows an animated preview

Every skin preview on the wheel SHALL be rendered as a live 3D entity render each frame. Previews of non-hovered sectors SHALL hold a static neutral pose. The preview of the currently hovered sector SHALL play the limb walk animation (arms and legs), and when the hover moves away or between sectors, the previously animated preview's limbs SHALL return to the neutral pose through a smooth eased transition rather than freezing instantly. At most one sector SHALL be hovered at any time.

#### Scenario: Hovering a sector animates its preview

- **WHEN** the user moves the mouse over a sector
- **THEN** that sector's preview plays the limb walk animation while all other previews stay static neutral

#### Scenario: Moving hover between sectors

- **WHEN** the user moves the mouse from one sector to another
- **THEN** the previously hovered preview's limbs ease back to the neutral pose and the newly hovered preview animates

#### Scenario: Mouse at rest renders all previews statically

- **WHEN** the wheel is open and the mouse hovers no sector
- **THEN** all visible previews are rendered live in a static neutral pose

## REMOVED Requirements

### Requirement: Skin previews are baked to textures instead of rendered live

**Reason**: Product decision — all wheel previews are live 3D renders again so the hovered-sector animation no longer requires a baked/live swap. The bounded-baking and single-live-render performance rules no longer apply; up to one live render per sector per frame is an accepted cost.

**Migration**: None. Previews are simply rendered live every frame; the bake queue and blit path disappear.

### Requirement: Baked previews are cached globally, not per wheel screen

**Reason**: The bake pipeline this cache served is removed with the move back to fully live rendering.

**Migration**: None. No preview bake cache exists anymore; the shared-preview-cache requirement is obsolete.
