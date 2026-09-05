package fr.raconteur.simpleskinswapper.gui.library

import fr.raconteur.simpleskinswapper.gui.EdgeSafeButtonWidget
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.client.renderer.RenderPipelines

/**
 * The per-card context menu (kebab control / right-click): Modifier opens the detail
 * overlay, Supprimer opens the shared delete confirmation. Anchored to the card's
 * bottom-right, flipping above the card when there is no room below. At most one menu
 * is open at a time (the screen holds a single instance); any click outside it closes
 * it and flows through.
 */
internal class CardMenu(
    private val screen: SkinLibraryScreen,
    private val card: SkinLibraryCard,
) {
    private val modifyButton: EdgeSafeButtonWidget = EdgeSafeButtonWidget(
        0, 0, MENU_W, BUTTON_H,
        Component.translatable("simpleskinswapper.screen.card.menu_modify")
    ) {
        screen.closeCardMenu()
        screen.openDetail(card)
    }

    private val deleteButton: EdgeSafeButtonWidget = EdgeSafeButtonWidget(
        0, 0, MENU_W, BUTTON_H,
        Component.translatable("simpleskinswapper.screen.card.menu_delete")
    ) {
        val entry = card.entry
        screen.closeCardMenu()
        screen.openDeletePopup(entry, panel = null)
    }

    private val buttons = listOf(modifyButton, deleteButton)

    /** True when the click landed on the menu (and was handled); false = outside. */
    fun handleClick(click: MouseButtonEvent, doubled: Boolean): Boolean {
        for (widget in buttons) {
            if (widget.mouseClicked(click, doubled)) return true
        }
        return false
    }

    fun draw(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        val boxH = PAD * 2 + buttons.size * BUTTON_H + (buttons.size - 1) * BUTTON_GAP
        val bx = (card.x + card.width - MENU_W).coerceIn(0, screen.width - MENU_W)
        val by = card.y + card.height + 2
        val flipped = by + boxH > screen.height
        val byFinal = if (flipped) card.y - boxH - 2 else by

        graphics.fill(bx, byFinal, bx + MENU_W, byFinal + boxH, BOX_BG)
        graphics.fill(bx, byFinal, bx + MENU_W, byFinal + 1, BOX_EDGE)

        var widgetY = byFinal + PAD
        for (widget in buttons) {
            widget.setX(bx + PAD)
            widget.setY(widgetY)
            //? if >=26.1 {
            widget.extractRenderState(graphics, mouseX, mouseY, delta)
            //?} else {
            /*widget.render(graphics, mouseX, mouseY, delta)
            *///?}
            widgetY += BUTTON_H + BUTTON_GAP
        }
    }

    companion object {
        private val BOX_BG = 0xFF1A2535.toInt()
        private val BOX_EDGE = 0xFFFFFFFF.toInt()
        private const val MENU_W = 100
        private const val BUTTON_H = 20
        private const val BUTTON_GAP = 4
        private const val PAD = 4
    }
}
