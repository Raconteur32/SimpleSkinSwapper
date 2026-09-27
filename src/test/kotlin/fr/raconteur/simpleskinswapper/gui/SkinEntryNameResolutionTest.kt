package fr.raconteur.simpleskinswapper.gui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import javax.imageio.ImageIO

/** The display-name resolution chain: category name, then global name, then file base name. */
class SkinEntryNameResolutionTest {

    @TempDir
    lateinit var dir: Path

    /** 64x64 so detectSkinType's (50, 19) pixel read stays in bounds; opaque pixel there. */
    private fun entry(name: String, globalName: String?, categoryName: String?): SkinEntry {
        val image = BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB)
        image.setRGB(50, 19, 0xFF336699.toInt())
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "png", out)
        val file = dir.resolve(name).toFile()
        file.writeBytes(out.toByteArray())
        return SkinEntry(file).also {
            it.globalName = globalName
            it.categoryName = categoryName
        }
    }

    @Test
    fun `the category name wins over the global name`() {
        val e = entry("skin.png", "Global", "Category")
        assertEquals("Category", e.displayName)
    }

    @Test
    fun `the global name wins over the file base name`() {
        val e = entry("hash_name.png", "Global", null)
        assertEquals("Global", e.displayName)
    }

    @Test
    fun `with no names set the file base name applies`() {
        val e = entry("base_name.png", null, null)
        assertEquals("base_name", e.displayName)
        assertNull(e.globalName)
        assertNull(e.categoryName)
    }
}
