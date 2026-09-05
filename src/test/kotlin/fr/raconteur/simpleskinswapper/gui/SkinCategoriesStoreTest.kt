package fr.raconteur.simpleskinswapper.gui.library

import fr.raconteur.simpleskinswapper.data.SkinLibraryEnv
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/** Boots the store seam against a temp folder — the library core runs without the game. */
class SkinCategoriesStoreTest {

    @TempDir
    lateinit var dir: Path

    private fun newStore() = SkinCategoriesStore(SkinLibraryEnv { dir })

    @Test
    fun `added category persists across instances`() {
        newStore().addCategory("PvP", "#F9FFFE")
        val reloaded = newStore()
        assertEquals(listOf("PvP"), reloaded.all().map { it.name })
        assertEquals("#F9FFFE", reloaded.all().single().colorHex)
    }

    @Test
    fun `missing file yields an empty library`() {
        assertEquals(0, newStore().all().size)
    }
}
