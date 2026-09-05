package fr.raconteur.simpleskinswapper.library

import fr.raconteur.simpleskinswapper.data.SkinLibraryEnv
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

/** Texture lifecycle: no orphan files, deletion on last reference, external-deletion pruning. */
class TextureLifecycleTest {

    @TempDir
    lateinit var dir: Path

    private fun pngFiles(): List<String> =
        (dir.toFile().listFiles { _, n -> n.endsWith(".png") } ?: emptyArray()).map { it.name }

    private fun lifecycle(): TextureLifecycle {
        val env = SkinLibraryEnv { dir }
        return TextureLifecycle(env, SkinRegistry(env), TextureNamer(env, TextureHashing.sha256))
    }

    private fun tinyPng(color: Int): ByteArray {
        val image = BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB)
        image.setRGB(1, 1, color)
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "png", out)
        return out.toByteArray()
    }

    @Test
    fun `creating a skin writes its texture file`() {
        val record = lifecycle().createSkin(tinyPng(0xFF112233.toInt()), SkinRecord.MODEL_SLIM, "Steve")
        assertNotNull(record)
        assertTrue(record!!.file.endsWith(".png"))
        assertTrue(pngFiles().contains(record.file))
        assertNotNull(lifecycle().let { SkinRegistry(SkinLibraryEnv { dir }).findById(record.id) })
    }

    @Test
    fun `the same texture serves both models from one file`() {
        val png = tinyPng(0xFF112233.toInt())
        val slim = lifecycle().createSkin(png, SkinRecord.MODEL_SLIM, "Slim")
        val classic = lifecycle().createSkin(png, SkinRecord.MODEL_CLASSIC, "Wide")
        assertNotNull(slim)
        assertNotNull(classic)
        assertEquals(1, pngFiles().size)
        assertEquals(slim!!.file, classic!!.file)
        assertEquals(slim.textureHash, classic.textureHash)
    }

    @Test
    fun `a refused duplicate writes nothing`() {
        val png = tinyPng(0xFF112233.toInt())
        assertNotNull(lifecycle().createSkin(png, SkinRecord.MODEL_SLIM, "First"))
        assertNull(lifecycle().createSkin(png, SkinRecord.MODEL_SLIM, "Second"))
        assertEquals(1, pngFiles().size)
    }

    @Test
    fun `undecodable bytes create neither skin nor file`() {
        assertNull(lifecycle().createSkin("junk".toByteArray(), SkinRecord.MODEL_SLIM, "Steve"))
        assertTrue(pngFiles().isEmpty())
        assertTrue(SkinRegistry(SkinLibraryEnv { dir }).all().isEmpty())
    }

    @Test
    fun `removing the last skin deletes the texture file`() {
        val lifecycle = lifecycle()
        val record = lifecycle.createSkin(tinyPng(0xFF112233.toInt()), SkinRecord.MODEL_SLIM, "Steve")!!
        assertTrue(lifecycle.removeSkin(record.id))
        assertFalse(pngFiles().contains(record.file))
        assertTrue(pngFiles().isEmpty())
    }

    @Test
    fun `a shared texture survives until its last skin is gone`() {
        val lifecycle = lifecycle()
        val png = tinyPng(0xFF112233.toInt())
        val slim = lifecycle.createSkin(png, SkinRecord.MODEL_SLIM, "Slim")!!
        val classic = lifecycle.createSkin(png, SkinRecord.MODEL_CLASSIC, "Wide")!!
        lifecycle.removeSkin(slim.id)
        assertTrue(pngFiles().contains(classic.file))
        lifecycle.removeSkin(classic.id)
        assertTrue(pngFiles().isEmpty())
    }

    @Test
    fun `an external texture deletion prunes its skins`() {
        val lifecycle = lifecycle()
        val record = lifecycle.createSkin(tinyPng(0xFF112233.toInt()), SkinRecord.MODEL_SLIM, "Steve")!!
        Files.delete(dir.resolve(record.file))
        assertEquals(listOf(record.id), lifecycle.pruneMissingTextures())
        assertTrue(SkinRegistry(SkinLibraryEnv { dir }).all().isEmpty())
    }
}
