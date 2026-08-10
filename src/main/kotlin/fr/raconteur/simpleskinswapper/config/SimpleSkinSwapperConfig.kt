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

    /**
     * Returns the command for the given server address, or null if the server is not registered.
     */
    fun getCommandForServer(address: String?): String? {
        if (address == null) return null
        return serverCommands?.get(address)
    }

    /**
     * Adds an entry with an empty command for the server if not already registered,
     * then persists the config.
     */
    fun registerServerIfAbsent(address: String?) {
        if (address == null) return
        val commands = serverCommands ?: return
        if (!commands.containsKey(address)) {
            commands[address] = ""
            save()
        }
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
                            return loaded
                        }
                    }
                } catch (e: IOException) {
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
