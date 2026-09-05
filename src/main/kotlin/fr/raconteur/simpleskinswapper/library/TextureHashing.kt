package fr.raconteur.simpleskinswapper.library

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.util.Locale
import javax.imageio.ImageIO

/** Digest abstraction so tests can inject a tiny deterministic hasher and force
 *  collisions; production uses SHA-256 (see [TextureHashing]). */
fun interface Hasher {
    fun digest(input: ByteArray): ByteArray
}

/** Canonical texture values and their hex rendering.
 *  A texture's identity is the hash of its DECODED pixels — never of the PNG bytes —
 *  so resaves, metadata churn and re-encodings of the same skin stay one texture. */
object TextureHashing {

    /** Shortest file-name prefix (hex chars) used for hash-named textures. */
    const val MIN_PREFIX = 8

    /** Production hasher: SHA-256 over the canonical pixel bytes. */
    @JvmStatic
    val sha256: Hasher = Hasher { input ->
        java.security.MessageDigest.getInstance("SHA-256").digest(input)
    }

    /** Decodes a PNG and returns its canonical value: every pixel's ARGB as 4 big-endian
     *  bytes, row-major. Encoding, palette and metadata are intentionally ignored.
     *  Returns null when the bytes are not a decodable image. */
    @JvmStatic
    fun canonicalPixels(png: ByteArray): ByteArray? {
        val image: BufferedImage = try {
            ImageIO.read(ByteArrayInputStream(png))
        } catch (_: Exception) {
            null
        } ?: return null
        val argb = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
        val out = ByteArray(argb.size * 4)
        for (i in argb.indices) {
            val p = argb[i]
            out[i * 4] = (p ushr 24).toByte()
            out[i * 4 + 1] = (p ushr 16 and 0xFF).toByte()
            out[i * 4 + 2] = (p ushr 8 and 0xFF).toByte()
            out[i * 4 + 3] = (p and 0xFF).toByte()
        }
        return out
    }

    /** Lowercase hex of a digest. */
    @JvmStatic
    fun toHex(bytes: ByteArray): String =
        bytes.joinToString(separator = "") { String.format(Locale.ROOT, "%02x", it) }
}
