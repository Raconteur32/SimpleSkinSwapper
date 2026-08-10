package fr.raconteur.simpleskinswapper.gui

import fr.raconteur.simpleskinswapper.SimpleSkinSwapper
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.resources.Identifier
import java.io.File

/**
 * Represents a skin file entry in the carousel.
 * Lazily loads the GPU texture on first render.
 */
class SkinEntry(@JvmField val file: File) {

    @JvmField
    val displayName: String

    @JvmField
    var skinType: SkinType

    /** GPU texture identifier, null until loaded. */
    @JvmField
    var textureId: Identifier? = null

    @JvmField
    var textureLoading = false

    init {
        val detected = SkinUtils.detectSkinType(file)
        skinType = SkinTypeStore.getType(file.name, detected)

        // Display name: filename without extension
        val name = file.name
        displayName = if (name.endsWith(".png")) name.substring(0, name.length - 4) else name
    }

    /**
     * Ensure the GPU texture is loaded. Call from render thread.
     * No-op if already loading or loaded.
     */
    fun ensureTextureLoaded() {
        if (textureId != null || textureLoading) return
        textureLoading = true

        val sanitized = file.name
            .lowercase()
            .replace(Regex("[^a-z0-9_/.-]"), "_")
        val pathHash = "%08x".format(file.absolutePath.hashCode() and 0x7FFFFFFF)
        val key = "skin/entry_${sanitized}_$pathHash"

        SkinUtils.loadSkinTextureAsync(file, key) { id ->
            this.textureId = id
            SimpleSkinSwapper.LOGGER.debug("Loaded skin entry texture: {}", file.name)
        }
    }

    companion object {
        /**
         * Scan the game's skins/ directory and return all .png files as SkinEntry objects.
         */
        @JvmStatic
        fun loadSkins(): List<SkinEntry> {
            val skinsDir = FabricLoader.getInstance().gameDir.resolve("skins")
            val dir = skinsDir.toFile()
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val entries = ArrayList<SkinEntry>()
            val files = dir.listFiles { _, name -> name.lowercase().endsWith(".png") }
            if (files != null) {
                for (file in files) {
                    entries.add(SkinEntry(file))
                }
            }
            return entries
        }
    }
}
