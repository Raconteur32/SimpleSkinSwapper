# Tasks: menu-button-side-option

## 1. Config model

- [x] 1.1 Add a `ButtonSide` enum (`LEFT`, `RIGHT`) in the config package
- [x] 1.2 Add `titleScreenButtonSide` and `pauseMenuButtonSide` fields to `SimpleSkinSwapperConfig`, both defaulting to `ButtonSide.RIGHT`
- [x] 1.3 On load, null-guard the two new fields back to `RIGHT` (mirroring the existing `serverCommands` null-guard) and broaden the load catch so an invalid enum value in hand-edited JSON falls back to defaults instead of failing to load

## 2. Config screen

- [x] 2.1 Add a new YACL category ("Menu buttons") in `YaclConfigScreen`, after the Servers category
- [x] 2.2 Add two options with an enum cycling controller (LEFT/RIGHT): "Title screen button side" bound to `titleScreenButtonSide`, "Pause menu button side" bound to `pauseMenuButtonSide`, persisted by the existing `save` callback
- [x] 2.3 Add the new translation keys (category name, both option names and descriptions) to `en_us.json` and `fr_fr.json`

## 3. Placement logic

- [x] 3.1 In `MixinTitleScreen`, read `titleScreenButtonSide` in `init()`: keep the current Quit-anchored placement for `RIGHT`; for `LEFT`, anchor on the Options button (`menu.options`), place the button at `options.x - 4 - buttonWidth`, and shift any `SpriteIconButton` left of Options (language icon) to the button's left edge minus gap, mirroring the accessibility-icon shift
- [x] 3.2 In `MixinGameMenuScreen`, read `pauseMenuButtonSide` in `init()`: keep current placement for `RIGHT`; for `LEFT`, place the button at `exit.x - 4 - buttonWidth`
- [x] 3.3 Verify no changes are needed to `SkinPreviewButton` (preview follows the button position)

## 4. Verification

- [x] 4.1 Build all Stonecutter versions (`1.21.11`, `26.1.2`, `26.2`) successfully
- [x] 4.2 In game: with a fresh/old config, confirm the button is on the right on both screens (unchanged behavior)
- [x] 4.3 In game: set title side to LEFT, save, reopen the title screen — button is left of Options, language icon shifted left, preview above the button; pause menu unchanged
- [x] 4.4 In game: set pause side to LEFT, save, open the pause menu — button is left of Disconnect; title screen unchanged
- [x] 4.5 Confirm the persisted `simpleskinswapper.json` contains the two new fields and that the config screen shows the saved values after restart
