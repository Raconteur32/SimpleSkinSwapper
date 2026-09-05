package fr.raconteur.simpleskinswapper.gui

import fr.raconteur.simpleskinswapper.data.FabricSkinLibraryEnv
import fr.raconteur.simpleskinswapper.data.JsonFileStore
import fr.raconteur.simpleskinswapper.data.SkinLibraryEnv
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/** Persists per-skin model types (skins/types.json, filename -> "slim"|"classic"). */
class SkinTypeStore(private val env: SkinLibraryEnv) {

    private val store = JsonFileStore(
        fileLabel = "types.json",
        path = { env.skinsDir().resolve("types.json") },
        serializer = MapSerializer(String.serializer(), String.serializer()),
        fresh = { linkedMapOf() },
    )

    /** Returns the stored type for a skin file, falling back to the auto-detected one. */
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
    fun setType(filename: String, type: SkinType) {
        val map = store.load().toMutableMap()
        map[filename] = type.mojangVariant
        store.save(map)
    }

    /** Removes the stored type for a skin file, e.g. when the file is deleted. */
    fun removeType(filename: String) {
        val map = store.load().toMutableMap()
        if (map.remove(filename) != null) {
            store.save(map)
        }
    }

    /** Moves a stored type from the old file name to the new one after a file rename. */
    fun renameType(oldFilename: String, newFilename: String) {
        if (oldFilename == newFilename) return
        val map = store.load().toMutableMap()
        val value = map.remove(oldFilename) ?: return
        map[newFilename] = value
        store.save(map)
    }
}

/** GUI-facing singleton delegating to the production-backed instance. */
object SkinTypes {

    private val instance by lazy { SkinTypeStore(FabricSkinLibraryEnv) }

    @JvmStatic fun getType(filename: String, detected: SkinType): SkinType = instance.getType(filename, detected)
    @JvmStatic fun setType(filename: String, type: SkinType) = instance.setType(filename, type)
    @JvmStatic fun removeType(filename: String) = instance.removeType(filename)
    @JvmStatic fun renameType(oldFilename: String, newFilename: String) = instance.renameType(oldFilename, newFilename)
}
