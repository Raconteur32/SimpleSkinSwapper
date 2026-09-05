package fr.raconteur.simpleskinswapper.library

import fr.raconteur.simpleskinswapper.data.SkinLibraryEnv
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

/** Legacy libraries migrate once: registry, hash textures, preserved originals, remapped cards. */
class LibraryMigratorTest {

    @TempDir
    lateinit var dir: Path

    @Serializable
    private data class LegacyCategoryDto(
        val name: String? = null,
        val color: String? = null,
        val maxWheels: Int? = null,
        val skins: List<String>? = null,
    )

    @Serializable
    private data class LegacyCategoriesDto(val categories: List<LegacyCategoryDto>? = null)

    private fun writeJson(name: String, content: String) {
        Files.write(dir.resolve(name), content.toByteArray())
    }

    private fun legacyCategoriesJson() {
        writeJson(
            "categories.json",
            """{"categories":[{"name":"PvP","color":"#99834D","maxWheels":1,"skins":["Steve.png"]}]}"""
        )
    }

    private fun writePng(name: String, color: Int): ByteArray {
        val image = BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB)
        image.setRGB(1, 1, color)
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "png", out)
        Files.write(dir.resolve(name), out.toByteArray())
        return out.toByteArray()
    }

    private fun migrator() = LibraryMigrator(
        SkinLibraryEnv { dir },
        SkinRegistry(SkinLibraryEnv { dir }),
        SkinCardStore(SkinLibraryEnv { dir }),
        TextureHashing.sha256,
        colorToDye = { if (it == "#99834D") "red" else "white" },
    )

    @Test
    fun `legacy library migrates and preserves originals`() {
        val steve = writePng("Steve.png", 0xFF112233.toInt())
        writePng("Alex.png", 0xFF445566.toInt())
        writeJson("names.json", """{"Steve":"My Hero"}""")
        writeJson("types.json", """{"Alex.png":"slim"}""")
        legacyCategoriesJson()

        assertTrue(migrator().isNeeded())
        assertEquals(2, migrator().migrate())

        val registry = SkinRegistry(SkinLibraryEnv { dir })
        assertEquals(2, registry.all().size)
        val steveSkin = registry.all().first { it.name == "My Hero" }
        val alexSkin = registry.all().first { it.model == SkinRecord.MODEL_SLIM }
        assertEquals(SkinRecord.MODEL_CLASSIC, steveSkin.model)
        assertEquals("Alex", alexSkin.name)

        // originals preserved untouched, root holds only hash-named textures now
        val userFiles = dir.resolve("User Files")
        assertTrue(Files.exists(userFiles.resolve("Steve.png")))
        assertTrue(Files.exists(userFiles.resolve("Alex.png")))
        assertEquals(steve.toList(), Files.readAllBytes(userFiles.resolve("Steve.png")).toList())
        assertEquals(2, (dir.toFile().listFiles { _, n -> n.endsWith(".png") } ?: emptyArray()).size)
        // The root files ARE the hash names the registry points at (the regression this guards).
        assertTrue(steveSkin.file.matches(Regex("[0-9a-f]{8}\\.png")), steveSkin.file)
        assertTrue(Files.exists(dir.resolve(steveSkin.file)))
        assertTrue(alexSkin.file.matches(Regex("[0-9a-f]{8}\\.png")), alexSkin.file)
        assertTrue(Files.exists(dir.resolve(alexSkin.file)))

        // categories remapped to card references
        val cards = SkinCardStore(SkinLibraryEnv { dir })
        val pvp = cards.all().single()
        assertEquals("PvP", pvp.name)
        assertEquals(1, pvp.maxWheels)
        // The legacy hex resolved to a dye NAME through the injected resolver.
        assertEquals("red", pvp.dye)
        assertEquals(listOf(steveSkin.id), pvp.cards.map { it.skinId })

        // a second run is a no-op
        assertFalse(migrator().isNeeded())
        assertEquals(0, migrator().migrate())
    }

    @Test
    fun `duplicate contents merge into one skin without losing originals`() {
        writePng("One.png", 0xFF112233.toInt())
        writePng("Two.png", 0xFF112233.toInt())
        // Two legacy files map onto the same (merged) skin: two mappings, one skin.
        assertEquals(2, migrator().migrate())
        assertEquals(1, SkinRegistry(SkinLibraryEnv { dir }).all().size)
        assertEquals(1, (dir.toFile().listFiles { _, n -> n.endsWith(".png") } ?: emptyArray()).size)
        val userFiles = dir.resolve("User Files")
        assertTrue(Files.exists(userFiles.resolve("One.png")))
        assertTrue(Files.exists(userFiles.resolve("Two.png")))
    }

    @Test
    fun `an undecodable file is preserved but creates no skin`() {
        Files.write(dir.resolve("junk.png"), "not a png".toByteArray())
        assertEquals(0, migrator().migrate())
        assertTrue(SkinRegistry(SkinLibraryEnv { dir }).all().isEmpty())
        assertTrue(Files.exists(dir.resolve("User Files").resolve("junk.png")))
        assertTrue((dir.toFile().listFiles { _, n -> n.endsWith(".png") } ?: emptyArray()).isEmpty())
    }

    @Test
    fun `a re-migration repairs file names and keeps new-format category cards`() {
        writePng("Steve.png", 0xFF112233.toInt())
        legacyCategoriesJson()
        migrator().migrate()
        val registry = SkinRegistry(SkinLibraryEnv { dir })
        val id = registry.all().single().id

        // Simulate the legacy-name bug: the root file carries its old name again and the
        // registry marker is gone, while categories.json is already registry-format.
        val badFile = dir.resolve(registry.all().single().file)
        Files.move(badFile, dir.resolve("Steve.png"))
        Files.delete(dir.resolve("skins.json"))
        Files.deleteIfExists(dir.resolve("User Files").resolve("Steve.png"))

        assertEquals(1, migrator().migrate())
        val repaired = SkinRegistry(SkinLibraryEnv { dir }).all().single()
        assertEquals(id, repaired.id)
        assertTrue(repaired.file.matches(Regex("[0-9a-f]{8}\\.png")), repaired.file)
        assertTrue(Files.exists(dir.resolve(repaired.file)))
        val cards = SkinCardStore(SkinLibraryEnv { dir }).all().single().cards
        assertEquals(listOf(id), cards.map { it.skinId })
    }

    @Test
    fun `a fresh install does not migrate`() {
        assertFalse(migrator().isNeeded())
        assertEquals(0, migrator().migrate())
    }

    @Test
    fun `an already migrated library is skipped`() {
        writePng("Steve.png", 0xFF112233.toInt())
        migrator().migrate()
        // Simulate any later load: skins.json exists now.
        assertNotNull(SkinRegistry(SkinLibraryEnv { dir }))
        assertFalse(migrator().isNeeded())
        assertEquals(1, (dir.toFile().listFiles { _, n -> n.endsWith(".png") } ?: emptyArray()).size)
    }
}
