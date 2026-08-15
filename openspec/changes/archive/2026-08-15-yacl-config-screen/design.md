## Context

See proposal.md — Why. Current state: `gui/ConfigScreen.kt` is a hand-built `Screen` bound to `Minecraft.getInstance().currentServer`; `SimpleSkinSwapperConfig` persists `serverCommands: MutableMap<String, String>` via Gson in `config/simpleskinswapper.json`; `SimpleSkinSwapperClient` auto-registers visited servers on JOIN. Downstream, `SkinChangeManager` reads commands via `getCommandForServer(...)` and already treats absent and blank identically (`isNullOrBlank`). The project builds three MC versions with Stonecutter (1.21.11, 26.1.2, 26.2) and already pulls mods from the Modrinth maven.

## Goals / Non-Goals

**Goals:**
- YACL 3 config screen with a `ListOption` of address+command entries, editable at all times.
- Custom controller rendering each entry as two text fields (address, command) plus a clear button.
- Gson config class and JSON format stay the source of truth, unchanged.
- YACL is an external dependency (declared in `fabric.mod.json` `depends`), not bundled.

**Non-Goals:**
- Migrating to YACL's Config API (serialization stays the hand-rolled Gson code).
- Adding config options other than per-server commands (the YACL structure makes this easy later, but none are added now).
- Changing the skin-apply flow or `SkinChangeManager`.
- Preventing the user from reordering the pinned current-server entry during a session.

## Decisions

### D1 — `ListOption<ServerCommand>` with a custom controller (over string encoding or dynamic groups)

Each entry is a pair (address, command), but YACL's built-in controllers are all scalar. A custom `Controller<ServerCommand>` whose element widget holds two `EditBox` fields (address, command) gives the right UX with a clean model. Alternatives rejected: encoding the pair in one string (`"host|command"` — poor UX, fragile parsing); one `OptionGroup` per server generated at build time (add/remove is unnatural in YACL and reinvents `ListOption`).

### D2 — Conversion at the boundaries; the map stays the model

`ListOption` binds a `List`, the config stores a `Map`. At screen build: `map → List<ServerCommand>` with the current server first (see D3), remaining entries in map order. At save (YACL applies bindings then fires its save callback): `list → map`, dropping blank addresses and blank commands, last occurrence winning on duplicate addresses, then `SimpleSkinSwapperConfig.save()`. The `Map` is a `LinkedHashMap`, so the list order at save time persists — harmless.

### D3 — Current-server-first is initial presentation only

The initial list is built as `[current server entry (command or "")] + other configured entries` when connected, or just the configured entries when offline. The user can still move that entry with the native reorder buttons during the session; pinning it permanently is not worth the extra controller work.

### D4 — No clear button; native remove + empty-command pruning suffice (revised)

Initially a per-entry clear button complemented YACL's native remove, as the natural gesture on the pinned current-server row. Removed during implementation (user feedback): it took horizontal space for little value — emptying the command field then saving deletes the entry just the same (D2 pruning), and the native ✕ removes the row.

### D5 — Remove `registerServerIfAbsent` and the JOIN hook

With the current server always proposed on top when connected and empty commands no longer persisted, auto-registration has no UX purpose left and only pollutes the JSON with empty entries for every visited server. Removing it also self-cleans legacy configs on first save. Consequence accepted by the user: servers visited but never configured no longer appear in the list when offline.

### D6 — Dependency wiring per Stonecutter version

Add `deps.yacl` to each version block in `stonecutter.properties.toml`, consumed in `build.gradle.kts` as `modImplementation("dev.isxander:yet-another-config-lib:$yaclVersion")` from the Xander Maven repository (`https://maven.isxander.dev/releases`). Recent YACL builds are not synced to the Modrinth maven, so the official Xander Maven coordinate is used instead; `3.8.1+1.21.11-fabric` is the newest 3.8.x published there for 1.21.11 (3.8.2 only exists on the Modrinth CDN). YACL is **not** bundled (initially planned jar-in-jar, revised during implementation): the official YACL docs discourage jar-in-jar because the jar is heavy and YACL is usually already present in modpacks. It is declared as an external dependency in `fabric.mod.json` `depends` as `"yet_another_config_lib_v3": ">=3.8.1"` (the floor of the two API lines we compile against). ModMenu stays `modCompileOnly`; `ModMenuCompat` returns `YetAnotherConfigLib...generateScreen(parent)`.

### D7 — Focus is managed inside the widget; no mixin

Two focus problems surfaced during implementation, both worked around in `ServerCommandControllerElement` itself:

1. **Clicks don't focus the fields.** Since MC 26.x, clicking an `EditBox` no longer focuses it by itself (the container hierarchy is expected to propagate `setFocused`), and YACL's `ListEntryWidget` doesn't propagate it either. So the element's `mouseClicked` explicitly tracks `focusedChild` and sets `isFocused` on the clicked field — otherwise `canConsumeInput()` stays false and typing is silently ignored.
2. **Vanilla wipes the wrapper's focus right after the click.** `ContainerObjectSelectionList.Entry.setFocused(child)` unfocuses the previous child even when it *is* the new one; for a container child like `ListEntryWidget`, the `ContainerEventHandler` default `setFocused(false)` clears its focused child — wiping the focus re-acquired in `mouseClicked`. Since `OptionListWidget` routes `charTyped` to entries solely through `ListEntryWidget.getFocused()`, keystrokes then reach nothing. Because YACL instantiates `ListEntryWidget` (it can't be subclassed), the element re-asserts the wrapper's focus on the next render via a one-shot flag set in `mouseClicked`, locating the wrapper by walking the screen tree (`OptionListWidget` → `OptionEntry` → `ListEntryWidget`). The one-shot flag ensures focus is never stolen back from the entry's own remove/move buttons, which suffer the same vanilla wipe.

An initial attempt fixed this with a mixin on `ContainerObjectSelectionList.Entry`; it was removed in favor of the in-widget workaround — the fix is self-contained, and no mixin on a vanilla/YACL class is needed.

## Risks / Trade-offs

- [YACL API skew between 3.8.x (1.21.11) and 3.9.x (26.x), especially around custom `Controller`/element APIs] → Verify the custom controller compiles on both lines early (first implementation task); absorb differences with Stonecutter `//?` comments as the codebase already does for MC API changes.
- [Pruning empty entries on save is a one-way cleanup: a user who saved empty entries intentionally (as bookmarks) loses them] → Accepted: empty entries had no effect downstream (`isNullOrBlank`), and the proposal marks this as an intentional behavioral break.
- [Duplicate addresses in the list: silently resolved (last wins) rather than blocked by validation] → Deterministic and documented in the spec; revisit with inline validation only if users report confusion.
- [YACL is a new external dependency: players must have YACL installed or the mod refuses to load] → Accepted (user decision): YACL officially discourages jar-in-jar (jar size, and YACL is usually already present in modpacks); `fabric.mod.json` `depends` gives a clear loader error if missing.

## Migration Plan

No data migration: the JSON format is unchanged and old files load as-is. Legacy empty entries are pruned on the first save from the new screen. Rollback = revert the change; configs written by the new version remain readable by the old one (same format).

## Open Questions

None blocking. Exact YACL 3.8 vs 3.9 custom-controller API deltas will be discovered during the first implementation task and absorbed with Stonecutter comments without changing this design.
