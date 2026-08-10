package fr.raconteur.simpleskinswapper.networking

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.mojang.authlib.properties.Property
import fr.raconteur.simpleskinswapper.SimpleSkinSwapper
import net.fabricmc.loader.api.FabricLoader
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest

object MineSkinCache {
    private val CACHE_FILE = FabricLoader.getInstance().gameDir
        .resolve("skins").resolve("cache.json")
    private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()

    private var cache: JsonObject? = null

    private fun load(): JsonObject {
        cache?.let { return it }
        var loaded: JsonObject? = null
        try {
            if (Files.exists(CACHE_FILE)) {
                val content = Files.readString(CACHE_FILE)
                loaded = GSON.fromJson(content, JsonObject::class.java)
            }
        } catch (e: Exception) {
            SimpleSkinSwapper.LOGGER.warn("MineSkinCache: failed to load cache: {}", e.message)
        }
        if (loaded == null) loaded = JsonObject()
        cache = loaded
        return loaded
    }

    private fun save() {
        try {
            Files.createDirectories(CACHE_FILE.parent)
            Files.writeString(CACHE_FILE, GSON.toJson(cache))
        } catch (e: IOException) {
            SimpleSkinSwapper.LOGGER.warn("MineSkinCache: failed to save cache: {}", e.message)
        }
    }

    @JvmStatic
    fun fileHash(file: File): String? {
        return try {
            val bytes = Files.readAllBytes(file.toPath())
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(bytes)
            val sb = StringBuilder()
            for (b in hash) sb.append("%02x".format(b))
            sb.toString()
        } catch (e: Exception) {
            SimpleSkinSwapper.LOGGER.warn("MineSkinCache: failed to hash file: {}", e.message)
            null
        }
    }

    @JvmStatic
    fun get(fileHash: String): Property? {
        val data = load()
        if (!data.has(fileHash)) return null
        return try {
            val entry = data.getAsJsonObject(fileHash)
            val value = entry.get("texture").asString
            val signature = if (entry.has("signature") && !entry.get("signature").isJsonNull)
                entry.get("signature").asString else null
            SimpleSkinSwapper.LOGGER.info("MineSkinCache: cache hit for {}", fileHash.substring(0, 8))
            Property("textures", value, signature)
        } catch (e: Exception) {
            SimpleSkinSwapper.LOGGER.warn("MineSkinCache: corrupted entry for {}: {}", fileHash, e.message)
            null
        }
    }

    @JvmStatic
    fun put(fileHash: String, property: Property) {
        val data = load()
        val entry = JsonObject()
        entry.addProperty("texture", property.value())
        if (property.hasSignature()) {
            entry.addProperty("signature", property.signature())
        } else {
            entry.add("signature", JsonNull.INSTANCE)
        }
        data.add(fileHash, entry)
        save()
        SimpleSkinSwapper.LOGGER.info("MineSkinCache: cached entry for {}", fileHash.substring(0, 8))
    }
}
