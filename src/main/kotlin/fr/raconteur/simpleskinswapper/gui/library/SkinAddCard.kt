package fr.raconteur.simpleskinswapper.gui.library

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

/**
 * Trailing pseudo-card at the end of every skin list: the idle card frame with a bare
 * "+" glyph, no preview and no name. Clicking it opens the add-skin overlay.
 */
class SkinAddCard(
    private val parent: SkinLibraryScreen,
    width: Int,
    height: Int
) : AbstractWidget(0, 0, width, height, Component.translatable("simpleskinswapper.screen.add.confirm")) {

    private val client: Minecraft = Minecraft.getInstance()

    // The grid viewport (same convention as SkinLibraryCard), updated by the parent.
    internal var clipLeft = Int.MIN_VALUE
    internal var clipTop = Int.MIN_VALUE
    internal var clipRight = Int.MAX_VALUE
    internal var clipBottom = Int.MAX_VALUE

    internal fun overridePosition(newX: Int, newY: Int) {
        setX(newX)
        setY(newY)
    }

    private fun isMouseOverCard(mouseX: Int, mouseY: Int): Boolean =
        mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height

    private fun isInsideViewport(mouseX: Int, mouseY: Int): Boolean =
        mouseX >= clipLeft && mouseX < clipRight && mouseY >= clipTop && mouseY < clipBottom

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val mx = event.x().toInt()
        val my = event.y().toInt()
        if (isMouseOverCard(mx, my) && isInsideViewport(mx, my)) {
            parent.openAddPanel()
            return true
        }
        return false
    }

    //? if >=26.1 {
    override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
    //?} else {
    /*override fun renderWidget(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
    *///?}
        val onScreen = y + height > clipTop && y < clipBottom && x + width > clipLeft && x < clipRight
        if (!onScreen) return
        if (clipRight <= clipLeft || clipBottom <= clipTop) return
        val hovered = isMouseOverCard(mouseX, mouseY) && isInsideViewport(mouseX, mouseY)

        graphics.enableScissor(clipLeft, clipTop, clipRight, clipBottom)
        // Same frame as idle cards (highlight on hover like a selected tab); a bare light
        // "+" on the dark background, no preview and no name.
        SkinLibraryScreen.drawCardFrame(graphics, x, y, width, height, hovered)
        graphics.centeredText(
            client.font, Component.literal("+"),
            x + width / 2, y + height / 2 - client.font.lineHeight / 2, 0xFFE0E0E0.toInt()
        )
        graphics.disableScissor()
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) = defaultButtonNarrationText(output)
}
