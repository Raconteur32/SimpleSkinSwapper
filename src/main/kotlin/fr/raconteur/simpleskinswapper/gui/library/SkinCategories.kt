package fr.raconteur.simpleskinswapper.gui.library

import fr.raconteur.simpleskinswapper.library.LibraryCategory

/** GUI-facing singleton over the card store: categories hold card references. */
object SkinCategories {

    private val instance get() = LibraryServices.cards

    @JvmStatic fun all(): List<LibraryCategory> = instance.all()
    @JvmStatic fun createCategory(name: String, colorHex: String): LibraryCategory = instance.createCategory(name, colorHex)
    @JvmStatic fun removeCategory(category: LibraryCategory) = instance.removeCategory(category)
    @JvmStatic fun moveCategory(from: Int, to: Int) = instance.moveCategory(from, to)

    /** Copy semantics: appends a card for [skinId]; other categories are untouched. */
    @JvmStatic fun addCard(category: LibraryCategory, skinId: String): Boolean = instance.addCard(category, skinId)
    @JvmStatic fun removeCard(category: LibraryCategory, skinId: String): Boolean = instance.removeCard(category, skinId)
    @JvmStatic fun categoriesOf(skinId: String): List<LibraryCategory> = instance.categoriesOf(skinId)
    @JvmStatic fun setCardName(category: LibraryCategory, skinId: String, name: String) =
        instance.setCardName(category, skinId, name)

    /** Wheel composition: allocated categories in order, each contributing at most
     *  `maxWheels * 10` skin ids from its card list. */
    @JvmStatic fun wheelComposition(): List<Pair<LibraryCategory, List<String>>> =
        instance.all().filter { it.maxWheels > 0 }
            .map { it to it.cards.take(it.maxWheels * 10).map { card -> card.skinId } }

    /** Removes every card for [skinId] (skin deleted or pruned). */
    @JvmStatic fun removeEverywhere(skinId: String): Boolean {
        var changed = false
        for (category in instance.all()) {
            changed = instance.removeCard(category, skinId) || changed
        }
        return changed
    }

    @JvmStatic fun save() = instance.save()
}
