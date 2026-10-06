package fr.raconteur.simpleskinswapper.library

import fr.raconteur.simpleskinswapper.data.SkinLibraryEnv
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/** Cards reference skins: additive across categories, unique within one, persistent. */
class SkinCardStoreTest {

    @TempDir
    lateinit var dir: Path

    private fun store() = SkinCardStore(SkinLibraryEnv { dir })

    @Test
    fun `adding a card appends and persists`() {
        val store = store()
        val category = store.createCategory("PvP", "white")
        store.addCard(category, "abc_slim")
        val reloaded = store()
        val cards = reloaded.all().single().cards
        assertEquals(listOf("abc_slim"), cards.map { it.skinId })
    }

    @Test
    fun `a category never holds the same skin twice`() {
        val category = store().createCategory("PvP", "white")
        assertTrue(store().addCard(category, "abc_slim"))
        assertFalse(store().addCard(category, "abc_slim"))
        assertEquals(1, category.cards.size)
    }

    @Test
    fun `a skin may be referenced by several categories`() {
        val store = store()
        val a = store.createCategory("A", "white")
        val b = store.createCategory("B", "white")
        store.addCard(a, "abc_slim")
        store.addCard(b, "abc_slim")
        assertEquals(listOf("A", "B"), store.categoriesOf("abc_slim").map { it.name })
    }

    @Test
    fun `removing a card keeps the other categories`() {
        val store = store()
        val a = store.createCategory("A", "white")
        val b = store.createCategory("B", "white")
        store.addCard(a, "abc_slim")
        store.addCard(b, "abc_slim")
        store.removeCard(a, "abc_slim")
        assertEquals(listOf("B"), store.categoriesOf("abc_slim").map { it.name })
        assertTrue(store().categoriesOf("abc_slim").map { it.name } == listOf("B"))
    }

    @Test
    fun `per-category card names persist`() {
        val store = store()
        val category = store.createCategory("PvP", "white")
        store.addCard(category, "abc_slim")
        store.setCardName(category, "abc_slim", "Hero")
        val card = store().all().single().cards.single()
        assertEquals("Hero", card.name)
    }

    @Test
    fun `category order can move and persists`() {
        val store = store()
        store.createCategory("A", "white")
        store.createCategory("B", "white")
        store.moveCategory(1, 0)
        assertEquals(listOf("B", "A"), store().all().map { it.name })
        assertNotNull(store().all().first())
    }

    /** One store holding one category with cards a, b, c, d. */
    private fun fourCards(): Pair<SkinCardStore, LibraryCategory> {
        val s = store()
        val category = s.createCategory("PvP", "white")
        listOf("a_slim", "b_slim", "c_slim", "d_slim").forEach { s.addCard(category, it) }
        return s to category
    }

    @Test
    fun `moving a card before a pivot lands right before it`() {
        val (s, category) = fourCards()
        assertTrue(s.moveCardBefore(category, "c_slim", "a_slim"))
        assertEquals(listOf("c_slim", "a_slim", "b_slim", "d_slim"), category.cards.map { it.skinId })
    }

    @Test
    fun `moving a card before its next neighbor changes nothing observable`() {
        val (s, category) = fourCards()
        assertTrue(s.moveCardBefore(category, "a_slim", "b_slim"))
        assertEquals(listOf("a_slim", "b_slim", "c_slim", "d_slim"), category.cards.map { it.skinId })
    }

    @Test
    fun `moving a card after a pivot lands right behind it`() {
        val (s, category) = fourCards()
        assertTrue(s.moveCardAfter(category, "a_slim", "c_slim"))
        assertEquals(listOf("b_slim", "c_slim", "a_slim", "d_slim"), category.cards.map { it.skinId })
    }

    @Test
    fun `moving a card after its previous neighbor changes nothing observable`() {
        val (s, category) = fourCards()
        assertTrue(s.moveCardAfter(category, "b_slim", "a_slim"))
        assertEquals(listOf("a_slim", "b_slim", "c_slim", "d_slim"), category.cards.map { it.skinId })
    }

    @Test
    fun `a wheel move persists across instances`() {
        val (s, category) = fourCards()
        s.moveCardBefore(category, "d_slim", "b_slim")
        assertEquals(listOf("a_slim", "d_slim", "b_slim", "c_slim"), store().all().single().cards.map { it.skinId })
    }

    @Test
    fun `moves with a missing card or pivot are no-ops`() {
        val (s, category) = fourCards()
        assertFalse(s.moveCardBefore(category, "ghost_slim", "a_slim"))
        assertFalse(s.moveCardBefore(category, "a_slim", "ghost_slim"))
        assertFalse(s.moveCardBefore(category, "a_slim", "a_slim"))
        assertEquals(listOf("a_slim", "b_slim", "c_slim", "d_slim"), category.cards.map { it.skinId })
    }
}
