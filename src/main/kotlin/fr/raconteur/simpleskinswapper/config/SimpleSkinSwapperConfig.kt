package fr.raconteur.simpleskinswapper.config

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import fr.raconteur.simpleskinswapper.SimpleSkinSwapper
import net.minecraft.client.Minecraft
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException

class SimpleSkinSwapperConfig {

    /**
     * Per-server skin commands. Key = server address, value = command to send.
     * An empty string means no command is configured for that server.
     */
    @JvmField
    var serverCommands: MutableMap<String, String>? = defaultServerCommands()

    /** Side of the Options/Quit row for the skin-preview button on the title screen. */
    @JvmField
    var titleScreenButtonSide: ButtonSide? = ButtonSide.RIGHT

    /** Side of the Disconnect row for the skin-preview button on the pause menu. */
    @JvmField
    var pauseMenuButtonSide: ButtonSide? = ButtonSide.RIGHT

    @JvmField
    var enableMovingLegs: Boolean = true

    /** Non-null accessor for callers (e.g. Java mixins): defaults to RIGHT. */
    fun titleScreenSide(): ButtonSide = titleScreenButtonSide ?: ButtonSide.RIGHT

    /** Non-null accessor for callers (e.g. Java mixins): defaults to RIGHT. */
    fun pauseMenuSide(): ButtonSide = pauseMenuButtonSide ?: ButtonSide.RIGHT

    /**
     * Returns the command for the given server address, or null if the server is not registered.
     */
    fun getCommandForServer(address: String?): String? {
        if (address == null) return null
        return serverCommands?.get(address)
    }

    /**
     * Builds the editable list shown by the config screen.
     * When connected to a server, its entry comes first — proposed with an
     * empty command if it has none configured yet.
     */
    fun toServerCommandList(currentServerAddress: String?): MutableList<ServerCommand> {
        val commands = serverCommands ?: emptyMap()
        val list = mutableListOf<ServerCommand>()
        if (currentServerAddress != null) {
            list.add(ServerCommand(currentServerAddress, commands[currentServerAddress] ?: ""))
        }
        commands.forEach { (address, command) ->
            if (address != currentServerAddress) list.add(ServerCommand(address, command))
        }
        return list
    }

    /**
     * Applies the edited list back to the config: entries with a blank address or
     * a blank command are dropped; on duplicate addresses the last occurrence wins.
     */
    fun applyServerCommandList(entries: List<ServerCommand>) {
        val map = LinkedHashMap<String, String>()
        for (entry in entries) {
            val address = entry.address.trim()
            val command = entry.command.trim()
            if (address.isNotEmpty() && command.isNotEmpty()) {
                map[address] = command
            }
        }
        serverCommands = map
    }

    companion object {
        private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()

        @Volatile
        private var instance: SimpleSkinSwapperConfig? = null

        private fun defaultServerCommands(): MutableMap<String, String> {
            val map = LinkedHashMap<String, String>()
            map["example-server.com"] = "/reloadskin"
            return map
        }

        @JvmStatic
        fun get(): SimpleSkinSwapperConfig {
            return instance ?: load().also { instance = it }
        }

        @JvmStatic
        fun save() {
            val current = instance ?: return
            try {
                val configFile = getConfigFile()
                configFile.parentFile.mkdirs()
                FileWriter(configFile).use { writer ->
                    GSON.toJson(current, writer)
                }
            } catch (e: IOException) {
                SimpleSkinSwapper.LOGGER.error("Failed to save config: {}", e.message)
            }
        }

        private fun load(): SimpleSkinSwapperConfig {
            val configFile = getConfigFile()
            if (configFile.exists()) {
                try {
                    FileReader(configFile).use { reader ->
                        val loaded = GSON.fromJson(reader, SimpleSkinSwapperConfig::class.java)
                        if (loaded != null) {
                            if (loaded.serverCommands == null) {
                                loaded.serverCommands = defaultServerCommands()
                            }
                            if (loaded.titleScreenButtonSide == null) {
                                loaded.titleScreenButtonSide = ButtonSide.RIGHT
                            }
                            if (loaded.pauseMenuButtonSide == null) {
                                loaded.pauseMenuButtonSide = ButtonSide.RIGHT
                            }
                            return loaded
                        }
                    }
                } catch (e: Exception) {
                    // IOException (unreadable file) or JsonParseException (e.g. an invalid
                    // enum value in a hand-edited file): fall back to defaults.
                    SimpleSkinSwapper.LOGGER.error("Failed to load config: {}", e.message)
                }
            }
            return SimpleSkinSwapperConfig()
        }

        private fun getConfigFile(): File {
            return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config")
                .resolve("simpleskinswapper.json")
                .toFile()
        }
    }
}
