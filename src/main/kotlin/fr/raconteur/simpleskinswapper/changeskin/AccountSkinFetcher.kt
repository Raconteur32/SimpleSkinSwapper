package fr.raconteur.simpleskinswapper.changeskin

import com.google.gson.Gson
import com.google.gson.JsonObject
import fr.raconteur.simpleskinswapper.SimpleSkinSwapper
import net.minecraft.client.Minecraft
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.function.Consumer

/**
 * Fetches a player's current skin from Mojang by account username and saves it into skins/.
 */
object AccountSkinFetcher {

    private val GSON = Gson()
    private val HTTP: HttpClient = HttpClient.newHttpClient()

    /**
     * Fetches the given account's current skin and writes it to `destination`.
     * The destination is chosen by the caller upfront so it can track the file before
     * the write happens (e.g. to tell a directory watcher to ignore its own creation event).
     */
    @JvmStatic
    fun fetch(username: String, destination: Path, onSuccess: Consumer<File>, onFailure: Runnable) {
        val thread = Thread({ run(username, destination, onSuccess, onFailure) }, "SimpleSkinSwapper-AccountFetch")
        thread.isDaemon = true
        thread.start()
    }

    private fun run(username: String, destination: Path, onSuccess: Consumer<File>, onFailure: Runnable) {
        val client = Minecraft.getInstance()
        try {
            val uuid = fetchUuid(username)
            if (uuid == null) {
                client.execute(onFailure)
                return
            }

            val property = StartupSkinSync.fetchMojangProperty(uuid)
            if (property == null) {
                client.execute(onFailure)
                return
            }

            val skinUrl = StartupSkinSync.extractSkinUrl(property.value())
            if (skinUrl == null) {
                client.execute(onFailure)
                return
            }

            val skinBytes = StartupSkinSync.downloadUrl(skinUrl)
            if (skinBytes == null) {
                client.execute(onFailure)
                return
            }

            Files.createDirectories(destination.parent)
            Files.write(destination, skinBytes)

            val file = destination.toFile()
            client.execute { onSuccess.accept(file) }
        } catch (e: IOException) {
            SimpleSkinSwapper.LOGGER.warn("AccountSkinFetcher failed: {}", e.message)
            client.execute(onFailure)
        }
    }

    // Deliberate total guard: any lookup/parsing failure yields null (log keeps the stack).
    @Suppress("TooGenericExceptionCaught")
    private fun fetchUuid(username: String): UUID? {
        return try {
            val uri = URI.create("https://api.mojang.com/users/profiles/minecraft/$username")
            val req = HttpRequest.newBuilder(uri).GET().build()
            val resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString())
            if (resp.statusCode() != 200) return null
            val body = GSON.fromJson(resp.body(), JsonObject::class.java)
            if (body == null || !body.has("id")) return null
            val raw = body.get("id").asString
            UUID.fromString(
                raw.replaceFirst(
                    Regex("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})"), "$1-$2-$3-$4-$5"
                )
            )
        } catch (ignored: Exception) {
            SimpleSkinSwapper.LOGGER.warn("AccountSkinFetcher: username lookup failed", ignored)
            null
        }
    }

    @JvmStatic
    fun sanitizeFilename(username: String): String =
        username.replace(Regex("[^a-zA-Z0-9_-]"), "_")

    @JvmStatic
    fun uniqueFile(path: Path): Path {
        if (!Files.exists(path)) return path
        val name = path.fileName.toString()
        val base = if (name.endsWith(".png")) name.substring(0, name.length - 4) else name
        val dir = path.parent
        var i = 2
        var candidate: Path
        do {
            candidate = dir.resolve("$base-$i.png")
            i++
        } while (Files.exists(candidate))
        return candidate
    }
}
