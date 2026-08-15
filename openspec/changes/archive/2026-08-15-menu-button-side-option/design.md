# Design: menu-button-side-option

## Context

Two mixins (`MixinTitleScreen`, `MixinGameMenuScreen`) inject `SkinPreviewButton` at a hardcoded position right of a vanilla anchor button, found by translation key (`menu.quit`; `menu.disconnect`/`menu.returnToMenu`). On the title screen, the injected button takes the accessibility icon's spot and the icon is shifted right. The config is a Gson-backed Kotlin class (`SimpleSkinSwapperConfig`) with all fields having defaults (implicit no-arg constructor), edited through a YACL screen (`YaclConfigScreen`) that currently has one category. See proposal.md — Why.

## Goals / Non-Goals

**Goals:**

- Two persisted, independent enum values (`LEFT`/`RIGHT`, default `RIGHT`) driving placement, read fresh on every screen `init()`.
- Left placement is a strict geometric mirror of right placement on the same vanilla row.
- No config migration: files written by older versions load and behave identically to today.

**Non-Goals:**

- Screen-edge overflow handling at very small logical widths (pre-existing, symmetric — unchanged).
- Other placements (screen corners, free coordinates) or per-screen offsets.
- Hiding the button entirely (could be a future third option value; not requested).

## Decisions

### Side model: a single enum, two config fields

One `ButtonSide { LEFT, RIGHT }` enum; two fields `titleScreenButtonSide` / `pauseMenuButtonSide` defaulting to `RIGHT` on `SimpleSkinSwapperConfig`.

- Gson serializes enums as their name; the class's implicit no-arg constructor initializes fields to `RIGHT`, and Gson only overwrites fields present in the JSON — so old configs get `RIGHT` for free. Add a null-guard on load mirroring the existing `serverCommands` one, in case a hand-edited file sets the value to `null`.
- *Alternative considered:* two booleans ("button on the left"). Rejected: less readable in JSON, and the enum leaves room for future positions without a format change.

### Left placement mirrors the anchor, not the screen edge

Left means "left of the same vanilla row", computed from the anchor's coordinates — never an absolute screen-edge position. This keeps behavior consistent across window sizes and GUI scales and reuses the existing anchor-finding approach.

- Title screen: the anchor switches by side. Right anchors on Quit (`menu.quit`, current behavior); left anchors on Options (`menu.options`) and places the button at `options.x - 4 - buttonWidth`.
- Pause menu: same anchor both sides (`menu.disconnect`/`menu.returnToMenu`); left places at `exit.x - 4 - buttonWidth`.
- *Alternative considered:* detach from vanilla buttons and pin to the screen edge. Rejected during exploration (option B) — floating overlay behavior, more edge cases, no benefit asked for.

### Icon shifting stays symmetric

Right placement keeps the current behavior: shift any `SpriteIconButton` right of Quit (the accessibility icon) to the button's right edge + 4. Left placement applies the mirror test: shift any `SpriteIconButton` left of Options (the language icon) to the button's left edge - 4 - iconWidth. The pause menu has no adjacent icons, so nothing shifts there.

### YACL presentation: new category, two enum options

New `ConfigCategory` ("Menu buttons" / "Boutons de menu", new translation keys in `en_us.json`/`fr_fr.json`) with two options using YACL's enum controller (cycling LEFT/RIGHT), bound directly to the two config fields and written on the existing `save` callback. *Alternative considered:* a boolean tick-box per screen; rejected for the same reason as the boolean config model.

### Preview needs no changes

`SkinPreviewButton` renders the preview centered above itself from its own coordinates; moving the button moves the preview. No changes to `SkinPreviewButton`, `SkinRenderer`, or the carousel.

## Risks / Trade-offs

- [Anchor or adjacent icon absent due to another mod] → Keep the current fail-safe: if the anchor button is not found, inject nothing. Icon shifting is best-effort: only shift an icon actually found at the expected relative position, never move unrelated widgets.
- [Hand-edited JSON sets a side to an invalid string] → Gson throws on unknown enum constants during load; the existing `load()` catches `IOException` only. Catch broadly (mirroring the intent of falling back to defaults) so a bad value cannot prevent the config — and thus the mod — from loading.
- [Row grows wider on the left too, worsening tiny-width clipping] → Accepted: symmetric with the existing right-side behavior, explicitly out of scope.
