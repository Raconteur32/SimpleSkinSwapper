## Why

The config screen is a hand-built `Screen` that only shows a command field for the currently connected server, and a static explanation text otherwise. There is no way to see, edit, add, or remove the set of configured servers at any time, and servers accumulate in the JSON config simply by being visited.

## What Changes

- Rebuild the config screen with YACL 3: the full list of configured servers is visible and editable at all times (connected or not), with native add / remove / reorder controls.
- Each list entry edits a server address + command pair via a custom controller (two text fields); removal uses YACL's native per-entry remove button (no dedicated clear button — emptying the command then saving deletes the entry).
- When connected, the current server's entry is presented first in the list; if it has no configured command, the entry is proposed with an empty command.
- **BREAKING (behavioral)**: empty commands are no longer persisted — saving prunes entries with a blank address or blank command, so clearing a command then saving deletes the entry. Existing empty entries inherited from older configs are pruned on first save.
- **BREAKING (behavioral)**: visited servers are no longer auto-registered in the config; `registerServerIfAbsent` and its connection hook are removed.
- The JSON save format itself is unchanged (same file, same `Map<String, String>` structure, no migration).

## Capabilities

### New Capabilities

- `config-screen`: the mod's configuration screen — an always-accessible, editable list of per-server skin commands, with current-server-first presentation, empty-command pruning on save, and an unchanged JSON save format.

### Modified Capabilities

## Impact

- **Dependencies**: new YACL dependency per Stonecutter version (`3.8.1+1.21.11-fabric`, `3.9.6+26.1-fabric`, `3.9.6+26.2-fabric`) via Xander Maven (recent builds are not on the Modrinth maven), as an **external dependency** declared in `fabric.mod.json` `depends` (`yet_another_config_lib_v3 >=3.8.1`) — not bundled jar-in-jar, per YACL's official guidance; `stonecutter.properties.toml` and `build.gradle.kts` updated.
- **Code**: `gui/ConfigScreen.kt` replaced by a YACL screen + custom controller/widget; `config/ModMenuCompat.kt` rewired to the YACL screen; `SimpleSkinSwapperConfig.registerServerIfAbsent` and the `ClientPlayConnectionEvents.JOIN` hook in `SimpleSkinSwapperClient.kt` removed. The custom element widget manages focus itself (explicit click-to-focus + a one-shot focus repair working around a vanilla `ContainerObjectSelectionList.Entry` quirk — no mixin required). The Gson config class and `getCommandForServer` consumers (`SkinChangeManager`) are untouched.
- **Resources**: `en_us.json` / `fr_fr.json` updated (new `simpleskinswapper.config.*` keys, obsolete `not_connected.*` keys removed).
