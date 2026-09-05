package fr.raconteur.simpleskinswapper.library

import fr.raconteur.simpleskinswapper.data.SkinLibraryEnv
import java.nio.file.Files

/**
 * Texture lifecycle over the registry: a texture file is written only after the skin
 * creation is accepted (no orphans), shared by up to one skin per model, and deleted
 * when the last skin referencing it disappears. Validation against the folder prunes
 * skins whose texture vanished externally — callers cascade to their cards.
 */
class TextureLifecycle(
    private val env: SkinLibraryEnv,
    private val registry: SkinRegistry,
    private val namer: TextureNamer,
) {

    /** Creates a skin from raw PNG bytes: canonicalizes, reuses or writes the texture
     *  file, then registers the skin. Returns null when the bytes are undecodable or the
     *  (texture, model) pair already exists — in both cases nothing is ever written. */
    fun createSkin(png: ByteArray, model: String, name: String): SkinRecord? {
        val value = TextureHashing.canonicalPixels(png) ?: return null
        val hash = namer.fullHashHex(value)
        val existingFile = namer.existingFileNameFor(value)
        // Registry first: a refused pair must not leave a file behind.
        val record = registry.create(hash, model, name, existingFile ?: namer.uniqueFileNameFor(value))
            ?: return null
        if (existingFile == null) {
            val dir = env.skinsDir()
            Files.createDirectories(dir)
            Files.write(dir.resolve(record.file), png)
        }
        return record
    }

    /** Removes a skin; its texture file is deleted when no other skin references it.
     *  Returns whether the skin existed. */
    fun removeSkin(id: String): Boolean {
        val record = registry.findById(id) ?: return false
        val hash = record.textureHash
        val file = record.file
        registry.remove(id)
        if (file.isNotEmpty() && registry.all().none { it.textureHash == hash }) {
            Files.deleteIfExists(env.skinsDir().resolve(file))
        }
        return true
    }

    /** Removes every skin whose texture file no longer exists in the folder (external
     *  deletion); returns the ids so callers can purge their cards. */
    fun pruneMissingTextures(): List<String> {
        val dir = env.skinsDir().toFile()
        val present = (dir.listFiles { _, name -> name.lowercase().endsWith(".png") } ?: emptyArray())
            .mapTo(HashSet()) { it.name }
        val dead = registry.all().filter { it.file !in present }
        for (record in dead) registry.remove(record.id)
        return dead.map { it.id }
    }
}
