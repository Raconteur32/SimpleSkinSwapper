package fr.raconteur.simpleskinswapper.changeskin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import com.mojang.authlib.properties.Property
import fr.raconteur.simpleskinswapper.SimpleSkinSwapper
import fr.raconteur.simpleskinswapper.gui.SkinType
import fr.raconteur.simpleskinswapper.gui.SkinUtils
import fr.raconteur.simpleskinswapper.gui.library.SkinLifecycle
import fr.raconteur.simpleskinswapper.gui.library.SkinRecords
import fr.raconteur.simpleskinswapper.library.SkinRecord
import fr.raconteur.simpleskinswapper.library.TextureHashing
import fr.raconteur.simpleskinswapper.networking.MineSkinCache
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Base64
import java.util.UUID

object StartupSkinSync {

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    /** Mojang session profile: {"properties":[{"name":"textures","value":...,"signature":...}]}. */
    @Serializable
    private data class MojangPropertyDto(val name: String? = null, val value: String? = null, val signature: String? = null)

    @Serializable
    private data class MojangProfileDto(val properties: List<MojangPropertyDto>? = null)

    /** Decoded texture payload: {"textures":{"SKIN":{"url":...,"metadata":{"model":...}}}}. */
    @Serializable
    private data class SkinMetadataDto(val model: String? = null)

    @Serializable
    private data class SkinDto(val url: String? = null, val metadata: SkinMetadataDto? = null)

    @Serializable
    private data class TexturesDto(@SerialName("SKIN") val skin: SkinDto? = null)

    @Serializable
    private data class TexturePayloadDto(val textures: TexturesDto? = null)
    private val HTTP: HttpClient = HttpClient.newHttpClient()

    @JvmStatic
    fun run() {
        val thread = Thread(::sync, "SimpleSkinSwapper-StartupSync")
        thread.isDaemon = true
        thread.start()
    }

    // Deliberate total guard: one malformed response (IO, JSON shape, NPE on a missing
    // field) must not crash startup; log and keep the stored selection.
    @Suppress("TooGenericExceptionCaught")
    private fun sync() {
        try {
            val client = Minecraft.getInstance()
            val uuid = client.user.profileId

            val mojangProperty = fetchMojangProperty(uuid) ?: return

            val mojangUrl = extractSkinUrl(mojangProperty.value())
            if (mojangUrl == null) {
                SimpleSkinSwapper.LOGGER.warn("StartupSkinSync: could not extract skin URL from Mojang response.")
                return
            }

            val stored = SelectedSkinStore.get()
            val storedUrl = stored.map { extractSkinUrl(it.value()) }.orElse(null)
            if (mojangUrl != storedUrl) {
                SimpleSkinSwapper.LOGGER.info("StartupSkinSync: skin mismatch, updating stored selection.")
                SelectedSkinStore.set(mojangProperty)
            } else {
                SimpleSkinSwapper.LOGGER.info("StartupSkinSync: stored selection matches Mojang skin.")
            }

            // The library mirrors the online skin: match by canonical texture hash through
            // the registry, ingest on miss — unconditionally (the old flow skipped the
            // download when the stored selection already matched, leaving gaps possible).
            val mojangSkinType = extractSkinType(mojangProperty.value())
            val skinBytes = downloadUrl(mojangUrl) ?: return

            findRegistryMatch(skinBytes, mojangSkinType)?.let { record ->
                SimpleSkinSwapper.LOGGER.info("StartupSkinSync: matched local skin {}.", record.name)
                loadPreview(record.file, mojangSkinType)
                return
            }

            val created = SkinLifecycle.createSkin(skinBytes, mojangSkinType.mojangVariant, client.user.name)
            if (created == null) {
                SimpleSkinSwapper.LOGGER.warn("StartupSkinSync: online skin could not be ingested (undecodable).")
                return
            }
            SimpleSkinSwapper.LOGGER.info("StartupSkinSync: ingested the online skin as {}.", created.file)

            val outFile = FabricLoader.getInstance().gameDir.resolve("skins").resolve(created.file).toFile()
            val hash = MineSkinCache.fileHash(outFile)
            if (hash != null) {
                // Seeds the upload cache: re-applying this skin skips the MineSkin upload.
                MineSkinCache.put("${mojangSkinType.mojangVariant}_$hash", mojangProperty)
            }
            loadPreview(created.file, mojangSkinType)
        } catch (e: Exception) {
            SimpleSkinSwapper.LOGGER.warn("StartupSkinSync failed: {}", e.message)
        }
    }

