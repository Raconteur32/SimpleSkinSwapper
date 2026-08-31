package fr.raconteur.simpleskinswapper.gui.library

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fr.raconteur.simpleskinswapper.SimpleSkinSwapper
import net.fabricmc.loader.api.FabricLoader
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

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

    private val GSON = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
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
        val root = JsonObject()
        val array = com.google.gson.JsonArray()
        for (category in categories) {
            val entry = JsonObject()
            entry.addProperty("name", category.name)
            entry.addProperty("color", category.colorHex)
            entry.addProperty("maxWheels", category.maxWheels)
            val skins = com.google.gson.JsonArray()
            for (name in category.skins) skins.add(name)
            entry.add("skins", skins)
            array.add(entry)
        }
        root.add("categories", array)
        try {
            Files.createDirectories(storeFile().parent)
            Files.writeString(storeFile(), GSON.toJson(root))
        } catch (e: IOException) {
            SimpleSkinSwapper.LOGGER.warn("Could not save skin categories: {}", e.message)
        }
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val file = storeFile()
        if (!Files.exists(file)) return
        try {
            val root = JsonParser.parseString(Files.readString(file)).asJsonObject
            val array = root.getAsJsonArray("categories") ?: return
            for (element in array) {
                val entry = element.asJsonObject
                val name = entry.get("name")?.asString ?: continue
                val color = entry.get("color")?.asString ?: SkinCategoryPalette.DEFAULT_HEX
                val maxWheels = entry.get("maxWheels")?.asInt ?: 0
                val skins = ArrayList<String>()
                entry.getAsJsonArray("skins")?.forEach { skins.add(it.asString) }
                categories.add(SkinCategory(name, color, maxWheels.coerceAtLeast(0), skins))
            }
        } catch (e: Exception) {
            SimpleSkinSwapper.LOGGER.warn("Could not read skin categories: {}", e.message)
            categories.clear()
        }
    }

    private fun storeFile(): Path = FabricLoader.getInstance().gameDir.resolve("skins").resolve("categories.json")
}
