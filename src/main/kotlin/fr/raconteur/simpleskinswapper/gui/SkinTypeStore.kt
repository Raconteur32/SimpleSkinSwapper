package fr.raconteur.simpleskinswapper.gui

import fr.raconteur.simpleskinswapper.data.JsonFileStore
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import net.fabricmc.loader.api.FabricLoader

object SkinTypeStore {

    private val store = JsonFileStore(
        fileLabel = "types.json",
        path = { FabricLoader.getInstance().gameDir.resolve("skins").resolve("types.json") },
        serializer = MapSerializer(String.serializer(), String.serializer()),
        fresh = { linkedMapOf() },
    )

    /** Returns the stored type for a skin file, falling back to the auto-detected one. */
    @JvmStatic
    fun getType(filename: String, detected: SkinType): SkinType {
        val stored = store.load()[filename]
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
        val map = store.load().toMutableMap()
        map[filename] = type.mojangVariant
        store.save(map)
    }

    /** Removes the stored type for a skin file, e.g. when the file is deleted. */
    @JvmStatic
    fun removeType(filename: String) {
        val map = store.load().toMutableMap()
        if (map.remove(filename) != null) {
            store.save(map)
        }
    }

    /** Moves a stored type from the old file name to the new one after a file rename. */
    @JvmStatic
    fun renameType(oldFilename: String, newFilename: String) {
        if (oldFilename == newFilename) return
        val map = store.load().toMutableMap()
        val value = map.remove(oldFilename) ?: return
        map[newFilename] = value
        store.save(map)
    }
}
