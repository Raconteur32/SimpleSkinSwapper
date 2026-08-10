package fr.raconteur.simpleskinswapper.changeskin

import com.google.gson.GsonBuilder
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.mojang.authlib.properties.Property
import fr.raconteur.simpleskinswapper.SimpleSkinSwapper
import fr.raconteur.simpleskinswapper.gui.SkinType
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.resources.Identifier
import java.io.IOException
import java.nio.file.Files
import java.util.Optional
import java.util.concurrent.Executors

object SelectedSkinStore {

    private val FILE = FabricLoader.getInstance().gameDir
        .resolve("skins").resolve("selected.json")
    private val GSON = GsonBuilder().setPrettyPrinting().create()

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
        SAVE_EXECUTOR.submit { save(property) }
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
        try {
            if (!Files.exists(FILE)) return
            val content = Files.readString(FILE)
            val obj = GSON.fromJson(content, JsonObject::class.java)
            if (obj == null || !obj.has("value")) return
            val value = obj.get("value").asString
            val signature = if (obj.has("signature") && !obj.get("signature").isJsonNull)
                obj.get("signature").asString else null
            selectedProperty = Property("textures", value, signature)
            SimpleSkinSwapper.LOGGER.info("SelectedSkinStore: loaded selected skin.")
        } catch (e: Exception) {
            SimpleSkinSwapper.LOGGER.warn("SelectedSkinStore: failed to load: {}", e.message)
        }
    }

    private fun save(property: Property) {
        try {
            Files.createDirectories(FILE.parent)
            val obj = JsonObject()
            obj.addProperty("value", property.value())
            if (property.hasSignature()) {
                obj.addProperty("signature", property.signature())
            } else {
                obj.add("signature", JsonNull.INSTANCE)
            }
            Files.writeString(FILE, GSON.toJson(obj))
        } catch (e: IOException) {
            SimpleSkinSwapper.LOGGER.warn("SelectedSkinStore: failed to save: {}", e.message)
        }
    }
}
