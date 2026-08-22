package fr.raconteur.simpleskinswapper.gui

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth

/**
 * A vanilla [Button] whose label renders through the clamped ScissorStack instead of
 * vanilla's ActiveTextCollector.acceptScrolling, which attaches its scissor unclamped to the
 * text render state. Since MC 26.2 (RenderPass scissor validation, introduced with the Vulkan
 * backend work), that unclamped scissor crashes the frame as soon as the button straddles a
 * screen edge or leaves it entirely. Sprite, hover, focus, tooltip and click behavior are
 * inherited unchanged — only the label rendering path differs. Use this for any button that
 * may be positioned (partially) off-screen, e.g. inside a scrolling carousel card.
 */
class EdgeSafeButtonWidget(
    x: Int, y: Int, width: Int, height: Int,
    message: Component, onPress: Button.OnPress
) : Button(x, y, width, height, message, onPress, Button.DEFAULT_NARRATION) {

    override fun extractContents(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        extractDefaultSprite(graphics)
        drawLabel(graphics)
    }

    private fun drawLabel(g: GuiGraphicsExtractor) {
        val font = net.minecraft.client.Minecraft.getInstance().font
        val color = if (this.active) 0xFFFFFFFF.toInt() else 0xFFA0A0A0.toInt()
        val textWidth = font.width(message)
        val textY = y + (height - 8) / 2

        // enableScissor goes through the ScissorStack, which clamps the rect to the window:
        // safe at any position, unlike the unclamped scrolling-label scissor.
        g.enableScissor(x, y, x + width, y + height)
        if (textWidth <= width - 2) {
            drawText(g, font, x + (width - textWidth) / 2, textY, color)
        } else {
            // Vanilla marquee: ping-pong the overflowing label inside the clip.
            val overflow = textWidth - (width - 2)
            val period = Math.max(overflow * 0.5, 3.0)
            val phase = Math.cos(2.0 * Math.PI * (System.currentTimeMillis() / 1000.0) / period)
            val t = Mth.sin((Math.PI / 2) * phase) / 2.0F + 0.5F
            drawText(g, font, x + 1 - (t * overflow).toInt(), textY, color)
        }
        g.disableScissor()
    }

    //? if >=26.1 {
    private fun drawText(g: net.minecraft.client.gui.GuiGraphicsExtractor, font: net.minecraft.client.gui.Font, tx: Int, ty: Int, color: Int) {
        g.text(font, message, tx, ty, color)
    }
    //?} else {
    /*private fun drawText(g: net.minecraft.client.gui.GuiGraphics, font: net.minecraft.client.gui.Font, tx: Int, ty: Int, color: Int) {
        g.drawString(font, message, tx, ty, color)
    }
    *///?}
}
