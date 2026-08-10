package fr.raconteur.simpleskinswapper.gui

import com.mojang.blaze3d.platform.NativeImage
import fr.raconteur.simpleskinswapper.SimpleSkinSwapper
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.function.Consumer
import javax.imageio.ImageIO

object SkinUtils {

    /**
     * Detect slim vs classic by checking pixel (50, 19) alpha.
     * If alpha == 0x00, the skin is slim (alex model).
     * Adapted from SkinSwapper (net.cobrasrock.skinswapper.gui.SkinEntry).
     */
    @JvmStatic
    fun detectSkinType(skinFile: File): SkinType {
        return try {
            val image = ImageIO.read(skinFile) ?: return SkinType.CLASSIC
            val pixel = image.getRGB(50, 19)
            val alpha = (pixel shr 24) and 0xFF
            if (alpha == 0x00) SkinType.SLIM else SkinType.CLASSIC
        } catch (e: IOException) {
            SimpleSkinSwapper.LOGGER.warn("Failed to detect skin type for {}: {}", skinFile.name, e.message)
            SkinType.CLASSIC
        }
    }

    /**
     * Load a skin file as a Minecraft GPU texture.
     * Handles 64x32 → 64x64 remapping if needed.
     * Returns the registered Identifier, or null on failure.
     */
    @JvmStatic
    fun loadSkinTexture(skinFile: File, textureId: String): Identifier? {
        try {
            Files.newInputStream(skinFile.toPath()).use { inputStream ->
                val raw = NativeImage.read(inputStream)
                var image = raw

                // Remap 64x32 skins to 64x64
                if (raw.height == 32) {
                    image = remapTexture(raw)
                    raw.close()
                }

                val id = Identifier.fromNamespaceAndPath(SimpleSkinSwapper.MOD_ID, textureId)
                val client = Minecraft.getInstance()
                val texture = DynamicTexture(
                    { "${SimpleSkinSwapper.MOD_ID}:$textureId" }, image
                )
                client.textureManager.register(id, texture)
                return id
            }
        } catch (e: IOException) {
            SimpleSkinSwapper.LOGGER.error("Failed to load skin texture from {}: {}", skinFile.name, e.message)
            return null
        }
    }

    /**
     * Load a skin file as a Minecraft GPU texture from a thread (schedules on render thread).
     */
    @JvmStatic
    fun loadSkinTextureAsync(skinFile: File, textureId: String, callback: Consumer<Identifier>) {
        val client = Minecraft.getInstance()
        client.execute {
            val id = loadSkinTexture(skinFile, textureId)
            if (id != null) callback.accept(id)
        }
    }

    /**
     * Remap a 64x32 legacy skin to 64x64 format.
     * Copies and mirrors limbs into the new bottom half.
     * Adapted from SkinSwapper (net.cobrasrock.skinswapper.gui.SkinUtils).
     */
    private fun remapTexture(src: NativeImage): NativeImage {
        val dst = NativeImage(NativeImage.Format.RGBA, 64, 64, false)

        // Copy top half (0-31) as-is
        for (x in 0..<64) {
            for (y in 0..<32) {
                dst.setPixel(x, y, src.getPixel(x, y))
            }
        }

        // Strip alpha from base layers
        stripAlpha(dst, 0, 0, 32, 16)

        // Mirror right leg → left leg (16,48)
        copyMirroredLimb(dst, 0, 16, 16, 48, 16, 16)
        // Mirror right arm → left arm (32,48)
        copyMirroredLimb(dst, 40, 16, 32, 48, 16, 16)

        return dst
    }

    private fun stripAlpha(img: NativeImage, x0: Int, y0: Int, w: Int, h: Int) {
        for (x in x0..<x0 + w) {
            for (y in y0..<y0 + h) {
                val color = img.getPixel(x, y)
                img.setPixel(x, y, color or 0xFF000000.toInt())
            }
        }
    }

    private fun copyMirroredLimb(img: NativeImage, srcX: Int, srcY: Int, dstX: Int, dstY: Int, w: Int, h: Int) {
        for (x in 0..<w) {
            for (y in 0..<h) {
                val color = img.getPixel(srcX + (w - 1 - x), srcY + y)
                img.setPixel(dstX + x, dstY + y, color)
            }
        }
    }
}
