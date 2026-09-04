package fr.raconteur.simpleskinswapper.gui

import fr.raconteur.simpleskinswapper.data.JsonFileStore
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import net.fabricmc.loader.api.FabricLoader

/** Persists per-skin display names (skins/names.json, filename -> display name). */
object SkinNameStore {

    private val store = JsonFileStore(
        fileLabel = "names.json",
        path = { FabricLoader.getInstance().gameDir.resolve("skins").resolve("names.json") },
        serializer = MapSerializer(String.serializer(), String.serializer()),
        fresh = { linkedMapOf() },
    )

    /** Returns the stored display name for a skin file, or null when it uses its file name. */
    @JvmStatic
    fun getName(filename: String): String? = store.load()[filename]

    /** Stores the display name for a skin file; a blank name removes the override. */
    @JvmStatic
    fun setName(filename: String, name: String) {
        val map = store.load().toMutableMap()
        if (name.isBlank()) map.remove(filename) else map[filename] = name
        store.save(map)
    }

    /** Removes the stored name for a skin file, e.g. when the file is deleted. */
    @JvmStatic
    fun removeName(filename: String) {
        val map = store.load().toMutableMap()
        if (map.remove(filename) != null) {
            store.save(map)
        }
    }

    /** Moves a stored name from the old file name to the new one after a file rename. */
    @JvmStatic
    fun renameKey(oldFilename: String, newFilename: String) {
        if (oldFilename == newFilename) return
        val map = store.load().toMutableMap()
        val value = map.remove(oldFilename) ?: return
        map[newFilename] = value
        store.save(map)
    }
}
