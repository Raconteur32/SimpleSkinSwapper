package fr.raconteur.simpleskinswapper.gui.library

import com.mojang.blaze3d.platform.InputConstants
import fr.raconteur.simpleskinswapper.changeskin.SkinChange
import fr.raconteur.simpleskinswapper.changeskin.SkinSwapperState
import fr.raconteur.simpleskinswapper.gui.EdgeSafeButtonWidget
import fr.raconteur.simpleskinswapper.gui.SkinEntry
import fr.raconteur.simpleskinswapper.gui.SkinRenderer
import fr.raconteur.simpleskinswapper.gui.SkinType
import fr.raconteur.simpleskinswapper.gui.SkinUtils
import fr.raconteur.simpleskinswapper.overlayMessage
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.ComponentPath
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.events.ContainerEventHandler
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.navigation.FocusNavigationEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.core.ClientAsset
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.PlayerModelType
import net.minecraft.world.entity.player.PlayerSkin

/**
 * One skin card in the library grid. The whole card is the reorder grab (press + move,
 * owned by the parent screen) while a press-and-release without real movement opens the
 * detail overlay; rotation lives only in the overlay. The card shows its 1-based position
 * and, when its position falls inside the category's wheel allocation, an allocation
 * marker strip in the category color.
 */
class SkinLibraryCard(
    private val parent: SkinLibraryScreen,
    internal val entry: SkinEntry,
    width: Int,
    height: Int
) : AbstractWidget(0, 0, width, height, Component.nullToEmpty(entry.displayName)), ContainerEventHandler, GridSlottedWidget {

    private val client: Minecraft = Minecraft.getInstance()

    private val cardButtons = ArrayList<EdgeSafeButtonWidget>()
    private var focusedChild: GuiEventListener? = null
    private var dragging = false

    private val applyButton: EdgeSafeButtonWidget

    private var pendingDetailOpen = false
    private var pressX = 0
    private var pressY = 0
    private var hoverAnimFactor = 0.0F
    private var lastHoverAnimUpdateNanos = 0L

    // The grid viewport (the page's inner area inside its baked border), updated by the
    // parent every frame. All cards render through this one fixed scissor rect, so cards
    // sliding in and out are smoothly half-clipped instead of popping in and out.
    override var clipLeft = Int.MIN_VALUE
    override var clipTop = Int.MIN_VALUE
    override var clipRight = Int.MAX_VALUE
    override var clipBottom = Int.MAX_VALUE

    init {
        // Bottom row: only the apply button. The card body is the reorder grab (press +
        // move) and a press-and-release without movement opens the detail overlay.
        applyButton = EdgeSafeButtonWidget(
            BUTTON_MARGIN, height - BUTTON_HEIGHT - BUTTON_MARGIN,
            width - BUTTON_MARGIN * 2, BUTTON_HEIGHT,
            Component.translatable("simpleskinswapper.screen.carousel.apply")
        ) { applySkin() }
        addChild(applyButton)
    }

    private fun addChild(button: EdgeSafeButtonWidget) {
        cardButtons.add(button)
    }

    override fun children(): List<GuiEventListener> = cardButtons

    override fun isDragging(): Boolean = dragging

    override fun setDragging(dragging: Boolean) {
        this.dragging = dragging
    }

    override fun getFocused(): GuiEventListener? = focusedChild

    override fun setFocused(focused: GuiEventListener?) {
        this.focusedChild = focused
    }

    override fun isFocused(): Boolean = super<AbstractWidget>.isFocused()

    override fun setFocused(focused: Boolean) = super<AbstractWidget>.setFocused(focused)

    override fun nextFocusPath(event: FocusNavigationEvent): ComponentPath? =
        super<AbstractWidget>.nextFocusPath(event)

    override fun updateWidgetNarration(output: NarrationElementOutput) = defaultButtonNarrationText(output)

    fun getEntry(): SkinEntry = entry

    private fun isMouseOverCard(mouseX: Int, mouseY: Int): Boolean =
        mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height

    private fun applySkin() {
        if (!SkinSwapperState.beginSwap()) return
        SkinChange.changeSkin(
            entry.file, entry.skinType, entry.textureId,
            { showOverlay(Component.translatable("simpleskinswapper.message.success")) },
            { err -> showOverlay(Component.translatable("simpleskinswapper.message.error", err)) }
        )
        parent.onClose()
        showOverlay(Component.translatable("simpleskinswapper.message.applying"))
    }

    private fun showOverlay(text: Component) {
        client.player?.overlayMessage(text)
    }

    override fun overridePosition(newX: Int, newY: Int) {
        val dx = newX - x
        val dy = newY - y
        if (dx == 0 && dy == 0) return
        setX(newX)
        setY(newY)
        for (child in cardButtons) {
            child.setX(child.x + dx)
            child.setY(child.y + dy)
        }
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        // Ignore clicks outside the visible (clipped) part of the card so a card sliding
        // under the config band or over the footer never steals their clicks.
        val mx = event.x().toInt()
        val my = event.y().toInt()
        if (!SkinUtils.inRect(mx, my, clipLeft, clipTop, clipRight - clipLeft, clipBottom - clipTop)) return false
        for (child in cardButtons) {
            if (child.y >= clipBottom) continue
            if (child.mouseClicked(event, doubleClick)) {
                focusedChild = child
                if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) dragging = true
                return true
            }
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && isMouseOverCard(event.x().toInt(), event.y().toInt())) {
            // The whole card is the reorder grab. The actual drag starts only once the
            // press moves (converted in mouseDragged); a release without real movement
            // opens the detail overlay.
            pendingDetailOpen = true
            pressX = mx
            pressY = my
            return true
        }
        return false
    }

    override fun mouseDragged(event: MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean {
        if (pendingDetailOpen && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            val mx = event.x().toInt()
            val my = event.y().toInt()
            if (Math.abs(mx - pressX) + Math.abs(my - pressY) > REORDER_START_SLOP) {
                pendingDetailOpen = false
                // Deferred: the screen starts the drag (and unregisters this card) once
                // its children iteration is over — removing a child mid-iteration risks
                // a ConcurrentModificationException.
                parent.requestCardReorder(this, mx, my)
            }
            return true
        }
        val focused = focusedChild
        if (dragging && focused != null) {
            return focused.mouseDragged(event, deltaX, deltaY)
        }
        return false
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (pendingDetailOpen) {
            pendingDetailOpen = false
            parent.openDetail(this)
            return true
        }
        if (dragging) {
            dragging = false
            return focusedChild?.mouseReleased(event) ?: false
        }
        return false
    }

    private fun easeTowards(current: Float, target: Float, lastUpdateNanos: Long): Pair<Float, Long> {
        val now = System.nanoTime()
        val dt = if (lastUpdateNanos == 0L) 0.0F else (now - lastUpdateNanos) / 1_000_000_000.0F
        val t = 1.0F - Math.exp((-SPRING_RETURN_SPEED * dt).toDouble()).toFloat()
        var eased = Mth.lerp(t, current, target)
        if (Math.abs(eased - target) < SPRING_SNAP_EPSILON) eased = target
        return eased to now
    }

    private fun updateHoverAnimation(mouseX: Int, mouseY: Int) {
        // Dragged cards own the animation: the reorder-dragged card animates nowhere and
        // cards beneath it stay static.
        val target = when {
            parent.reorderDraggingCard === this -> 0.0F
            parent.reorderDraggingCard != null -> 0.0F
            isMouseOverCard(mouseX, mouseY) -> 1.0F
            else -> 0.0F
        }
        val (eased, now) = easeTowards(hoverAnimFactor, target, lastHoverAnimUpdateNanos)
        hoverAnimFactor = eased
        lastHoverAnimUpdateNanos = now
    }

    private fun drawBackground(graphics: GuiGraphicsExtractor, hovered: Boolean, allocated: Boolean, allocationColor: Int) {
        // Vanilla recipe-book clickable-recipe frame (highlight variant on hover) over a
        // dark interior; the allocation marker strip is drawn on top of the frame.
        SkinLibraryScreen.drawCardFrame(graphics, x, y, width, height, hovered)
        if (allocated) {
            graphics.fill(x + 1, y + 1, x + width - 1, y + 1 + MARKER_HEIGHT, allocationColor)
        }
    }

    //? if >=26.1 {
    override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
    //?} else {
    /*override fun renderWidget(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
    *///?}
        // Every card clips to the same fixed viewport rect (the page's inner area): cards
        // sliding out are smoothly half-clipped, never popping. The reorder-dragged card
        // floats unclipped (it is drawn manually by the screen). Cards entirely outside
        // the viewport skip drawing altogether (their scissored output would be empty).
        val floating = parent.reorderDraggingCard === this
        val onScreen = y + height > clipTop && y < clipBottom && x + width > clipLeft && x < clipRight
        val clipped = !floating && onScreen
        if (clipped) graphics.enableScissor(clipLeft, clipTop, clipRight, clipBottom)

        if (onScreen || floating) {
            drawCardChrome(graphics, mouseX, mouseY, delta)
        }

        updateHoverAnimation(mouseX, mouseY)

        if (onScreen || floating) {
            drawCardHeader(graphics)
            drawCardPreview(graphics)
        }

        if (clipped) graphics.disableScissor()
    }

    private fun drawCardChrome(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        val allocationColor = parent.allocationColorFor(this)
        drawBackground(graphics, hovered = !parent.reorderDraggingCard.let { it != null && it !== this } && isMouseOverCard(mouseX, mouseY), allocated = allocationColor != null, allocationColor = allocationColor ?: 0)

        for (child in cardButtons) {
            //? if >=26.1 {
            child.extractRenderState(graphics, mouseX, mouseY, delta)
            //?} else {
            /*child.render(graphics, mouseX, mouseY, delta)
            *///?}
        }
    }

    private fun drawCardHeader(graphics: GuiGraphicsExtractor) {
        // Header strip: number left, name centered, handle right — all on one line.
        val margin = client.font.lineHeight / 2
        val textY = y + margin

        // Position number: left flank of the header; tinted with the category color while
        // the card sits inside the wheel allocation (single-line form: stonecutter rewrites
        // .text(client.font, Component for <26.1).
        graphics.text(client.font, Component.nullToEmpty((parent.indexOfCard(this) + 1).toString()), x + 5, textY, allocationTextColor())

        val nameColor = if (this.active) 0xFFFFFFFF.toInt() else 0xFF808080.toInt()
        // Left-aligned right after the number, truncated with an ellipsis before the
        // handle — same treatment as the category tab names (no hard clipping).
        val nameX = x + 15
        val nameRight = x + width - 16
        val available = nameRight - nameX
        var text = entry.displayName
        if (available >= 8 && client.font.width(text) > available) {
            while (text.isNotEmpty() && client.font.width("$text...") > available) text = text.dropLast(1)
            text += "..."
        }
        graphics.text(client.font, Component.nullToEmpty(text), nameX, textY, nameColor)
    }

    private fun allocationTextColor(): Int = parent.allocationColorFor(this) ?: 0xFF909090.toInt()

    private fun drawCardPreview(graphics: GuiGraphicsExtractor) {
        entry.ensureTextureLoaded()

        val previewTop = y + HEADER_HEIGHT + 2
        val previewBottom = y + height - BUTTON_HEIGHT - BUTTON_MARGIN * 2
        val previewLeft = x + 1
        val previewRight = x + width - 1

        val textureId = entry.textureId
        if (textureId != null) {
            val size = ((previewBottom - previewTop) * 0.5f).toInt()
            val skinTextures = PlayerSkin(
                ClientAsset.DownloadedTexture(textureId, ""), null, null,
                if (entry.skinType == SkinType.SLIM) PlayerModelType.SLIM else PlayerModelType.WIDE,
                true
            )
            SkinRenderer.renderPlayerRotatable(
                graphics, intArrayOf(previewLeft, previewTop, previewRight, previewBottom),
                size, skinTextures, 0.0F, 0.0F, hoverAnimFactor
            )
        }
    }

    companion object {
        // Compact card chrome: 16px button rows, 3px margins, 14px header strip.
        private const val BUTTON_HEIGHT = 16
        private const val BUTTON_MARGIN = 3

        // Header strip (marker + number + name) height in px.
        private const val HEADER_HEIGHT = 14

        // Allocation marker strip thickness in px.
        private const val MARKER_HEIGHT = 2

        // Press movement (Manhattan px) beyond which a card press becomes a reorder drag
        // instead of a click.
        private const val REORDER_START_SLOP = 6

        private const val SPRING_RETURN_SPEED = 10.0F
        private const val SPRING_SNAP_EPSILON = 0.05F
    }
}
