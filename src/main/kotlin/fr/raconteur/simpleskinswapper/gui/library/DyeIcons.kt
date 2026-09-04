package fr.raconteur.simpleskinswapper.gui.library

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier

//? if >=26.1 {
import net.minecraft.client.resources.model.sprite.SpriteId
import net.minecraft.client.renderer.texture.TextureAtlas
//?} else {
/*import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.client.resources.model.Material
*///?}

/**
 * Blits vanilla dye item textures straight from the atlas — dye PNGs are flat
 * pre-colored sprites, so no item-model rendering is involved. The atlas hosting
 * item sprites diverges on 26.x (split items atlas, see design D2).
 */
internal object DyeIcons {

    fun spriteId(dyeName: String): Identifier =
        Identifier.fromNamespaceAndPath("minecraft", "item/${dyeName}_dye")

    fun draw(graphics: GuiGraphicsExtractor, dyeName: String, x: Int, y: Int, size: Int) {
        val id = spriteId(dyeName)
        //? if >=26.1 {
        val sprite = graphics.getSprite(SpriteId(TextureAtlas.LOCATION_ITEMS, id))
        //?} else {
        /*val sprite = graphics.getSprite(Material(TextureAtlas.LOCATION_BLOCKS, id))
        *///?}
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, size, size)
    }
}
