package fr.raconteur.simpleskinswapper.library

import fr.raconteur.simpleskinswapper.data.SkinLibraryEnv
import java.nio.file.Files

/**
 * Decides the file name of a texture from its canonical value ([TextureHashing]).
 * Identity comparisons always use the FULL hash — the short prefix only shapes the file
 * name. When two different values collide on a prefix, the name lengthens until unique;
 * when the value already exists, its current file name is reused so the folder never
 * holds two files with the same texture. File writing stays with the caller (lifecycle).
 */
class TextureNamer(private val env: SkinLibraryEnv, private val hasher: Hasher) {

    /** Existing texture files: name -> full hash of their canonical pixels. */
    private val existing: Map<String, ByteArray> by lazy {
        val dir = env.skinsDir().toFile()
        val files = dir.listFiles { _, name -> name.lowercase().endsWith(".png") } ?: emptyArray()
        files.associate { file ->
            val value = TextureHashing.canonicalPixels(file.readBytes())
            file.name to hasher.digest(value ?: file.readBytes())
        }
    }

    /** The file name of an existing identical texture, or null when [value] is new. */
    fun existingFileNameFor(value: ByteArray): String? {
        val digest = hasher.digest(value)
        return existing.entries.firstOrNull { it.value.contentEquals(digest) }?.key
    }

    /** A unique, collision-safe file name for a texture value known to be new. */
    fun uniqueFileNameFor(value: ByteArray): String {
        val hex = TextureHashing.toHex(hasher.digest(value))
        var length = TextureHashing.MIN_PREFIX
        while (true) {
            val candidate = hex.take(length) + ".png"
            val owner = existing[candidate] ?: return candidate
            // Name taken by a different value (equal values never get here): lengthen.
            if (!owner.contentEquals(hasher.digest(value))) length += 2
        }
    }
}
