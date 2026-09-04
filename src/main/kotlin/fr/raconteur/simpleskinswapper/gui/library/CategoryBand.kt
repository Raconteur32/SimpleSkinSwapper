package fr.raconteur.simpleskinswapper.gui.library

import fr.raconteur.simpleskinswapper.gui.EdgeSafeButtonWidget
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.client.renderer.RenderPipelines

/**
 * The category config band inside the grid page: collapsed bar (name · count · wheels),
 * expanded row with the color swatch grid, rename field, wheel stepper and delete button —
 * plus the delete-confirmation modal it opens. Owns its widgets; the screen registers them.
 */
internal class CategoryBand(private val screen: SkinLibraryScreen) {

    // The stonecutter text-drawing rewrite for old versions matches the literal
    // `client.font` receiver argument, so this class keeps a Minecraft client handle.
    private val client = net.minecraft.client.Minecraft.getInstance()

    val nameField: EditBox = EditBox(
        screen.font, 0, 0, BAND_NAME_WIDTH, BAND_FIELD_HEIGHT,
        Component.translatable("simpleskinswapper.screen.library.category_name")
    ).also {
        it.setMaxLength(32)
        it.setResponder { text -> onCategoryRenamed(text) }
    }

    val wheelsMinus: EdgeSafeButtonWidget = EdgeSafeButtonWidget(0, 0, 20, BAND_FIELD_HEIGHT, Component.literal("-")) {
        screen.selectedCategory?.let { it.maxWheels = (it.maxWheels - 1).coerceAtLeast(0); SkinCategoriesStore.save() }
    }

    val wheelsPlus: EdgeSafeButtonWidget = EdgeSafeButtonWidget(0, 0, 20, BAND_FIELD_HEIGHT, Component.literal("+")) {
        screen.selectedCategory?.let { it.maxWheels = (it.maxWheels + 1).coerceAtLeast(0); SkinCategoriesStore.save() }
    }

    val deleteButton: EdgeSafeButtonWidget = EdgeSafeButtonWidget(0, 0, 20, BAND_FIELD_HEIGHT, Component.literal("✕")) {
        confirmingDelete = true
    }

    val confirmOverlayButton: EdgeSafeButtonWidget = EdgeSafeButtonWidget(0, 0, 100, 20, Component.translatable(
        "simpleskinswapper.screen.library.delete_category_confirm")) { screen.confirmCategoryDelete() }
    val cancelOverlayButton: EdgeSafeButtonWidget = EdgeSafeButtonWidget(0, 0, 100, 20, CommonComponents.GUI_CANCEL) {
        confirmingDelete = false
    }

    var expanded = false
    var confirmingDelete = false

    fun height(hasCategory: Boolean): Int =
        if (!hasCategory) 0 else if (expanded) BAND_EXPANDED_H else BAND_COLLAPSED_H

    fun y(): Int = screen.gridTop + BAND_Y_MARGIN - screen.scrollY

    /** Widget visibility + positions follow the band's scrolled position; each hides while
     *  its row is scrolled above the viewport top (vanilla widgets render outside the card
     *  scissor, so they cannot simply be clipped). */
    fun refreshWidgets() {
        val category = screen.selectedCategory
        val by = y()
        val expandedNow = category != null && expanded
        nameField.visible = expandedNow && by + 24 >= screen.gridTop
        wheelsMinus.visible = expandedNow && by + 44 >= screen.gridTop
        wheelsPlus.visible = expandedNow && by + 44 >= screen.gridTop
        deleteButton.visible = expandedNow && by + 44 >= screen.gridTop
        if (expandedNow) {
            // Two-column layout verified by the layout_check script: swatches left,
            // name field and wheel stepper right, delete at the far right — no overlaps.
            val left = screen.gridLeft()
            val right = screen.gridRight()
            val swatchW = 10 * (BAND_SWATCH_SIZE + BAND_SWATCH_GAP) - BAND_SWATCH_GAP
            val x2 = left + 8 + swatchW + 12
            val nameWidth = Math.min(BAND_NAME_WIDTH, right - 24 - 8 - x2)
            nameField.setWidth(nameWidth)
            nameField.setX(x2); nameField.setY(by + 24)
            if (nameField.value != category.name) nameField.value = category.name
            wheelsMinus.setX(x2); wheelsMinus.setY(by + 44)
            wheelsPlus.setX(x2 + 36); wheelsPlus.setY(by + 44)
            deleteButton.setX(right - 24); deleteButton.setY(by + 44)
        }
    }

