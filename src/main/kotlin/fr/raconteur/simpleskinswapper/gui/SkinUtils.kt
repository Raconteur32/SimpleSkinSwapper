package fr.raconteur.simpleskinswapper.gui

import fr.raconteur.simpleskinswapper.SimpleSkinSwapper
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO
import fr.raconteur.simpleskinswapper.SkinType

object SkinUtils {

    /** True when ([mx], [my]) lies inside the rectangle ([x], [y], [w], [h]) (right/bottom exclusive). */
    @JvmStatic
    fun inRect(mx: Int, my: Int, x: Int, y: Int, w: Int, h: Int): Boolean {
        val inX = mx >= x && mx < x + w
        val inY = my >= y && my < y + h
        return inX && inY
    }

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

}
