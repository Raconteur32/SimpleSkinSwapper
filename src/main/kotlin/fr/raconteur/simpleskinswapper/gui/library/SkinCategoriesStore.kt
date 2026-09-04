package fr.raconteur.simpleskinswapper.gui.library

import fr.raconteur.simpleskinswapper.data.JsonFileStore
import kotlinx.serialization.Serializable
import net.fabricmc.loader.api.FabricLoader

/** One user-defined skin group: display name, palette color, wheel allocation, ordered skin files. */
class SkinCategory(
    @JvmField var name: String,
    @JvmField var colorHex: String,
    @JvmField var maxWheels: Int,
    @JvmField val skins: ArrayList<String> = ArrayList()
)

/**
 * Persistence for skin categories: a single `skins/categories.json` holding one entry per
 * category with its own ordered skin file list. Every mutation is written through immediately.
 * The skins folder stays the source of truth for which files exist; dangling names are skipped
 * by consumers. The format intentionally allows the same file in several categories.
 */
object SkinCategoriesStore {

    /** On-disk shape of categories.json; fields stay lenient to match the Gson-era semantics. */
    @Serializable
    internal data class CategoryDto(
        val name: String? = null,
        val color: String? = null,
        val maxWheels: Int? = null,
        val skins: List<String>? = null,
    )

    @Serializable
    internal data class CategoriesFileDto(val categories: List<CategoryDto>? = null)

    private val store = JsonFileStore(
        fileLabel = "categories.json",
        path = { FabricLoader.getInstance().gameDir.resolve("skins").resolve("categories.json") },
        serializer = CategoriesFileDto.serializer(),
        fresh = { CategoriesFileDto() },
    )
    private val categories = ArrayList<SkinCategory>()
    private var loaded = false

    @JvmStatic
    fun all(): List<SkinCategory> {
        ensureLoaded()
        return categories
    }

    @JvmStatic
    fun categoryOf(fileName: String): SkinCategory? {
        ensureLoaded()
        return categories.firstOrNull { fileName in it.skins }
    }

    @JvmStatic
    fun addCategory(name: String, colorHex: String): SkinCategory {
        ensureLoaded()
        val category = SkinCategory(name, colorHex, 0)
        categories.add(category)
        save()
        return category
    }

    @JvmStatic
    fun removeCategory(category: SkinCategory) {
        ensureLoaded()
        if (categories.remove(category)) save()
    }

    @JvmStatic
    fun moveCategory(from: Int, to: Int) {
        ensureLoaded()
        if (from == to || from !in categories.indices || to !in categories.indices) return
        val category = categories.removeAt(from)
        categories.add(to, category)
        save()
    }

    /** Appends [fileName] to [category] and removes it from every other category. */
    @JvmStatic
    fun assignSkin(category: SkinCategory, fileName: String) {
        ensureLoaded()
        var changed = removeFromAll(fileName)
        if (fileName !in category.skins) {
            category.skins.add(fileName)
            changed = true
        }
        if (changed) save()
    }

    /** Removes [fileName] from every category (unassign), keeping the file itself untouched. */
    @JvmStatic
    fun removeFromAll(fileName: String): Boolean {
        ensureLoaded()
        var changed = false
        for (category in categories) {
            changed = category.skins.remove(fileName) || changed
        }
        if (changed) save()
        return changed
    }

    /** Renames a skin file in every category that references it, preserving order. */
    fun renameInAll(oldFileName: String, newFileName: String) {
        if (oldFileName == newFileName) return
        ensureLoaded()
        var changed = false
        for (category in categories) {
            val idx = category.skins.indexOf(oldFileName)
            if (idx >= 0) {
                category.skins[idx] = newFileName
                changed = true
            }
        }
        if (changed) save()
    }

    /**
     * Wheel composition: allocated categories in order, each contributing at most
     * `maxWheels * 10` file names from its ordered list. Categories with allocation 0
     * contribute nothing.
     */
    @JvmStatic
    fun wheelComposition(): List<Pair<SkinCategory, List<String>>> {
        ensureLoaded()
        val result = ArrayList<Pair<SkinCategory, List<String>>>()
        for (category in categories) {
            if (category.maxWheels > 0) {
                result += category to category.skins.take(category.maxWheels * 10)
            }
        }
        return result
    }

    @JvmStatic
    fun save() {
        store.save(CategoriesFileDto(categories.map {
            CategoryDto(name = it.name, color = it.colorHex, maxWheels = it.maxWheels, skins = it.skins.toList())
        }))
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        for (dto in store.load().categories ?: emptyList()) {
            // Entries without a name are skipped; the other fields fall back like before.
            val name = dto.name ?: continue
            val color = dto.color ?: SkinCategoryPalette.DEFAULT_HEX
            val maxWheels = (dto.maxWheels ?: 0).coerceAtLeast(0)
            categories.add(SkinCategory(name, color, maxWheels, ArrayList(dto.skins ?: emptyList())))
        }
    }
}
