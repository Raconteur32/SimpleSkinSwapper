package fr.raconteur.simpleskinswapper.library

import fr.raconteur.simpleskinswapper.data.JsonFileStore
import fr.raconteur.simpleskinswapper.data.SkinLibraryEnv
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.nio.file.Files
import java.nio.file.Path

/**
 * One-shot migration of a legacy library (user-named pngs, names.json/types.json/
 * categories.json) into the registry model: hash-named textures, registry entries,
 * originals preserved untouched under `skins/User Files/`, category file lists remapped
 * to card references. The equipped-skin store (selected.json) holds a signed Mojang
 * property, not a library reference — it survives untouched. Marked done by the mere
 * existence of skins.json, so a second run is a no-op. A fresh [TextureNamer] is used
 * per file so hashes just written during the migration are always seen.
 */
class LibraryMigrator(
    private val env: SkinLibraryEnv,
    private val registry: SkinRegistry,
    private val cards: SkinCardStore,
    private val hasher: Hasher,
) {

    @Serializable
    internal data class LegacyCategoryDto(
        val name: String? = null,
        val color: String? = null,
        val maxWheels: Int? = null,
        val skins: List<String>? = null,
    )

    @Serializable
    internal data class LegacyCategoriesDto(val categories: List<LegacyCategoryDto>? = null)

    private val userFilesDir: Path get() = env.skinsDir().resolve(USER_FILES)

    /** True when legacy data is present and not yet migrated. */
    fun isNeeded(): Boolean {
        if (Files.exists(env.skinsDir().resolve("skins.json"))) return false
        val dir = env.skinsDir().toFile()
        val hasPngs = (dir.listFiles { _, n -> n.lowercase().endsWith(".png") } ?: emptyArray()).isNotEmpty()
        return hasPngs || Files.exists(env.skinsDir().resolve("categories.json"))
    }

    /** Runs the migration; returns the number of legacy files that produced (or merged
     *  into) a skin. No-op when [isNeeded] is false. */
    fun migrate(): Int {
        if (!isNeeded()) return 0
        val names = loadMap("names.json")
        val types = loadMap("types.json")
        val skinIdByFile = HashMap<String, String>()
        val dir = env.skinsDir()
        val legacyFiles = (dir.toFile().listFiles { _, n -> n.lowercase().endsWith(".png") } ?: emptyArray())
            .sortedBy { it.name }
        // Phase 1 — preserve every original FIRST: the root then holds only migrated
        // textures, so the namer can never "reuse" a legacy name for a hash name.
        val preserved: Map<String, Path> = legacyFiles.associate { file ->
            file.name to preserveOriginal(file.toPath())
        }
        // Phase 2 — ingest each preserved copy (bytes are the untouched originals).
        for ((name, preservedPath) in preserved) {
            val bytes = Files.readAllBytes(preservedPath)
            val value = TextureHashing.canonicalPixels(bytes) ?: continue
            val namer = TextureNamer(env, hasher)
            val hash = namer.fullHashHex(value)
            val target = namer.existingFileNameFor(value) ?: namer.uniqueFileNameFor(value)
            val model = if (types[name] == SkinRecord.MODEL_SLIM) SkinRecord.MODEL_SLIM else SkinRecord.MODEL_CLASSIC
            val baseName = name.removeSuffix(".png")
            val record = registry.create(hash, model, names[baseName] ?: baseName, target)
                ?: registry.find(hash, model)
            if (record != null) {
                skinIdByFile[name] = record.id
                Files.write(dir.resolve(record.file), bytes)
            }
        }
        migrateCategories(skinIdByFile)
        return skinIdByFile.size
    }

    /** Moves the original into `User Files/`, never overwriting an existing namesake;
     *  returns the preserved copy's path. */
    private fun preserveOriginal(original: Path): Path {
        Files.createDirectories(userFilesDir)
        val name = original.fileName.toString()
        var target = userFilesDir.resolve(name)
        var i = 2
        while (Files.exists(target)) {
            val base = if (name.endsWith(".png")) name.removeSuffix(".png") else name
            target = userFilesDir.resolve("$base ($i).png")
            i++
        }
        Files.move(original, target)
        return target
    }

    private fun migrateCategories(skinIdByFile: Map<String, String>) {
        val legacyFile = env.skinsDir().resolve("categories.json")
        if (!Files.exists(legacyFile)) return
        // A file already in the registry format (version marker) keeps its cards — a
        // re-migration must not wipe remapped memberships.
        if (categoriesFileHasVersion(legacyFile)) return
        val legacy = JsonFileStore(
            fileLabel = "categories.json",
            path = { legacyFile },
            serializer = LegacyCategoriesDto.serializer(),
            fresh = { LegacyCategoriesDto() },
        ).load()
        // Remove the legacy file BEFORE the card store's first load — otherwise its
        // ensureLoaded() pulls the legacy category in and createCategory duplicates it.
        Files.deleteIfExists(legacyFile)
        for (dto in legacy.categories ?: emptyList()) {
            val name = dto.name ?: continue
            val category = cards.createCategory(name, dto.color ?: SkinCardStore.DEFAULT_CATEGORY_COLOR)
            category.maxWheels = (dto.maxWheels ?: 0).coerceAtLeast(0)
            for (file in dto.skins ?: emptyList()) {
                val skinId = skinIdByFile[file] ?: continue
                cards.addCard(category, skinId)
            }
        }
    }

    /** True when categories.json already carries the registry-format version marker. */
    private fun categoriesFileHasVersion(file: Path): Boolean =
        JsonFileStore(
            fileLabel = "categories.json",
            path = { file },
            serializer = FormatProbeDto.serializer(),
            fresh = { FormatProbeDto() },
        ).load().version != null

    @Serializable
    internal data class FormatProbeDto(val version: Int? = null)

    private fun loadMap(label: String): Map<String, String> {
        val file = env.skinsDir().resolve(label)
        if (!Files.exists(file)) return emptyMap()
        return JsonFileStore(
            fileLabel = label,
            path = { file },
            serializer = MapSerializer(String.serializer(), String.serializer()),
            fresh = { linkedMapOf() },
        ).load()
    }

    companion object {
        const val USER_FILES = "User Files"
    }
}
