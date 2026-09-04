package fr.raconteur.simpleskinswapper.data

import fr.raconteur.simpleskinswapper.SimpleSkinSwapper
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Typed JSON file persistence for small stores. `load()` returns [fresh] when the file is
 * missing, unreadable or corrupt (these files are hand-editable and must never crash the
 * client); `save()` writes through with pretty printing. Stores keep their load-mutate-save
 * rhythm on top of this. The runtime jar is provided by Fabric Language Kotlin.
 */
class JsonFileStore<T>(
    private val fileLabel: String,
    private val path: () -> Path,
    private val serializer: KSerializer<T>,
    private val fresh: () -> T,
) {

    fun load(): T {
        val file = path()
        if (!Files.exists(file)) return fresh()
        return try {
            JSON.decodeFromString(serializer, Files.readString(file))
        } catch (e: SerializationException) {
            SimpleSkinSwapper.LOGGER.warn("Could not read {}: {}", fileLabel, e.message)
            fresh()
        } catch (e: IOException) {
            SimpleSkinSwapper.LOGGER.warn("Could not read {}: {}", fileLabel, e.message)
            fresh()
        }
    }

    fun save(value: T) {
        val file = path()
        try {
            Files.createDirectories(file.parent)
            Files.writeString(file, JSON.encodeToString(serializer, value))
        } catch (e: IOException) {
            SimpleSkinSwapper.LOGGER.warn("Could not write {}: {}", fileLabel, e.message)
        }
    }

    private companion object {
        val JSON = Json { prettyPrint = true; ignoreUnknownKeys = true }
    }
}
