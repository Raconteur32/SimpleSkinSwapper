package fr.raconteur.simpleskinswapper.library

import fr.raconteur.simpleskinswapper.data.SkinLibraryEnv
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

/** Hashing and collision-safe naming of textures, with a forced collision via a tiny hasher. */
class TextureNamerTest {

    @TempDir
    lateinit var dir: Path

    /** First digest bytes depend only on input[0] (bytes 0..3) then input[1] (byte 4):
     *  any two values sharing a first byte collide on the 8-hex prefix but differ on the 10-hex one. */
    private val colliding = Hasher { input ->
        byteArrayOf(input[0], 0, 0, 0, input.getOrElse(1) { 0 }, 0, 0, 0)
    }

    private fun namer() = TextureNamer(SkinLibraryEnv { dir }, colliding)

    private fun tinyPng(): ByteArray {
        val image = BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB)
        image.setRGB(1, 1, 0xFF336699.toInt())
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "png", out)
        return out.toByteArray()
    }

    @Test
    fun `a new texture takes the short hash name`() {
        val name = namer().uniqueFileNameFor(byteArrayOf(1, 2, 3))
        assertEquals("01000000.png", name)
    }

    @Test
    fun `identical values reuse the existing file`() {
        val png = tinyPng()
        Files.write(dir.resolve("deadbeef.png"), png)
        val value = TextureHashing.canonicalPixels(png)!!
        assertEquals("deadbeef.png", namer().existingFileNameFor(value))
    }

    @Test
    fun `a prefix collision lengthens the name`() {
        val png = tinyPng()
        Files.write(dir.resolve("01000000.png"), png)
        val name = namer().uniqueFileNameFor(byteArrayOf(1, 2, 3))
        assertEquals("0100000002.png", name)
        assertNotEquals("01000000.png", name)
    }

    @Test
    fun `canonical pixels ignore the png encoding`() {
        val source = BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB)
        source.setRGB(0, 0, 0xFF112233.toInt())
        source.setRGB(2, 3, 0x80445566.toInt())
        val outA = ByteArrayOutputStream()
        ImageIO.write(source, "png", outA)
        val reencoded = BufferedImage(4, 4, BufferedImage.TYPE_4BYTE_ABGR)
        reencoded.setRGB(0, 0, 0xFF112233.toInt())
        reencoded.setRGB(2, 3, 0x80445566.toInt())
        val outB = ByteArrayOutputStream()
        ImageIO.write(reencoded, "png", outB)
        assertNotEquals(outA.toByteArray(), outB.toByteArray())
        assertArrayEquals(TextureHashing.canonicalPixels(outA.toByteArray()), TextureHashing.canonicalPixels(outB.toByteArray()))
    }

    @Test
    fun `undecodable bytes have no canonical value`() {
        assertNull(TextureHashing.canonicalPixels("not a png".toByteArray()))
    }

    @Test
    fun `sha256 hex is full length`() {
        val hex = TextureHashing.toHex(TextureHashing.sha256.digest(byteArrayOf(1)))
        assertEquals(64, hex.length)
    }
}
