# Proposal: menu-button-side-option

## Why

The skin-preview entry button is hardcoded to the right of the Quit button on the title screen and of the Disconnect button on the pause menu. Users with different layouts, languages with long labels, or other mods occupying that spot may want the button on the left instead — and the two screens deserve independent choices.

## What Changes

- Add two independent configuration options, persisted in the existing JSON config: the side (left or right, default right) of the skin-preview button on the **title screen** and on the **pause menu**.
- "Left" mirrors the current right placement on the same vanilla button row: on the title screen the button goes left of Options (and the language icon shifts further left, symmetric to the current accessibility-icon shift); on the pause menu it goes left of the Disconnect/Return-to-Menu button.
- Expose both options in the YACL config screen under a new category ("Menu buttons"), separate from the existing "Servers" category.
- Changes apply the next time each screen opens; no restart required.
- Screen-edge overflow at very small logical widths is unchanged (pre-existing, symmetric on both sides) — out of scope.

## Capabilities

### New Capabilities

- `menu-button`: placement and behavior of the skin-preview entry button injected into the title screen and pause menu — anchoring to the vanilla button row, preview rendered above the button, icon-shifting to make room, and the new per-screen configurable side.

### Modified Capabilities

- `config-screen`: the config screen gains a new category holding the two menu-button side options, in addition to the existing per-server commands category.

## Impact

- `SimpleSkinSwapperConfig.kt`: two new persisted enum fields (default `RIGHT`); old config files load unchanged (Gson keeps defaults for absent fields — no migration).
- `YaclConfigScreen.kt`: new category with two options; new translation keys in `en_us.json` and `fr_fr.json`.
- `MixinTitleScreen.java`, `MixinGameMenuScreen.java`: side-aware placement logic read from the config in `init()`.
- No new dependencies; no breaking changes.
