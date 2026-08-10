package fr.raconteur.simpleskinswapper.gui

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import fr.raconteur.simpleskinswapper.SimpleSkinSwapper
import net.fabricmc.loader.api.FabricLoader
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

object SkinTypeStore {

    private val GSON = GsonBuilder().setPrettyPrinting().create()
    private val MAP_TYPE = object : TypeToken<Map<String, String>>() {}.type

    private fun typesFile(): Path =
        FabricLoader.getInstance().gameDir.resolve("skins").resolve("types.json")

    private fun load(): MutableMap<String, String> {
        val path = typesFile()
        if (!Files.exists(path)) return HashMap()
        return try {
            val json = Files.readString(path)
            GSON.fromJson<MutableMap<String, String>>(json, MAP_TYPE) ?: HashMap()
        } catch (e: IOException) {
            SimpleSkinSwapper.LOGGER.warn("Could not read types.json: {}", e.message)
            HashMap()
        }
    }

    private fun save(map: Map<String, String>) {
        val path = typesFile()
        try {
            Files.createDirectories(path.parent)
            Files.writeString(path, GSON.toJson(map))
        } catch (e: IOException) {
            SimpleSkinSwapper.LOGGER.warn("Could not write types.json: {}", e.message)
        }
    }

    /** Returns the stored type for a skin file, falling back to the auto-detected one. */
    @JvmStatic
    fun getType(filename: String, detected: SkinType): SkinType {
        val stored = load()[filename]
        if (stored == null) {
            // First access: persist the detected value
            setType(filename, detected)
            return detected
        }
        return if (stored == "slim") SkinType.SLIM else SkinType.CLASSIC
    }

    /** Stores the user-chosen type for a skin file. */
    @JvmStatic
    fun setType(filename: String, type: SkinType) {
        val map = load()
        map[filename] = type.mojangVariant
        save(map)
    }

    /** Removes the stored type for a skin file, e.g. when the file is deleted. */
    @JvmStatic
    fun removeType(filename: String) {
        val map = load()
        if (map.remove(filename) != null) {
            save(map)
        }
    }
}