    internal fun fetchMojangProperty(uuid: UUID): Property? {
        try {
            val uuidStr = uuid.toString().replace("-", "")
            val uri = URI.create("https://sessionserver.mojang.com/session/minecraft/profile/$uuidStr?unsigned=false")
            val req = HttpRequest.newBuilder(uri).GET().build()
            val resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString())
            if (resp.statusCode() != 200) {
                SimpleSkinSwapper.LOGGER.warn("StartupSkinSync: Mojang profile HTTP {}.", resp.statusCode())
                return null
            }
            val prop = json.decodeFromString(MojangProfileDto.serializer(), resp.body())
                .properties
                ?.firstOrNull { it.name == "textures" && it.value != null } ?: return null
            return Property("textures", prop.value, prop.signature)
        } catch (e: IOException) {
            SimpleSkinSwapper.LOGGER.warn("StartupSkinSync: fetchMojangProperty failed", e)
            return null
        } catch (e: SerializationException) {
            SimpleSkinSwapper.LOGGER.warn("StartupSkinSync: fetchMojangProperty failed", e)
            return null
        }
    }

    /**
     * Decode the base64 texture value and extract the skin texture URL.
     * The URL is content-addressed on Mojang's CDN, so identical PNGs share the same URL.
     */
    @JvmStatic
    fun extractSkinUrl(base64Value: String): String? {
        return try {
            json.decodeFromString(TexturePayloadDto.serializer(), String(Base64.getDecoder().decode(base64Value)))
                .textures?.skin?.url
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /** Model declared by the texture metadata ("slim"), or classic when absent. */
    @JvmStatic
    fun extractSkinType(base64Value: String): SkinType {
        try {
            val payload = json.decodeFromString(
                TexturePayloadDto.serializer(), String(Base64.getDecoder().decode(base64Value))
            )
            if (payload.textures?.skin?.metadata?.model == "slim") return SkinType.SLIM
        } catch (_: SerializationException) {
        } catch (_: IllegalArgumentException) {
        }
        return SkinType.CLASSIC
    }

    /** The registry skin whose texture content matches [skinBytes] with [type], or null. */
    private fun findRegistryMatch(skinBytes: ByteArray, type: SkinType): SkinRecord? {
        val value = TextureHashing.canonicalPixels(skinBytes) ?: return null
        val model = if (type == SkinType.SLIM) SkinRecord.MODEL_SLIM else SkinRecord.MODEL_CLASSIC
        val hash = TextureHashing.toHex(TextureHashing.sha256.digest(value))
        return SkinRecords.find(hash, model)
    }

    /** Loads the menu preview from a local library file (never persisted across launches). */
    private fun loadPreview(fileName: String, type: SkinType) {
        val file = FabricLoader.getInstance().gameDir.resolve("skins").resolve(fileName).toFile()
        SkinUtils.loadSkinTextureAsync(file, "selected_preview") { id ->
            SelectedSkinStore.setPreview(id, type)
        }
    }

    internal fun downloadUrl(url: String): ByteArray? {
        try {
            val req = HttpRequest.newBuilder(URI.create(url)).GET().build()
            val resp = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray())
            if (resp.statusCode() == 200) return resp.body()
            SimpleSkinSwapper.LOGGER.warn("StartupSkinSync: download HTTP {}.", resp.statusCode())
        } catch (e: IOException) {
            SimpleSkinSwapper.LOGGER.warn("StartupSkinSync: download failed", e)
        }
        return null
    }
}
