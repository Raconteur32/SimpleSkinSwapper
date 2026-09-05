package fr.raconteur.simpleskinswapper.gui.library

import fr.raconteur.simpleskinswapper.gui.EdgeSafeButtonWidget
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.client.renderer.RenderPipelines

/**
 * The shared destructive-action confirmation popup: dimmed backdrop, centered box,
 * optional title, word-wrapped message lines and one to three buttons (the last one
 * is always cancel — ESC and backdrop clicks resolve to it). Hosted by
 * [SkinLibraryScreen], which draws it above every panel and routes it the clicks.
 * Drawing lives here so the later re-texture happens in one place for every
 * confirmation (skin deletes and category delete alike).
 */
internal class ConfirmPopup(
    private val screen: SkinLibraryScreen,
    private val title: Component?,
    messageLines: List<Component>,
    buttons: List<PopupButton>,
) {
    // The stonecutter text-drawing rewrite for old versions matches the literal
    // `client.font` receiver argument, so this class keeps a Minecraft client handle.
    private val client = net.minecraft.client.Minecraft.getInstance()
    /** One popup action; the cancel button is appended automatically. */
    class PopupButton(val label: Component, val onClick: () -> Unit)

    /** Resolves the popup (removes it from the screen) without running any action. */
    fun cancel() {
        screen.closeConfirmPopup()
    }

    private val allButtons: List<PopupButton> = buttons + PopupButton(CommonComponents.GUI_CANCEL) { cancel() }

    private val buttonWidgets: List<EdgeSafeButtonWidget> = allButtons.map { button ->
        EdgeSafeButtonWidget(0, 0, BUTTON_W, BUTTON_H, button.label) {
            screen.closeConfirmPopup()
            button.onClick()
        }
    }

    // Word-wrapped at construction: Component.string + font.width work on every target
    // version, and the box width is fixed, so the lines never change afterwards.
    private val lines: List<String> = messageLines.flatMap { wrap(it.string) }

    private fun wrap(text: String): List<String> {
        val wrapped = ArrayList<String>()
        var currentLine = ""
        for (word in text.split(" ")) {
            val candidate = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (client.font.width(candidate) > BOX_W - PAD * 2 && currentLine.isNotEmpty()) {
                wrapped.add(currentLine)
                currentLine = word
            } else {
                currentLine = candidate
            }
        }
        if (currentLine.isNotEmpty()) wrapped.add(currentLine)
        return wrapped
    }

    var open = false
        private set

    fun show() {
        open = true
    }

    /** Box height follows the content: title + wrapped lines + button row. */
    private fun boxHeight(): Int {
        val lineHeight = client.font.lineHeight
        var h = PAD * 2 + lines.size * lineHeight
        if (title != null) h += lineHeight + TITLE_GAP
        h += BUTTON_H + BUTTON_GAP
        return h
    }

    /** ESC closes the popup (resolved as cancel); true when it was open. */
    fun onEsc(): Boolean {
        if (!open) return false
        cancel()
        return true
    }

    /** While open the popup swallows every click: buttons run, anything else cancels. */
    fun handleClick(click: MouseButtonEvent, doubled: Boolean): Boolean {
        if (!open) return false
        for (widget in buttonWidgets) {
            if (widget.mouseClicked(click, doubled)) return true
        }
        cancel()
        return true
    }

    fun draw(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        val w = screen.width
        val h = screen.height
        graphics.fill(0, 0, w, h, BACKDROP)
        val boxH = boxHeight()
        val boxW = BOX_W
        val bx = w / 2 - boxW / 2
        val by = h / 2 - boxH / 2
        graphics.fill(bx, by, bx + boxW, by + boxH, BOX_BG)
        graphics.fill(bx, by, bx + boxW, by + 1, BOX_EDGE)

        val lineHeight = client.font.lineHeight
        var ly = by + PAD
        if (title != null) {
            // Argument must start with `Component`: the stonecutter rewrite keys on it.
            graphics.text(client.font, Component.nullToEmpty(title.string), bx + PAD, ly, 0xFFFFFFFF.toInt())
            ly += lineHeight + TITLE_GAP
        }
        for (lineText in lines) {
            graphics.text(client.font, Component.nullToEmpty(lineText), bx + PAD, ly, 0xFFFFFFFF.toInt())
            ly += lineHeight
        }

        // Button row: evenly spread across the box width, cancel last (rightmost).
        val buttonY = by + boxH - BUTTON_H - PAD
        val count = buttonWidgets.size
        val gap = BUTTON_GAP
        val totalW = count * BUTTON_W + (count - 1) * gap
        var btnX = bx + (boxW - totalW) / 2
        for (widget in buttonWidgets) {
            widget.setX(btnX)
            widget.setY(buttonY)
            //? if >=26.1 {
            widget.extractRenderState(graphics, mouseX, mouseY, delta)
            //?} else {
            /*widget.render(graphics, mouseX, mouseY, delta)
            *///?}
            btnX += BUTTON_W + gap
        }
    }

    companion object {
        private val BACKDROP = 0x88000000.toInt()
        private val BOX_BG = 0xFF1A2535.toInt()
        private val BOX_EDGE = 0xFFFFFFFF.toInt()
        private const val BOX_W = 280
        private const val PAD = 8
        private const val TITLE_GAP = 4
        private const val BUTTON_H = 20
        private const val BUTTON_W = 88
        private const val BUTTON_GAP = 6
    }
}
