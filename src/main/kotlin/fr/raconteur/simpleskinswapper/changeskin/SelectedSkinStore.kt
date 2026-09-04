package fr.raconteur.simpleskinswapper.changeskin

import com.mojang.authlib.properties.Property
import fr.raconteur.simpleskinswapper.SimpleSkinSwapper
import fr.raconteur.simpleskinswapper.data.JsonFileStore
import fr.raconteur.simpleskinswapper.gui.SkinType
import kotlinx.serialization.Serializable
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.resources.Identifier
import java.util.Optional
import java.util.concurrent.Executors

object SelectedSkinStore {

    /** On-disk shape of selected.json (Gson era wrote "signature" as explicit null too). */
    @Serializable
    private data class SelectedSkinDto(val value: String? = null, val signature: String? = null)

    private val store = JsonFileStore(
        fileLabel = "selected.json",
        path = {
            FabricLoader.getInstance().gameDir.resolve("skins").resolve("selected.json")
        },
        serializer = SelectedSkinDto.serializer(),
        fresh = { SelectedSkinDto() },
    )

    private val SAVE_EXECUTOR = Executors.newSingleThreadExecutor { r ->
        Thread(r, "SimpleSkinSwapper-SkinStoreSave").apply { isDaemon = true }
    }

    @Volatile
    private var selectedProperty: Property? = null

    @Volatile
    private var loaded = false

    @Volatile
    private var previewTextureId: Identifier? = null

    @Volatile
    private var previewSkinType: SkinType? = null

    @JvmStatic
    fun get(): Optional<Property> {
        ensureLoaded()
        return Optional.ofNullable(selectedProperty)
    }

    @JvmStatic
    fun set(property: Property) {
        selectedProperty = property
        loaded = true
        SAVE_EXECUTOR.submit { store.save(SelectedSkinDto(property.value(), property.signature())) }
    }

    @JvmStatic
    fun setPreview(textureId: Identifier?, skinType: SkinType?) {
        previewTextureId = textureId
        previewSkinType = skinType
    }

    @JvmStatic
    fun getPreviewTexture(): Identifier? = previewTextureId

    @JvmStatic
    fun getPreviewSkinType(): SkinType? = previewSkinType

    @Synchronized
    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        // A file without a "value" (or corrupt/missing) means "no stored selection".
        val dto = store.load()
        val value = dto.value ?: return
        selectedProperty = Property("textures", value, dto.signature)
        SimpleSkinSwapper.LOGGER.info("SelectedSkinStore: loaded selected skin.")
    }
}
