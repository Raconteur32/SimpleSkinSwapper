package fr.raconteur.simpleskinswapper.changeskin

import com.google.gson.Gson
import com.google.gson.JsonObject
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

    private val GSON = Gson()
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

    // Deliberate total guard: a failed profile lookup returns null (log keeps the stack).
    @Suppress("TooGenericExceptionCaught")
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
            val body = GSON.fromJson(resp.body(), JsonObject::class.java)
            if (!body.has("properties")) return null
            for (el in body.getAsJsonArray("properties")) {
                val prop = el.asJsonObject
                if (prop.get("name").asString == "textures") {
                    val value = prop.get("value").asString
                    val sig = if (prop.has("signature") && !prop.get("signature").isJsonNull)
                        prop.get("signature").asString else null
                    return Property("textures", value, sig)
                }
            }
        } catch (ignored: Exception) {
            SimpleSkinSwapper.LOGGER.warn("StartupSkinSync: fetchMojangProperty failed", ignored)
        }
        return null
    }

    /**
     * Decode the base64 texture value and extract the skin texture URL.
     * The URL is content-addressed on Mojang's CDN, so identical PNGs share the same URL.
     */
    @JvmStatic
    // Malformed base64 or unexpected JSON shape yields null by contract (content-addressed URL lookup).
    @Suppress("TooGenericExceptionCaught")
    fun extractSkinUrl(base64Value: String): String? {
        return try {
            val json = String(Base64.getDecoder().decode(base64Value))
            val obj = GSON.fromJson(json, JsonObject::class.java)
            obj.getAsJsonObject("textures")
                .getAsJsonObject("SKIN")
                .get("url").asString
        } catch (_: Exception) {
            null
        }
    }

    private fun extractSkinType(base64Value: String): SkinType {
        try {
            val json = String(Base64.getDecoder().decode(base64Value))
            val obj = GSON.fromJson(json, JsonObject::class.java)
            val skin = obj.getAsJsonObject("textures").getAsJsonObject("SKIN")
            if (skin.has("metadata")) {
                val model = skin.getAsJsonObject("metadata").get("model").asString
                if (model == "slim") return SkinType.SLIM
            }
        } catch (ignored: Exception) {
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
