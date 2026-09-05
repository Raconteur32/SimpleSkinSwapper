package fr.raconteur.simpleskinswapper.library

import fr.raconteur.simpleskinswapper.data.JsonFileStore
import fr.raconteur.simpleskinswapper.data.SkinLibraryEnv
import kotlinx.serialization.Serializable

/**
 * One skin: a texture (referenced by its FULL content hash) paired with a model, plus a
 * display name. The identity is "<textureHash>_<model>" — two skins may share a texture
 * (one per model) but the registry never holds the same pair twice.
 */
class SkinRecord(
    @JvmField val textureHash: String,
    @JvmField val model: String,
    @JvmField var name: String,
) {
    val id: String get() = "${textureHash}_$model"

    companion object {
        const val MODEL_SLIM = "slim"
        const val MODEL_CLASSIC = "classic"
    }
}

/**
 * Persistence for the skin registry (`skins/skins.json`): the mod-managed list of skins,
 * written through on every mutation. The version field doubles as the migration marker —
 * a missing file means a legacy library that has not been migrated yet.
 */
class SkinRegistry(env: SkinLibraryEnv) {

    @Serializable
    internal data class SkinDto(
        val hash: String? = null,
        val model: String? = null,
        val name: String? = null,
    )

    @Serializable
    internal data class RegistryDto(
        val version: Int? = null,
        val skins: List<SkinDto>? = null,
    )

    private val store = JsonFileStore(
        fileLabel = "skins.json",
        path = { env.skinsDir().resolve("skins.json") },
        serializer = RegistryDto.serializer(),
        fresh = { RegistryDto() },
    )
    private val skins = ArrayList<SkinRecord>()
    private var loaded = false

    fun all(): List<SkinRecord> {
        ensureLoaded()
        return skins
    }

    fun findById(id: String): SkinRecord? = lookup(id)

    fun find(textureHash: String, model: String): SkinRecord? = lookup("\${textureHash}_\$model")

    /** Creates a skin; returns null when the (texture, model) pair already exists. */
    fun create(textureHash: String, model: String, name: String): SkinRecord? {
        ensureLoaded()
        if (skins.any { it.textureHash == textureHash && it.model == model }) return null
        val record = SkinRecord(textureHash, model, name)
        skins.add(record)
        save()
        return record
    }

    /** Stores the display name of a skin (blank resets it to unnamed). */
    fun rename(id: String, name: String) {
        ensureLoaded()
        val record = skins.firstOrNull { it.id == id } ?: return
        record.name = name
        save()
    }

    /** Removes a skin from the registry. The texture file itself is lifecycle's business. */
    fun remove(id: String): Boolean {
        ensureLoaded()
        val removed = skins.removeAll { it.id == id }
        if (removed) save()
        return removed
    }

    fun save() {
        store.save(RegistryDto(version = FORMAT_VERSION, skins = skins.map {
            SkinDto(hash = it.textureHash, model = it.model, name = it.name)
        }))
    }

    private fun lookup(id: String): SkinRecord? {
        ensureLoaded()
        return skins.firstOrNull { it.id == id }
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        for (dto in store.load().skins ?: emptyList()) {
            val hash = dto.hash ?: continue
            val model = dto.model ?: continue
            skins.add(SkinRecord(hash, model, dto.name ?: ""))
        }
    }

    companion object {
        const val FORMAT_VERSION = 1
    }
}
