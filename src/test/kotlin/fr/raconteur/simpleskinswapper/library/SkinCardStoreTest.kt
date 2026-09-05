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
}
