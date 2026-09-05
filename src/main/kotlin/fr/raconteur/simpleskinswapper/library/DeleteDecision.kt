package fr.raconteur.simpleskinswapper.library

/** Where a delete was requested from — drives the dialog's choices and dynamic text. */
enum class DeleteSource { ALL_SKINS, UNCATEGORIZED, CATEGORY }

/** A discrete action the delete popup offers for this decision. */
enum class DeleteAction { REMOVE_CARD_HERE, DELETE_EVERYWHERE }

/**
 * The pure decision behind the delete dialog: which actions exist and what the context
 * line says. Text rendering and lang keys stay in the GUI; this is the tested contract.
 */
data class DeleteDecision(
    /** The view offers "remove this card only" (leaves the skin and its other cards). */
    val offerRemoveCard: Boolean,
    /** The view offers "delete the skin everywhere" (registry + texture + all cards). */
    val offerDeleteEverywhere: Boolean,
    /** Categories holding the skin besides the current view's (drives "also in X other
     *  categories"). 0 means referenced nowhere else. */
    val otherCategories: Int,
    /** Total category references of the skin (drives "removes occurrences in X categories"). */
    val totalCategories: Int,
    ) {
        /** The popup's action buttons in display order. A category view collapses to a
         *  single definitive delete when this card is the skin's last location — removing
         *  it there would be the same thing. Cancel is added by the popup, not this. */
        fun actions(): List<DeleteAction> =
            if (offerRemoveCard && otherCategories > 0)
                listOf(DeleteAction.REMOVE_CARD_HERE, DeleteAction.DELETE_EVERYWHERE)
            else
                listOf(DeleteAction.DELETE_EVERYWHERE)

        companion object {
        fun of(source: DeleteSource, totalCategories: Int): DeleteDecision = when (source) {
            DeleteSource.ALL_SKINS -> DeleteDecision(
                offerRemoveCard = false,
                offerDeleteEverywhere = true,
                otherCategories = 0,
                totalCategories = totalCategories,
            )
            DeleteSource.UNCATEGORIZED -> DeleteDecision(
                offerRemoveCard = false,
                offerDeleteEverywhere = true,
                otherCategories = 0,
                totalCategories = 0,
            )
            DeleteSource.CATEGORY -> DeleteDecision(
                offerRemoveCard = true,
                offerDeleteEverywhere = true,
                otherCategories = (totalCategories - 1).coerceAtLeast(0),
                totalCategories = totalCategories,
            )
        }
    }
}
