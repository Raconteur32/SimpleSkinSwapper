package fr.raconteur.simpleskinswapper.gui

import fr.raconteur.simpleskinswapper.data.FabricSkinLibraryEnv
import fr.raconteur.simpleskinswapper.data.JsonFileStore
import fr.raconteur.simpleskinswapper.data.SkinLibraryEnv
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/** Persists per-skin display names (skins/names.json, filename -> display name). */
class SkinNameStore(private val env: SkinLibraryEnv) {

    private val store = JsonFileStore(
        fileLabel = "names.json",
        path = { env.skinsDir().resolve("names.json") },
        serializer = MapSerializer(String.serializer(), String.serializer()),
        fresh = { linkedMapOf() },
    )

    /** Returns the stored display name for a skin file, or null when it uses its file name. */
    fun getName(filename: String): String? = store.load()[filename]

    /** Stores the display name for a skin file; a blank name removes the override. */
    fun setName(filename: String, name: String) {
        val map = store.load().toMutableMap()
        if (name.isBlank()) map.remove(filename) else map[filename] = name
        store.save(map)
    }

    /** Removes the stored name for a skin file, e.g. when the file is deleted. */
    fun removeName(filename: String) {
        val map = store.load().toMutableMap()
        if (map.remove(filename) != null) {
            store.save(map)
        }
    }

    /** Moves a stored name from the old file name to the new one after a file rename. */
    fun renameKey(oldFilename: String, newFilename: String) {
        if (oldFilename == newFilename) return
        val map = store.load().toMutableMap()
        val value = map.remove(oldFilename) ?: return
        map[newFilename] = value
        store.save(map)
    }
}

/** GUI-facing singleton delegating to the production-backed instance. */
object SkinNames {

    private val instance by lazy { SkinNameStore(FabricSkinLibraryEnv) }

    @JvmStatic fun getName(filename: String): String? = instance.getName(filename)
    @JvmStatic fun setName(filename: String, name: String) = instance.setName(filename, name)
    @JvmStatic fun removeName(filename: String) = instance.removeName(filename)
    @JvmStatic fun renameKey(oldFilename: String, newFilename: String) = instance.renameKey(oldFilename, newFilename)
}
