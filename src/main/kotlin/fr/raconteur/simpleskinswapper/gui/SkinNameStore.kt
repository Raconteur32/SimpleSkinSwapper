package fr.raconteur.simpleskinswapper.gui

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import fr.raconteur.simpleskinswapper.SimpleSkinSwapper
import net.fabricmc.loader.api.FabricLoader
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/** Persists per-skin display names (skins/names.json, filename -> display name). */
object SkinNameStore {

    private val GSON = GsonBuilder().setPrettyPrinting().create()
    private val MAP_TYPE = object : TypeToken<Map<String, String>>() {}.type

    private fun namesFile(): Path =
        FabricLoader.getInstance().gameDir.resolve("skins").resolve("names.json")

    private fun load(): MutableMap<String, String> {
        val path = namesFile()
        if (!Files.exists(path)) return HashMap()
        return try {
            val json = Files.readString(path)
            GSON.fromJson<MutableMap<String, String>>(json, MAP_TYPE) ?: HashMap()
        } catch (e: IOException) {
            SimpleSkinSwapper.LOGGER.warn("Could not read names.json: {}", e.message)
            HashMap()
        }
    }

    private fun save(map: Map<String, String>) {
        val path = namesFile()
        try {
            Files.createDirectories(path.parent)
            Files.writeString(path, GSON.toJson(map))
        } catch (e: IOException) {
            SimpleSkinSwapper.LOGGER.warn("Could not write names.json: {}", e.message)
        }
    }

    /** Returns the stored display name for a skin file, or null when it uses its file name. */
    @JvmStatic
    fun getName(filename: String): String? = load()[filename]

    /** Stores the display name for a skin file; a blank name removes the override. */
    @JvmStatic
    fun setName(filename: String, name: String) {
        val map = load()
        if (name.isBlank()) map.remove(filename) else map[filename] = name
        save(map)
    }

    /** Removes the stored name for a skin file, e.g. when the file is deleted. */
    @JvmStatic
    fun removeName(filename: String) {
        val map = load()
        if (map.remove(filename) != null) {
            save(map)
        }
    }

    /** Moves a stored name from the old file name to the new one after a file rename. */
    @JvmStatic
    fun renameKey(oldFilename: String, newFilename: String) {
        if (oldFilename == newFilename) return
        val map = load()
        val value = map.remove(oldFilename) ?: return
        map[newFilename] = value
        save(map)
    }
}