    private fun onCategoryRenamed(text: String) {
        val category = screen.selectedCategory ?: return
        val trimmed = text.trim()
        if (trimmed.isNotEmpty() && trimmed != category.name) {
            category.name = trimmed
            SkinCategoriesStore.save()
        }
    }

    /** Collapsed-bar click toggles expansion; true when consumed. */
    fun handleBarClick(mouseX: Int, mouseY: Int): Boolean {
        val inCollapsedBar = mouseY >= screen.gridTop && mouseY >= y() && mouseY < y() + BAND_COLLAPSED_H
        if (screen.selectedCategory == null || !inCollapsedBar || mouseX < screen.gridLeft()) return false
        // This branch must run whether or not the band is already expanded — it used to be
        // gated on expanded, which made the band impossible to open.
        expanded = !expanded
        refreshWidgets()
        screen.recomputeLayout()
        return true
    }

    /** Expanded-band swatch pick; true when consumed. */
    fun handleSwatchClick(mouseX: Int, mouseY: Int): Boolean {
        if (!expanded || screen.selectedCategory == null) return false
        val swatch = swatchAt(mouseX, mouseY) ?: return false
        screen.selectedCategory?.colorHex = SkinCategoryPalette.toHex(swatch)
        SkinCategoriesStore.save()
        return true
    }

    fun swatchAt(mouseX: Int, mouseY: Int): Int? {
        val by = y()
        if (!expanded || screen.selectedCategory == null || mouseY < screen.gridTop) return null
        val x0 = screen.gridLeft() + 8
        val y0 = by + 24
        if (mouseX < x0 || mouseY < y0) return null
        val hue = (mouseX - x0) / (BAND_SWATCH_SIZE + BAND_SWATCH_GAP)
        val row = (mouseY - y0) / (BAND_SWATCH_SIZE + BAND_SWATCH_GAP)
        if (hue !in 0..9 || row !in 0..1) return null
        val swatches = SkinCategoryPalette.swatches()
        val idx = hue * 2 + row
        return if (idx in swatches.indices) swatches[idx] else null
    }

