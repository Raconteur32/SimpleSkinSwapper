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
 * One skin card in the library grid. Drag intent is spatially split: the model area rotates
 * the preview (existing behavior), while the card frame or the ⋮⋮ handle starts a reorder
 * drag owned by the parent screen. The card shows its 1-based position and, when its position
 * falls inside the category's wheel allocation, an allocation marker strip in the category color.
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

    private var rotatingPreview = false
    private var pendingDetailOpen = false
    private var previewYaw = 0.0F
    private var previewPitch = 0.0F
    private var lastSpringUpdateNanos = 0L
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
        // Single bottom row: only the replay (apply) button — model type and delete live
        // in the detail overlay, freeing preview space on the card.
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

    /** Frame band: within [FRAME_BAND] px of a card edge — a reorder grab zone. */
    private fun isOnFrame(mouseX: Int, mouseY: Int): Boolean {
        val inOuter = isMouseOverCard(mouseX, mouseY)
        val inInner = mouseX >= x + FRAME_BAND && mouseX < x + width - FRAME_BAND &&
            mouseY >= y + FRAME_BAND && mouseY < y + height - FRAME_BAND
        return inOuter && !inInner
    }

    private fun isOnHandle(mouseX: Int, mouseY: Int): Boolean {
        val r = handleRect()
        return mouseX >= r.first && mouseX < r.first + HANDLE && mouseY >= r.second && mouseY < r.second + HANDLE
    }

    /** ⋮⋮ handle: right flank of the preview area, vertically centered. */
    private fun handleRect(): Pair<Int, Int> = (x + width - HANDLE - 4) to (previewCenterY() - HANDLE / 2)

    /** Vertical center of the preview area — shared by the hit test and the renderer. */
    private fun previewCenterY(): Int {
        val top = y + HEADER_HEIGHT + 2
        val bottom = y + height - BUTTON_HEIGHT - BUTTON_MARGIN * 2
        return (top + bottom) / 2
    }

    private fun isOnModel(mouseX: Int, mouseY: Int): Boolean {
        val top = y + HEADER_HEIGHT + 2
        val bottom = y + height - BUTTON_HEIGHT - BUTTON_MARGIN * 2
        return mouseX >= x + 1 && mouseX < x + width - 1 && mouseY >= top && mouseY < bottom
    }

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
            val mx = event.x().toInt()
            val my = event.y().toInt()
            if (isOnHandle(mx, my) || isOnFrame(mx, my)) {
                parent.beginCardReorder(this, mx, my)
                return true
            }
            if (isOnModel(mx, my)) {
                rotatingPreview = true
                parent.dragRotatingCard = this
                return true
            }
            // Plain click on the card body: open the detail overlay on release.
            pendingDetailOpen = true
            return true
        }
        return false
    }

    override fun mouseDragged(event: MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean {
        if (rotatingPreview && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            previewYaw = Mth.wrapDegrees((previewYaw - deltaX.toFloat() * DRAG_SENSITIVITY).toDouble()).toFloat()
            previewPitch = Mth.clamp(previewPitch - deltaY.toFloat() * DRAG_SENSITIVITY, -MAX_PITCH, MAX_PITCH)
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
        if (rotatingPreview) {
            rotatingPreview = false
            if (parent.dragRotatingCard === this) parent.dragRotatingCard = null
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
        // Dragged cards own the animation: the reorder-dragged card animates nowhere and cards
        // beneath it stay static; the drag-rotated card keeps animating wherever the cursor goes.
        val target = when {
            parent.reorderDraggingCard === this -> 0.0F
            parent.reorderDraggingCard != null -> 0.0F
            rotatingPreview -> 1.0F
            parent.dragRotatingCard != null -> 0.0F
            isMouseOverCard(mouseX, mouseY) -> 1.0F
            else -> 0.0F
        }
        val (eased, now) = easeTowards(hoverAnimFactor, target, lastHoverAnimUpdateNanos)
        hoverAnimFactor = eased
        lastHoverAnimUpdateNanos = now
    }

    private fun updateSpringBack() {
        val now = System.nanoTime()
        val dt = if (lastSpringUpdateNanos == 0L) 0.0F else (now - lastSpringUpdateNanos) / 1_000_000_000.0F
        lastSpringUpdateNanos = now

        if (rotatingPreview || (previewYaw == 0.0F && previewPitch == 0.0F)) {
            return
        }

        val t = 1.0F - Math.exp((-SPRING_RETURN_SPEED * dt).toDouble()).toFloat()
        previewYaw = Mth.lerp(t, previewYaw, 0.0F)
        previewPitch = Mth.lerp(t, previewPitch, 0.0F)

        if (Math.abs(previewYaw) < SPRING_SNAP_EPSILON) previewYaw = 0.0F
        if (Math.abs(previewPitch) < SPRING_SNAP_EPSILON) previewPitch = 0.0F
    }

    private fun drawBackground(graphics: GuiGraphicsExtractor, hovered: Boolean, allocated: Boolean, allocationColor: Int) {
        // Vanilla recipe-book clickable-recipe frame (highlight variant on hover) over a
        // dark interior; the allocation marker strip is drawn on top of the frame.
        SkinLibraryScreen.drawCardFrame(graphics, x, y, width, height, hovered)
        if (allocated) {
            graphics.fill(x + 1, y + 1, x + width - 1, y + 1 + MARKER_HEIGHT, allocationColor)
        }
    }

    private fun drawHandle(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val hx = handleRect().first
        val hy = handleRect().second
        val hovered = mouseX >= hx - 1 && mouseX < hx + HANDLE + 1 && mouseY >= hy - 1 && mouseY < hy + HANDLE + 1
        if (hovered) {
            graphics.fill(hx - 1, hy - 1, hx + HANDLE + 1, hy + HANDLE + 1, 0x30FFFFFF)
        }
        val dotColor = 0xFFB0B8C0.toInt()
        for (col in 0..1) {
            for (row in 0..2) {
                val px = hx + 3 + col * 5
                val py = hy + 2 + row * 4
                graphics.fill(px, py, px + 2, py + 2, dotColor)
            }
        }
    }

    //? if >=26.1 {
    // Complexity debt: layered card render dispatch — deferred to the card/Extract Class
    // refactoring change.
    @Suppress("CyclomaticComplexMethod")
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

        val allocationColor = parent.allocationColorFor(this)
        if (onScreen || floating) {
            drawBackground(graphics, hovered = !parent.reorderDraggingCard.let { it != null && it !== this } && isMouseOverCard(mouseX, mouseY), allocated = allocationColor != null, allocationColor = allocationColor ?: 0)
            drawHandle(graphics, mouseX, mouseY)

            for (child in cardButtons) {
                //? if >=26.1 {
                child.extractRenderState(graphics, mouseX, mouseY, delta)
                //?} else {
                /*child.render(graphics, mouseX, mouseY, delta)
                *///?}
            }
        }

        updateSpringBack()
        updateHoverAnimation(mouseX, mouseY)

        if (onScreen || floating) {
            val previewTop = y + HEADER_HEIGHT + 2
            val previewBottom = y + height - BUTTON_HEIGHT - BUTTON_MARGIN * 2
            val centerY = previewCenterY()

            // Position number: left flank of the preview area, vertically centered; tinted with
            // the category color while the card sits inside the wheel allocation (single-line
            // form: stonecutter rewrites .text(client.font, Component for <26.1).
            graphics.text(client.font, Component.nullToEmpty((parent.indexOfCard(this) + 1).toString()), x + 5, centerY - 4, allocationColor ?: 0xFF909090.toInt())

            val margin = client.font.lineHeight / 2
            val nameColor = if (this.active) 0xFFFFFFFF.toInt() else 0xFF808080.toInt()
            val textWidth = client.font.width(entry.displayName)
            val textX = x + (width - textWidth) / 2
            val textY = y + margin
            // The whole header line belongs to the name; number and handle live on the flanks.
            val nameLeft = x + 4
            val nameRight = x + width - 4
            // Guard the scissor: a zero/negative-size scissor rectangle crashes MC 26.2.
            if (nameRight - nameLeft >= 8) {
                graphics.enableScissor(nameLeft, textY, nameRight, textY + client.font.lineHeight)
                graphics.text(client.font, Component.nullToEmpty(entry.displayName), textX, textY, nameColor)
                graphics.disableScissor()
            } else {
                graphics.text(client.font, Component.nullToEmpty(entry.displayName), textX, textY, nameColor)
            }

            entry.ensureTextureLoaded()

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
                    size, skinTextures, previewYaw, previewPitch, hoverAnimFactor
                )
            }
        }

        if (clipped) graphics.disableScissor()
    }

    companion object {
        // Compact card chrome: 16px button rows, 3px margins, 14px header strip.
        private const val BUTTON_HEIGHT = 16
        private const val BUTTON_MARGIN = 3

        // Header strip (marker + number + name + handle) height in px.
        private const val HEADER_HEIGHT = 14

        // Reorder grab zones: the ⋮⋮ handle and a [FRAME_BAND] px band along the card edges.
        private const val FRAME_BAND = 4
        private const val HANDLE = 12

        // Allocation marker strip thickness in px.
        private const val MARKER_HEIGHT = 2

        private const val MAX_PITCH = 45.0F
        private const val DRAG_SENSITIVITY = 1.0F
        private const val SPRING_RETURN_SPEED = 10.0F
        private const val SPRING_SNAP_EPSILON = 0.05F
    }
}
