# Simple Skin Swapper

**Simple Skin Swapper** is a client-side Fabric mod for Minecraft 1.21.11 and 26.x that lets you switch between your local skin files on the fly — without restarting the game or navigating through external websites. Open a dynamic skin wheel with a keybind, hover over the skin you want, click, and you're done.

Supported versions: **1.21.11**, **26.1**, **26.2** (one jar per version, built from a single source tree with [Stonecutter](https://codeberg.org/stonecutter/stonecutter)).

---

## Features

- **Skin wheel** — a radial menu that displays up to 10 of your local skins at once, opened with a configurable keybind
- **Skin carousel** — a classic list view for browsing skins more carefully, or for accessing skins that don't fit on the wheel when you have more than 10
- **Multiplayer skin refresh** — other players see your new skin in real time without reconnecting; three options depending on your server setup:
  - install the mod server-side for native skin refresh support
  - install the [SkinShuffle Bridge](https://modrinth.com/plugin/skinshuffle) plugin on Paper servers for the same result
  - configure a per-server command as a fallback, if the server provides one (e.g. `/reloadskin`)

---

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/installer/), [Fabric API](https://modrinth.com/mod/fabric-api) and [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin)
2. Drop the mod `.jar` matching your Minecraft version into your `.minecraft/mods/` folder
3. Add your skin PNG files to `.minecraft/skins/`
4. Launch the game and bind the skin wheel key in Controls

---

## Configuration

Open the configuration screen via [ModMenu](https://modrinth.com/mod/modmenu) or from the carousel/wheel screen.

If the server does not have the SkinShuffle Bridge plugin, you can configure a command to send after applying a skin so other players see the change immediately. The server entry is created automatically on first connection with an empty command. Leave it empty to disable the feature for that server.

> **Note:** This configuration is not needed if the server has the SkinShuffle Bridge plugin installed — the mod will detect it automatically and use the native skin refresh packet instead.

**Config file location:** `.minecraft/config/simpleskinswapper.json`

```json
{
  "serverCommands": {
    "play.example.com": "/reloadskin",
    "another-server.net": ""
  }
}
```

---

## Usage

Both the skin wheel and the skin carousel can be opened with a configurable keybind. You can set them in **Options → Controls**, under the *Simple Skin Swapper* category.

<center>
  <img src="https://cdn.modrinth.com/data/kWMT8Yql/images/2e84e4de8eb2def89ff945cf21a5a3b3c282cc10.png" alt="Keybinds screen">
</center>

**Skin wheel:** hold your keybind to open the wheel, hover over the skin you want, and **click** to apply it. Releasing the keybind without clicking simply closes the wheel — no skin will be changed.

**Skin carousel:** open it by pressing its keybind once, or through ModMenu. Browse your skins and click the *Apply* button on the skin you want to switch to. You can also reorder skins using the arrow buttons — the order is saved, and it determines which skins appear on the wheel (the first 10 in the list).

<center>
  <img src="https://cdn.modrinth.com/data/kWMT8Yql/images/0c6e406889f39359e3dbde4ad60e372ca6a214d3.png" alt="Skin carousel screen">
</center>

**Adding skins:** drop any PNG skin file into the `.minecraft/skins/` folder (or the `skins/` folder of your instance if you use a custom launcher). A shortcut button to open that folder is available directly in the carousel screen.

---

## Development

Build instructions, multi-version setup and contribution notes live in [DEV.md](DEV.md).

---

## Acknowledgements

- [**cobrasrock**](https://modrinth.com/user/cobrasrock) for creating [SkinSwapper](https://modrinth.com/mod/skinswapper) under an open license that allowed me to reuse parts of its logic
- [**imb11**](https://modrinth.com/organization/imb11) for creating [SkinShuffle](https://modrinth.com/mod/skinshuffle), an outstanding mod that remains the most fully-featured in its category — and the one I would be using if not for the specific features I was looking for

---

## License

[GNU Lesser General Public License v3.0 or later](https://www.gnu.org/licenses/lgpl-3.0.html)
