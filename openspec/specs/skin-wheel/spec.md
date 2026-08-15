# Skin Wheel

## Purpose

The radial skin picker overlay: a hold-to-open wheel showing the user's first skins as pie sectors with 3D previews, where aiming with the mouse and releasing applies the highlighted skin.

## Requirements

### Requirement: Skin previews are baked to textures instead of rendered live

Each skin preview on the wheel SHALL be rendered once into an offscreen texture and then drawn as a simple textured blit each frame. The wheel SHALL NOT submit more than one live picture-in-picture entity render per frame. Baking SHALL be progressive (a bounded number of previews baked per frame) so opening the wheel never stalls on baking all previews at once.

#### Scenario: Wheel open with 10 skins, mouse at rest

- **WHEN** the wheel is open showing 10 skins and the mouse is not hovering any sector
- **THEN** all visible previews are drawn from baked textures and no live entity render is submitted for the wheel

#### Scenario: Opening the wheel does not stall

- **WHEN** the wheel opens with skins whose previews are not yet baked
- **THEN** at most a bounded number of previews are baked per frame, missing previews are simply not drawn until baked, and the screen remains responsive

#### Scenario: Preview texture reused across frames

- **WHEN** a skin's preview has been baked and the wheel remains open
- **THEN** subsequent frames draw the baked texture without re-rendering the 3D model for that skin

### Requirement: Hovered sector shows a live animated preview

The preview of the currently hovered sector SHALL be rendered as a live 3D entity render, so the idle animation plays on the hovered skin. At most one sector SHALL be hovered at any time, hence at most one live preview per frame.

#### Scenario: Hovering a sector animates its preview

- **WHEN** the user moves the mouse over a sector
- **THEN** that sector's preview becomes a live 3D render with idle animation, while all other previews remain baked textures

#### Scenario: Moving hover between sectors

- **WHEN** the user moves the mouse from one sector to another
- **THEN** the previously hovered preview returns to its baked texture and the newly hovered preview becomes the single live render

### Requirement: Pie sectors are drawn as meshes, not per-column fills

Wheel pie sectors SHALL be drawn as triangle meshes (one mesh per sector, or equivalent batched geometry) rather than one GUI fill per pixel column. The visual result (sector shape, gap angle, colors, hover highlight) SHALL remain unchanged.

#### Scenario: Wheel renders with full sector count

- **WHEN** the wheel is open with N sectors (1..10)
- **THEN** sector backgrounds are drawn with at most O(N) draw submissions per frame, independent of the wheel radius in pixels

#### Scenario: Hover highlight still works

- **WHEN** the user hovers a sector
- **THEN** that sector is drawn in the hover color and all other sectors keep the base color, exactly as before

### Requirement: Baked previews are cached globally, not per wheel screen

Baked preview textures SHALL be stored in a cache keyed by skin identity (file/texture), shared outside the wheel screen instance, so that a future layout with multiple simultaneously visible wheels reuses the same baked previews without per-screen duplication.

#### Scenario: Reopening the wheel

- **WHEN** the user closes and reopens the wheel with an unchanged skin library
- **THEN** previously baked previews are reused from the cache instead of being re-baked

#### Scenario: Cache survives multiple wheel instances

- **WHEN** more than one wheel is visible at once (future multi-wheel layout)
- **THEN** all wheels draw previews from the same shared cache and at most one live preview exists globally (single mouse cursor)
