package fr.raconteur.simpleskinswapper.library

import fr.raconteur.simpleskinswapper.data.JsonFileStore
import fr.raconteur.simpleskinswapper.data.SkinLibraryEnv
import kotlinx.serialization.Serializable

/** One card: a reference to a skin inside a category, optionally carrying a per-category name. */
class CardEntry(
    @JvmField val skinId: String,
    @JvmField var name: String,
)

/** A category: display name, dye color, wheel allocation, and its ordered card list. */
class LibraryCategory(
    @JvmField var name: String,
    @JvmField var colorHex: String,
    @JvmField var maxWheels: Int,
    @JvmField val cards: ArrayList<CardEntry> = ArrayList(),
)

/**
 * Persistence for the registry-model categories (`skins/categories.json`): each category
 * holds an ordered list of card references. A skin may be referenced by several categories
 * but never twice within one; adding a card is additive (copy semantics — the source keeps
 * its own). Written through on every mutation.
 */
class SkinCardStore(env: SkinLibraryEnv) {

    @Serializable
    internal data class CardDto(
        val ref: String? = null,
        val name: String? = null,
    )

    @Serializable
    internal data class CategoryDto(
        val name: String? = null,
        val color: String? = null,
        val maxWheels: Int? = null,
        val cards: List<CardDto>? = null,
    )

    @Serializable
    internal data class CardsFileDto(val version: Int? = null, val categories: List<CategoryDto>? = null)

    private val store = JsonFileStore(
        fileLabel = "categories.json",
        path = { env.skinsDir().resolve("categories.json") },
        serializer = CardsFileDto.serializer(),
        fresh = { CardsFileDto() },
    )
    private val categories = ArrayList<LibraryCategory>()
    private var loaded = false

    fun all(): List<LibraryCategory> {
        ensureLoaded()
        return categories
    }

    fun createCategory(name: String, colorHex: String): LibraryCategory {
        ensureLoaded()
        val category = LibraryCategory(name, colorHex, 0)
        categories.add(category)
        save()
        return category
    }

    fun removeCategory(category: LibraryCategory) {
        ensureLoaded()
        if (categories.remove(category)) save()
    }

    fun moveCategory(from: Int, to: Int) {
        ensureLoaded()
        if (from == to || from !in categories.indices || to !in categories.indices) return
        val category = categories.removeAt(from)
        categories.add(to, category)
        save()
    }

    /** Appends a card for [skinId] at the end of [category]; no-op (false) when the
     *  category already holds a card for that skin. Other categories are untouched. */
    fun addCard(category: LibraryCategory, skinId: String): Boolean {
        ensureLoaded()
        if (category.cards.any { it.skinId == skinId }) return false
        category.cards.add(CardEntry(skinId, ""))
        save()
        return true
    }

    /** Removes the card for [skinId] from [category] only. */
    fun removeCard(category: LibraryCategory, skinId: String): Boolean {
        ensureLoaded()
        val removed = category.cards.removeAll { it.skinId == skinId }
        if (removed) save()
        return removed
    }

    /** Stores the per-category name of a card (blank clears the custom name). */
    fun setCardName(category: LibraryCategory, skinId: String, name: String) {
        ensureLoaded()
        val card = category.cards.firstOrNull { it.skinId == skinId } ?: return
        card.name = name
        save()
    }

    /** Every category holding a card for [skinId], in category order. */
    fun categoriesOf(skinId: String): List<LibraryCategory> {
        ensureLoaded()
        return categories.filter { category -> category.cards.any { it.skinId == skinId } }
    }

    fun save() {
        store.save(CardsFileDto(version = FORMAT_VERSION, categories = categories.map {
            CategoryDto(
                name = it.name,
                color = it.colorHex,
                maxWheels = it.maxWheels,
                cards = it.cards.map { card -> CardDto(ref = card.skinId, name = card.name) },
            )
        }))
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        for (dto in store.load().categories ?: emptyList()) {
            val name = dto.name ?: continue
            val color = dto.color ?: DEFAULT_CATEGORY_COLOR
            val maxWheels = (dto.maxWheels ?: 0).coerceAtLeast(0)
            val cards = ArrayList<CardEntry>()
            for (card in dto.cards ?: emptyList()) {
                val ref = card.ref ?: continue
                cards.add(CardEntry(ref, card.name ?: ""))
            }
            categories.add(LibraryCategory(name, color, maxWheels, cards))
        }
    }

    companion object {
        /** White dye's wool map color — the palette's default (kept here so the pure
         *  core never touches the Minecraft-backed palette object). */
        const val DEFAULT_CATEGORY_COLOR = "#F9FFFE"

        /** Marks categories.json as registry-format (the migrator's re-entry guard). */
        const val FORMAT_VERSION = 1
    }
}
