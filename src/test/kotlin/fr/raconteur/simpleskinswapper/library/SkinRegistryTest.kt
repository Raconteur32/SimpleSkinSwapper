package fr.raconteur.simpleskinswapper.library

import fr.raconteur.simpleskinswapper.data.SkinLibraryEnv
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/** The registry holds unique (texture, model) skins and persists them across instances. */
class SkinRegistryTest {

    @TempDir
    lateinit var dir: Path

    private fun registry() = SkinRegistry(SkinLibraryEnv { dir })

    @Test
    fun `created skins persist across instances`() {
        val record = registry().create("abc123", SkinRecord.MODEL_SLIM, "Steve")
        assertNotNull(record)
        val reloaded = registry()
        assertEquals(listOf("abc123_slim"), reloaded.all().map { it.id })
        assertEquals("Steve", reloaded.findById("abc123_slim")?.name)
    }

    @Test
    fun `a duplicate texture-model pair is refused`() {
        val first = registry().create("abc123", SkinRecord.MODEL_SLIM, "Steve")
        val second = registry().create("abc123", SkinRecord.MODEL_SLIM, "Other")
        assertNotNull(first)
        assertNull(second)
        assertEquals(1, registry().all().size)
    }

    @Test
    fun `the same texture may serve both models`() {
        registry().create("abc123", SkinRecord.MODEL_SLIM, "Slim Steve")
        registry().create("abc123", SkinRecord.MODEL_CLASSIC, "Wide Steve")
        assertEquals(2, registry().all().size)
    }

    @Test
    fun `removal persists`() {
        registry().create("abc123", SkinRecord.MODEL_SLIM, "Steve")
        registry().remove("abc123_slim")
        assertNull(registry().findById("abc123_slim"))
        assertEquals(0, registry().all().size)
    }

    @Test
    fun `rename persists`() {
        registry().create("abc123", SkinRecord.MODEL_SLIM, "Steve")
        registry().rename("abc123_slim", "Hero")
        assertEquals("Hero", registry().findById("abc123_slim")?.name)
    }
}
