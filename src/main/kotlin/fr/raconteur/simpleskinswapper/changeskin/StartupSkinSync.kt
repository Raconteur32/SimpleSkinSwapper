package fr.raconteur.simpleskinswapper.changeskin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import com.mojang.authlib.properties.Property
import fr.raconteur.simpleskinswapper.SimpleSkinSwapper
import fr.raconteur.simpleskinswapper.gui.SkinEntry
import fr.raconteur.simpleskinswapper.gui.SkinType
import fr.raconteur.simpleskinswapper.gui.SkinUtils
import fr.raconteur.simpleskinswapper.networking.MineSkinCache
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
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
            var matchesStored = false
            if (stored.isPresent) {
                val storedUrl = extractSkinUrl(stored.get().value())
                matchesStored = mojangUrl == storedUrl
            }

            if (!matchesStored) {
                SimpleSkinSwapper.LOGGER.info("StartupSkinSync: skin mismatch, updating stored selection.")
                SelectedSkinStore.set(mojangProperty)
            } else {
                SimpleSkinSwapper.LOGGER.info("StartupSkinSync: stored selection matches Mojang skin.")
            }

            // Preview texture is never persisted, so it must be reloaded from a matching local file every launch.
            val mojangSkinType = extractSkinType(mojangProperty.value())
            val matchingEntry = findMatchingEntry(mojangUrl)

            if (matchingEntry != null) {
                SimpleSkinSwapper.LOGGER.info("StartupSkinSync: matched existing file {}.", matchingEntry.displayName)
                val skinType = matchingEntry.skinType
                SkinUtils.loadSkinTextureAsync(matchingEntry.file, "selected_preview") { id ->
                    SelectedSkinStore.setPreview(id, skinType)
                }
                return
            }

            if (matchesStored) {
                SimpleSkinSwapper.LOGGER.info("StartupSkinSync: no local file to preview, skipping download.")
                return
            }

            val skinBytes = downloadUrl(mojangUrl) ?: return

            val pseudo = client.user.name
            val timestamp = SimpleDateFormat("dd-MM-yyyy-HH-mm-ss").format(Date())
            val skinsDir = FabricLoader.getInstance().gameDir.resolve("skins")
            Files.createDirectories(skinsDir)
            val outFile = skinsDir.resolve("$pseudo-$timestamp.png")
            Files.write(outFile, skinBytes)
            SimpleSkinSwapper.LOGGER.info("StartupSkinSync: saved Mojang skin as {}.", outFile.fileName)

            val hash = MineSkinCache.fileHash(outFile.toFile())
            if (hash != null) {
                MineSkinCache.put("${mojangSkinType.mojangVariant}_$hash", mojangProperty)
            }

            SkinUtils.loadSkinTextureAsync(outFile.toFile(), "selected_preview") { id ->
                SelectedSkinStore.setPreview(id, mojangSkinType)
            }
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

    private fun extractSkinType(base64Value: String): SkinType {
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

    private fun findMatchingEntry(mojangUrl: String): SkinEntry? {
        val entries = SkinEntry.loadSkins()
        for (entry in entries) {
            val hash = MineSkinCache.fileHash(entry.file) ?: continue
            for (variant in listOf("classic", "slim")) {
                val cached = MineSkinCache.get("${variant}_$hash") ?: continue
                val cachedUrl = extractSkinUrl(cached.value())
                if (mojangUrl == cachedUrl) return entry
            }
        }
        return null
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
