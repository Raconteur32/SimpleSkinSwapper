package fr.raconteur.simpleskinswapper.networking

import com.mojang.authlib.properties.Property
import fr.raconteur.simpleskinswapper.SimpleSkinSwapper
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import net.fabricmc.loader.api.FabricLoader
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

object MineSkinCache {
    /** One cached upload: texture value + optional signature, keyed by file hash. */
    @Serializable
    internal data class CacheEntryDto(val texture: String? = null, val signature: String? = null)

    private val CACHE_FILE = FabricLoader.getInstance().gameDir
        .resolve("skins").resolve("cache.json")
    private val json = kotlinx.serialization.json.Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), CacheEntryDto.serializer())

    private var cache: MutableMap<String, CacheEntryDto>? = null

    private fun load(): MutableMap<String, CacheEntryDto> {
        cache?.let { return it }
        val loaded = try {
            if (Files.exists(CACHE_FILE)) {
                json.decodeFromString(serializer, Files.readString(CACHE_FILE)).toMutableMap()
            } else {
                mutableMapOf()
            }
        } catch (e: SerializationException) {
            // A corrupt cache file degrades to an empty cache.
            SimpleSkinSwapper.LOGGER.warn("MineSkinCache: failed to load cache", e)
            mutableMapOf()
        } catch (e: IOException) {
            SimpleSkinSwapper.LOGGER.warn("MineSkinCache: failed to load cache", e)
            mutableMapOf()
        }
        cache = loaded
        return loaded
    }

    private fun save() {
        val data = cache ?: return
        try {
            Files.createDirectories(CACHE_FILE.parent)
            Files.writeString(CACHE_FILE, json.encodeToString(serializer, data))
        } catch (e: IOException) {
            SimpleSkinSwapper.LOGGER.warn("MineSkinCache: failed to save cache: {}", e.message)
        }
    }

    @JvmStatic
    fun fileHash(file: File): String? {
        // An unreadable file yields null (hashes are only a cache key).
        return try {
            val bytes = Files.readAllBytes(file.toPath())
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(bytes)
            val sb = StringBuilder()
            for (b in hash) sb.append("%02x".format(b))
            sb.toString()
        } catch (e: IOException) {
            SimpleSkinSwapper.LOGGER.warn("MineSkinCache: failed to hash file", e)
            null
        } catch (e: NoSuchAlgorithmException) {
            SimpleSkinSwapper.LOGGER.warn("MineSkinCache: failed to hash file", e)
            null
        }
    }

    @JvmStatic
    fun get(fileHash: String): Property? {
        val entry = load()[fileHash] ?: return null
        // A corrupt entry yields null and is re-fetched later.
        val texture = entry.texture ?: run {
            SimpleSkinSwapper.LOGGER.warn("MineSkinCache: corrupted entry for {}", fileHash)
            return null
        }
        SimpleSkinSwapper.LOGGER.info("MineSkinCache: cache hit for {}", fileHash.substring(0, 8))
        return Property("textures", texture, entry.signature)
    }

    @JvmStatic
    fun put(fileHash: String, property: Property) {
        val data = load()
        data[fileHash] = CacheEntryDto(property.value(), property.signature())
        save()
        SimpleSkinSwapper.LOGGER.info("MineSkinCache: cached entry for {}", fileHash.substring(0, 8))
    }
}
