package fr.raconteur.simpleskinswapper.gui

import com.mojang.blaze3d.platform.InputConstants
import fr.raconteur.simpleskinswapper.changeskin.SkinChange
import fr.raconteur.simpleskinswapper.overlayMessage
import fr.raconteur.simpleskinswapper.changeskin.SkinSwapperState
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

class SkinCard(
    private val parent: SkinCarouselScreen,
    internal val entry: SkinEntry,
    width: Int,
    height: Int
) : AbstractWidget(0, 0, width, height, Component.nullToEmpty(entry.displayName)), ContainerEventHandler {

    private val client: Minecraft = Minecraft.getInstance()

    // Child buttons use coordinates relative to the card; overridePosition shifts them along
    // with the card (vanilla widgets are absolutely positioned, so they don't follow on their own).
    private val cardButtons = ArrayList<EdgeSafeButtonWidget>()
    private var focusedChild: GuiEventListener? = null
    private var dragging = false

    private val applyButton: EdgeSafeButtonWidget
    private val leftArrow: EdgeSafeButtonWidget
    private val rightArrow: EdgeSafeButtonWidget
    private val typeButton: EdgeSafeButtonWidget
    private val deleteButton: EdgeSafeButtonWidget
    private val confirmDeleteButton: EdgeSafeButtonWidget

    private var confirmingDelete = false
    private var rotatingPreview = false
    private var previewYaw = 0.0F
    private var previewPitch = 0.0F
    private var lastSpringUpdateNanos = 0L
    private var hoverAnimFactor = 0.0F
    private var lastHoverAnimUpdateNanos = 0L

    init {
        val halfW = (width - BUTTON_MARGIN * 3) / 2

        applyButton = EdgeSafeButtonWidget(
            BUTTON_MARGIN, height - BUTTON_HEIGHT * 3 - BUTTON_MARGIN * 3,
            width - BUTTON_MARGIN * 2, BUTTON_HEIGHT,
            Component.translatable("simpleskinswapper.screen.carousel.apply")
        ) { applySkin() }
        addChild(applyButton)

        typeButton = EdgeSafeButtonWidget(
            BUTTON_MARGIN, height - BUTTON_HEIGHT * 2 - BUTTON_MARGIN * 2,
            halfW, BUTTON_HEIGHT,
            typeLabel()
        ) { toggleType() }
        addChild(typeButton)

        deleteButton = EdgeSafeButtonWidget(
            BUTTON_MARGIN * 2 + halfW, height - BUTTON_HEIGHT * 2 - BUTTON_MARGIN * 2,
            halfW, BUTTON_HEIGHT,
            Component.translatable("simpleskinswapper.screen.carousel.delete")
        ) { beginDeleteConfirmation() }
        addChild(deleteButton)

        leftArrow = EdgeSafeButtonWidget(
            BUTTON_MARGIN, height - BUTTON_HEIGHT - BUTTON_MARGIN,
            halfW, BUTTON_HEIGHT,
            Component.literal("←")
        ) { parent.moveCard(this, -1) }
        addChild(leftArrow)

        rightArrow = EdgeSafeButtonWidget(
            BUTTON_MARGIN * 2 + halfW, height - BUTTON_HEIGHT - BUTTON_MARGIN,
            halfW, BUTTON_HEIGHT,
            Component.literal("→")
        ) { parent.moveCard(this, +1) }
        addChild(rightArrow)

        val deleteBlockTop = height - BUTTON_HEIGHT * 3 - BUTTON_MARGIN * 3
        val deleteBlockHeight = BUTTON_HEIGHT * 3 + BUTTON_MARGIN * 2
        confirmDeleteButton = EdgeSafeButtonWidget(
            BUTTON_MARGIN, deleteBlockTop + (deleteBlockHeight - BUTTON_HEIGHT) / 2,
            width - BUTTON_MARGIN * 2, BUTTON_HEIGHT,
            Component.translatable("simpleskinswapper.screen.carousel.delete_confirm")
        ) { confirmDelete() }
        confirmDeleteButton.visible = false
        addChild(confirmDeleteButton)
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

    // ContainerEventHandler defaults conflict with AbstractWidget's implementations.
    // The card itself is a leaf for keyboard focus/narration (children are mouse-driven).
    override fun isFocused(): Boolean = super<AbstractWidget>.isFocused()

    override fun setFocused(focused: Boolean) = super<AbstractWidget>.setFocused(focused)

    override fun nextFocusPath(event: FocusNavigationEvent): ComponentPath? =
        super<AbstractWidget>.nextFocusPath(event)

    override fun updateWidgetNarration(output: NarrationElementOutput) = defaultButtonNarrationText(output)

    fun updateArrowStates(canMoveLeft: Boolean, canMoveRight: Boolean) {
        leftArrow.active = canMoveLeft
        rightArrow.active = canMoveRight
    }

    fun getEntry(): SkinEntry = entry

    private fun typeLabel(): Component =
        Component.translatable("simpleskinswapper.screen.carousel.skin_type." + entry.skinType.mojangVariant)

    private fun toggleType() {
        entry.skinType = if (entry.skinType == SkinType.CLASSIC) SkinType.SLIM else SkinType.CLASSIC
        SkinTypeStore.setType(entry.file.name, entry.skinType)
        typeButton.message = typeLabel()
    }

    private fun beginDeleteConfirmation() {
        confirmingDelete = true
        setNormalButtonsVisible(false)
        confirmDeleteButton.visible = true
    }

    private fun cancelDeleteConfirmation() {
        confirmingDelete = false
        setNormalButtonsVisible(true)
        confirmDeleteButton.visible = false
    }

    private fun confirmDelete() {
        confirmingDelete = false
        parent.deleteEntry(entry)
    }

    private fun setNormalButtonsVisible(visible: Boolean) {
        applyButton.visible = visible
        typeButton.visible = visible
        deleteButton.visible = visible
        leftArrow.visible = visible
        rightArrow.visible = visible
    }

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

    fun overridePosition(newX: Int, newY: Int) {
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

    // Child event routing is written out explicitly: Kotlin cannot call Java interface
    // default methods (ContainerEventHandler.mouseClicked et al.) via a qualified super call.
    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        for (child in cardButtons) {
            if (child.mouseClicked(event, doubleClick)) {
                focusedChild = child
                if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) dragging = true
                return true
            }
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && isMouseOverCard(event.x().toInt(), event.y().toInt())) {
            rotatingPreview = true
            parent.dragRotatingCard = this
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

    /** Eases [current] toward [target] with the same exponential family as the drag spring-back. */
    private fun easeTowards(current: Float, target: Float, lastUpdateNanos: Long): Pair<Float, Long> {
        val now = System.nanoTime()
        val dt = if (lastUpdateNanos == 0L) 0.0F else (now - lastUpdateNanos) / 1_000_000_000.0F
        val t = 1.0F - Math.exp((-SPRING_RETURN_SPEED * dt).toDouble()).toFloat()
        var eased = Mth.lerp(t, current, target)
        if (Math.abs(eased - target) < SPRING_SNAP_EPSILON) eased = target
        return eased to now
    }

    private fun updateHoverAnimation(mouseX: Int, mouseY: Int) {
        // The drag-rotated card keeps animating wherever the cursor goes; other cards stay
        // static while a drag is in progress so the animation follows the dragged skin only.
        val target = when {
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

    private fun drawBackground(graphics: GuiGraphicsExtractor) {
        val borderColor = if (this.active) 0xDF000000.toInt() else 0x5F000000
        drawBorder(graphics, x, y, width, height, borderColor)
        graphics.fill(
            x + 1, y + 1, x + width - 1, y + height - 1,
            if (this.active) 0x7F000000.toInt() else 0x0D000000
        )
    }

    private fun drawBorder(ctx: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, color: Int) {
        ctx.fill(x, y, x + w, y + 1, color)
        ctx.fill(x, y + h - 1, x + w, y + h, color)
        ctx.fill(x, y + 1, x + 1, y + h - 1, color)
        ctx.fill(x + w - 1, y + 1, x + w, y + h - 1, color)
    }

    //? if >=26.1 {
    override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
    //?} else {
    /*override fun renderWidget(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
    *///?}
        if (confirmingDelete && !isMouseOverCard(mouseX, mouseY)) {
            cancelDeleteConfirmation()
        }

        drawBackground(graphics)

        for (child in cardButtons) {
            //? if >=26.1 {
            child.extractRenderState(graphics, mouseX, mouseY, delta)
            //?} else {
            /*child.render(graphics, mouseX, mouseY, delta)
            *///?}
        }

        updateSpringBack()
        updateHoverAnimation(mouseX, mouseY)

        val margin = client.font.lineHeight / 2
        val nameColor = if (this.active) 0xFFFFFFFF.toInt() else 0xFF808080.toInt()
        val textWidth = client.font.width(entry.displayName)
        val textX = x + (width - textWidth) / 2
        val textY = y + margin
        graphics.enableScissor(x + margin, textY, x + width - margin, textY + client.font.lineHeight)
        graphics.text(client.font, Component.nullToEmpty(entry.displayName), textX, textY, nameColor)
        graphics.disableScissor()

        entry.ensureTextureLoaded()

        val previewTop = y + margin + client.font.lineHeight + 2
        val previewBottom = y + height - BUTTON_HEIGHT * 3 - BUTTON_MARGIN * 4
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
                graphics, previewLeft, previewTop, previewRight, previewBottom,
                size, skinTextures, previewYaw, previewPitch, hoverAnimFactor
            )
        }
    }

    companion object {
        private const val BUTTON_HEIGHT = 20
        private const val BUTTON_MARGIN = 4

        // Vertical drag limit: enough to see under the chin / over the head without flipping past horizontal.
        private const val MAX_PITCH = 45.0F
        private const val DRAG_SENSITIVITY = 1.0F

        // Higher = snappier spring-back to the initial orientation once the drag is released.
        private const val SPRING_RETURN_SPEED = 10.0F
        private const val SPRING_SNAP_EPSILON = 0.05F
    }
}
