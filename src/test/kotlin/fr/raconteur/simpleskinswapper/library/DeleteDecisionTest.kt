package fr.raconteur.simpleskinswapper.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The delete dialog's contract: choices and context counts per source view. */
class DeleteDecisionTest {

    @Test
    fun `from a category with other categories offers both levels`() {
        val decision = DeleteDecision.of(DeleteSource.CATEGORY, totalCategories = 3)
        assertTrue(decision.offerRemoveCard)
        assertTrue(decision.offerDeleteEverywhere)
        assertEquals(2, decision.otherCategories)
        assertEquals(3, decision.totalCategories)
    }

    @Test
    fun `from a category where the skin is nowhere else hides the other-count`() {
        val decision = DeleteDecision.of(DeleteSource.CATEGORY, totalCategories = 1)
        assertTrue(decision.offerRemoveCard)
        assertEquals(0, decision.otherCategories)
    }

    @Test
    fun `from All skins only a full delete is offered with the occurrence count`() {
        val decision = DeleteDecision.of(DeleteSource.ALL_SKINS, totalCategories = 2)
        assertFalse(decision.offerRemoveCard)
        assertTrue(decision.offerDeleteEverywhere)
        assertEquals(2, decision.totalCategories)
    }

    @Test
    fun `from Uncategorized the delete is final and referenced nowhere`() {
        val decision = DeleteDecision.of(DeleteSource.UNCATEGORIZED, totalCategories = 5)
        assertFalse(decision.offerRemoveCard)
        assertEquals(0, decision.totalCategories)
        assertEquals(0, decision.otherCategories)
    }
}