    /** Band body, drawn at its scrolled position (the caller clips to the viewport). */
    fun draw(graphics: GuiGraphicsExtractor, entriesCount: Int, mouseX: Int, mouseY: Int) {
        val category = screen.selectedCategory ?: return
        val by = y()
        // The band spans the same inner area as the card grid (same margins to the page
        // border) and uses the same pre-darkened sprite as idle cards, so the transparent
        // corners stay untinted.
        val left = screen.gridLeft()
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SkinLibraryScreen.CARD_SPRITE_ACCESS, left, by, screen.gridRight() - left, height(true))
        val arrow = if (expanded) "▾" else "▸"
        val wheelsLabel = Component.translatable("simpleskinswapper.screen.library.wheels").string
        graphics.text(client.font, Component.nullToEmpty("$arrow ${category.name} · $entriesCount · ${category.maxWheels} $wheelsLabel"), left + 8, by + (BAND_COLLAPSED_H - client.font.lineHeight) / 2, 0xFFFFFFFF.toInt())
        if (expanded) {
            // Color swatch grid (10 hues × 2 rows) at the left; controls column at the right.
            // Geometry mirrored by the layout_check script — do not move without re-running it.
            val categoryColor = SkinCategoryPalette.parse(category.colorHex)
            val sx0 = left + 8
            val sy0 = by + 24
            for (i in SkinCategoryPalette.swatches().indices) {
                val hue = i / 2
                val row = i % 2
                val sx = sx0 + hue * (BAND_SWATCH_SIZE + BAND_SWATCH_GAP)
                val y0 = sy0 + row * (BAND_SWATCH_SIZE + BAND_SWATCH_GAP)
                val hovered = mouseX >= sx && mouseX < sx + BAND_SWATCH_SIZE && mouseY >= y0 && mouseY < y0 + BAND_SWATCH_SIZE
                graphics.fill(sx - 1, y0 - 1, sx + BAND_SWATCH_SIZE + 1, y0 + BAND_SWATCH_SIZE + 1,
                    if (SkinCategoryPalette.swatches()[i] == categoryColor) 0xFFFFFFFF.toInt()
                    else if (hovered) 0xFF606060.toInt() else 0xFF202020.toInt())
                graphics.fill(sx, y0, sx + BAND_SWATCH_SIZE, y0 + BAND_SWATCH_SIZE, SkinCategoryPalette.swatches()[i])
            }
            // Wheel stepper label, right of the [-] count [+] cluster
            val x2 = sx0 + 10 * (BAND_SWATCH_SIZE + BAND_SWATCH_GAP) - BAND_SWATCH_GAP + 12
            graphics.text(client.font, Component.translatable("simpleskinswapper.screen.library.wheels"), x2 + 58, by + 48, 0xFFB0B8C0.toInt())
        }
    }

    /** Delete-confirmation modal over a dimmed screen. */
    fun drawDeleteOverlay(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        val w = screen.width
        val h = screen.height
        graphics.fill(0, 0, w, h, 0x88000000.toInt())
        val boxW = 260
        val boxH = 80
        val bx = w / 2 - boxW / 2
        val by = h / 2 - boxH / 2
        graphics.fill(bx, by, bx + boxW, by + boxH, 0xFF1A2535.toInt())
        graphics.fill(bx, by, bx + boxW, by + 1, 0xFFFFFFFF.toInt())
        val question = Component.translatable("simpleskinswapper.screen.library.delete_category_question")
        // Simple word wrap (Component.string + font.width work on every target version).
        val wrapped = ArrayList<String>()
        var currentLine = ""
        for (word in question.string.split(" ")) {
            val candidate = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (client.font.width(candidate) > boxW - 16 && currentLine.isNotEmpty()) {
                wrapped.add(currentLine)
                currentLine = word
            } else {
                currentLine = candidate
            }
        }
        if (currentLine.isNotEmpty()) wrapped.add(currentLine)
        var ly = by + 8
        for (lineText in wrapped) {
            graphics.text(client.font, Component.nullToEmpty(lineText), bx + 8, ly, 0xFFFFFFFF.toInt())
            ly += client.font.lineHeight
        }
        confirmOverlayButton.setX(bx + 8)
        confirmOverlayButton.setY(by + boxH - 28)
        cancelOverlayButton.setX(bx + boxW - 108)
        cancelOverlayButton.setY(by + boxH - 28)
        //? if >=26.1 {
        confirmOverlayButton.extractRenderState(graphics, mouseX, mouseY, delta)
        cancelOverlayButton.extractRenderState(graphics, mouseX, mouseY, delta)
        //?} else {
        /*confirmOverlayButton.render(graphics, mouseX, mouseY, delta)
        cancelOverlayButton.render(graphics, mouseX, mouseY, delta)
        *///?}
    }

    companion object {
        // GRID_MARGIN: the band scrolls with the grid content, offset from the viewport top.
        private const val BAND_Y_MARGIN = 4
        private const val BAND_COLLAPSED_H = 18
        private const val BAND_EXPANDED_H = 72
        private const val BAND_NAME_WIDTH = 140
        private const val BAND_FIELD_HEIGHT = 16
        private const val BAND_SWATCH_SIZE = 12
        private const val BAND_SWATCH_GAP = 2
    }
}
