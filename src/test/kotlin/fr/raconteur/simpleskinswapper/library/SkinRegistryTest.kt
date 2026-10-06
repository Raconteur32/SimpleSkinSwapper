package fr.raconteur.simpleskinswapper.library

import fr.raconteur.simpleskinswapper.data.SkinLibraryEnv
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/** The registry holds unique (texture, model) skins and persists them across instances. */
class SkinRegistryTest {

    @TempDir
    lateinit var dir: Path

    private fun registry() = SkinRegistry(SkinLibraryEnv { dir })

    /** Four skins a b c d in registry order, ids "a_slim"…"d_slim". */
    private fun fourSkins(): SkinRegistry = registry().also { r ->
        r.create("a", SkinRecord.MODEL_SLIM, "A", "a.png")
        r.create("b", SkinRecord.MODEL_SLIM, "B", "b.png")
        r.create("c", SkinRecord.MODEL_SLIM, "C", "c.png")
        r.create("d", SkinRecord.MODEL_SLIM, "D", "d.png")
    }

    @Test
    fun `created skins persist across instances`() {
        val record = registry().create("abc123", SkinRecord.MODEL_SLIM, "Steve", "abc123ab.png")
        assertNotNull(record)
        val reloaded = registry()
        assertEquals(listOf("abc123_slim"), reloaded.all().map { it.id })
        assertEquals("Steve", reloaded.findById("abc123_slim")?.name)
    }

    @Test
    fun `a duplicate texture-model pair is refused`() {
        val first = registry().create("abc123", SkinRecord.MODEL_SLIM, "Steve", "abc123ab.png")
        val second = registry().create("abc123", SkinRecord.MODEL_SLIM, "Other", "abc123ab.png")
        assertNotNull(first)
        assertNull(second)
        assertEquals(1, registry().all().size)
    }

    @Test
    fun `the same texture may serve both models`() {
        registry().create("abc123", SkinRecord.MODEL_SLIM, "Slim Steve", "abc123ab.png")
        registry().create("abc123", SkinRecord.MODEL_CLASSIC, "Wide Steve", "abc123ab.png")
        assertEquals(2, registry().all().size)
    }

    @Test
    fun `removal persists`() {
        registry().create("abc123", SkinRecord.MODEL_SLIM, "Steve", "abc123ab.png")
        registry().remove("abc123_slim")
        assertNull(registry().findById("abc123_slim"))
        assertEquals(0, registry().all().size)
    }

    @Test
    fun `rename persists`() {
        registry().create("abc123", SkinRecord.MODEL_SLIM, "Steve", "abc123ab.png")
        registry().rename("abc123_slim", "Hero")
        assertEquals("Hero", registry().findById("abc123_slim")?.name)
    }

    @Test
    fun `move before a pivot skips over the skins in between`() {
        val r = fourSkins()
        // Displayed subset [a c] dragged c onto a's displayed slot: relative move, not index 0.
        assertTrue(r.moveBefore("c_slim", "a_slim"))
        assertEquals(listOf("c_slim", "a_slim", "b_slim", "d_slim"), r.all().map { it.id })
    }

    @Test
    fun `move before the next skin changes nothing observable`() {
        val r = fourSkins()
        assertTrue(r.moveBefore("a_slim", "b_slim"))
        assertEquals(listOf("a_slim", "b_slim", "c_slim", "d_slim"), r.all().map { it.id })
    }

    @Test
    fun `move after a pivot lands right behind it`() {
        val r = fourSkins()
        // Drop past the last displayed card [a c]: c lands right after a, followers stay behind.
        assertTrue(r.moveAfter("a_slim", "c_slim"))
        assertEquals(listOf("b_slim", "c_slim", "a_slim", "d_slim"), r.all().map { it.id })
    }

    @Test
    fun `moves with a missing pivot or skin are no-ops`() {
        val r = fourSkins()
        assertFalse(r.moveBefore("a_slim", "ghost_slim"))
        assertFalse(r.moveBefore("ghost_slim", "a_slim"))
        assertFalse(r.moveAfter("a_slim", "a_slim"))
        assertEquals(listOf("a_slim", "b_slim", "c_slim", "d_slim"), r.all().map { it.id })
    }

    @Test
    fun `a relative move persists across instances`() {
        val r = fourSkins()
        assertTrue(r.moveBefore("c_slim", "a_slim"))
        assertEquals(listOf("c_slim", "a_slim", "b_slim", "d_slim"), registry().all().map { it.id })
    }
}
