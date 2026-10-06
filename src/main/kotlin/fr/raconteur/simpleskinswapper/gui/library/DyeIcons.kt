package fr.raconteur.simpleskinswapper.gui.library

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier

/**
 * Blits the mod-owned dye swatch sprites straight from the GUI atlas — flat
 * pre-colored icons shipped under the mod's namespace, so resource packs can
 * retheme them without the items-atlas coupling or any per-version sprite
 * wrapper type.
 */
internal object DyeIcons {

    fun spriteId(dyeName: String): Identifier =
        Identifier.fromNamespaceAndPath("simpleskinswapper", "dye/$dyeName")

    fun draw(graphics: GuiGraphicsExtractor, dyeName: String, x: Int, y: Int, size: Int) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, spriteId(dyeName), x, y, size, size)
    }
}
