package fr.raconteur.simpleskinswapper.gui

import dev.lambdaurora.spruceui.Position
import dev.lambdaurora.spruceui.render.SpruceGuiGraphics
import dev.lambdaurora.spruceui.widget.SpruceButtonWidget
import dev.lambdaurora.spruceui.widget.container.SpruceContainerWidget
import fr.raconteur.simpleskinswapper.changeskin.SkinChange
import fr.raconteur.simpleskinswapper.overlayMessage
import fr.raconteur.simpleskinswapper.changeskin.SkinSwapperState
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.core.ClientAsset
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.PlayerModelType
import net.minecraft.world.entity.player.PlayerSkin
import org.lwjgl.glfw.GLFW

class SkinCard(
    private val parent: SkinCarouselScreen,
    internal val entry: SkinEntry,
    width: Int,
    height: Int
) : SpruceContainerWidget(Position.of(0, 0), width, height) {

    private val applyButton: SpruceButtonWidget
    private val leftArrow: SpruceButtonWidget
    private val rightArrow: SpruceButtonWidget
    private val typeButton: SpruceButtonWidget
    private val deleteButton: SpruceButtonWidget
    private val confirmDeleteButton: SpruceButtonWidget

    private var confirmingDelete = false
    private var rotatingPreview = false
    private var previewYaw = 0.0F
    private var previewPitch = 0.0F
    private var lastSpringUpdateNanos = 0L

    init {
        val halfW = (width - BUTTON_MARGIN * 3) / 2

        applyButton = SpruceButtonWidget(
            Position.of(BUTTON_MARGIN, height - BUTTON_HEIGHT * 3 - BUTTON_MARGIN * 3),
            width - BUTTON_MARGIN * 2, BUTTON_HEIGHT,
            Component.translatable("simpleskinswapper.screen.carousel.apply")
        ) { applySkin() }
        addChild(applyButton)

        typeButton = SpruceButtonWidget(
            Position.of(BUTTON_MARGIN, height - BUTTON_HEIGHT * 2 - BUTTON_MARGIN * 2),
            halfW, BUTTON_HEIGHT,
            typeLabel()
        ) { toggleType() }
        addChild(typeButton)

        deleteButton = SpruceButtonWidget(
            Position.of(BUTTON_MARGIN * 2 + halfW, height - BUTTON_HEIGHT * 2 - BUTTON_MARGIN * 2),
            halfW, BUTTON_HEIGHT,
            Component.translatable("simpleskinswapper.screen.carousel.delete")
        ) { beginDeleteConfirmation() }
        addChild(deleteButton)

        leftArrow = SpruceButtonWidget(
            Position.of(BUTTON_MARGIN, height - BUTTON_HEIGHT - BUTTON_MARGIN),
            halfW, BUTTON_HEIGHT,
            Component.literal("←")
        ) { parent.moveCard(this, -1) }
        addChild(leftArrow)

        rightArrow = SpruceButtonWidget(
            Position.of(BUTTON_MARGIN * 2 + halfW, height - BUTTON_HEIGHT - BUTTON_MARGIN),
            halfW, BUTTON_HEIGHT,
            Component.literal("→")
        ) { parent.moveCard(this, +1) }
        addChild(rightArrow)

        val deleteBlockTop = height - BUTTON_HEIGHT * 3 - BUTTON_MARGIN * 3
        val deleteBlockHeight = BUTTON_HEIGHT * 3 + BUTTON_MARGIN * 2
        confirmDeleteButton = SpruceButtonWidget(
            Position.of(BUTTON_MARGIN, deleteBlockTop + (deleteBlockHeight - BUTTON_HEIGHT) / 2),
            width - BUTTON_MARGIN * 2, BUTTON_HEIGHT,
            Component.translatable("simpleskinswapper.screen.carousel.delete_confirm")
        ) { confirmDelete() }
        confirmDeleteButton.isVisible = false
        addChild(confirmDeleteButton)
    }

    fun updateArrowStates(canMoveLeft: Boolean, canMoveRight: Boolean) {
        leftArrow.isActive = canMoveLeft
        rightArrow.isActive = canMoveRight
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
        confirmDeleteButton.isVisible = true
    }

    private fun cancelDeleteConfirmation() {
        confirmingDelete = false
        setNormalButtonsVisible(true)
        confirmDeleteButton.isVisible = false
    }

    private fun confirmDelete() {
        confirmingDelete = false
        parent.deleteEntry(entry)
    }

    private fun setNormalButtonsVisible(visible: Boolean) {
        applyButton.isVisible = visible
        typeButton.isVisible = visible
        deleteButton.isVisible = visible
        leftArrow.isVisible = visible
        rightArrow.isVisible = visible
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

    fun overridePosition(x: Int, y: Int) {
        this.position.move(x, y)
    }

    override fun onMouseClick(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (super.onMouseClick(event, doubleClick)) {
            return true
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            rotatingPreview = true
            return true
        }
        return false
    }

    override fun onMouseDrag(event: MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean {
        if (rotatingPreview && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            previewYaw = Mth.wrapDegrees((previewYaw - deltaX.toFloat() * DRAG_SENSITIVITY).toDouble()).toFloat()
            previewPitch = Mth.clamp(previewPitch - deltaY.toFloat() * DRAG_SENSITIVITY, -MAX_PITCH, MAX_PITCH)
            return true
        }
        return super.onMouseDrag(event, deltaX, deltaY)
    }

    override fun onMouseRelease(event: MouseButtonEvent): Boolean {
        if (rotatingPreview) {
            rotatingPreview = false
            return true
        }
        return super.onMouseRelease(event)
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

    override fun extractBackground(graphics: SpruceGuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        val borderColor = if (this.active) 0xDF000000.toInt() else 0x5F000000
        drawBorder(graphics.vanilla(), x, y, width, height, borderColor)
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
    override fun extractRenderState(graphics: SpruceGuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
    //?} else {
    /*override fun renderWidget(graphics: SpruceGuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
    *///?}
        if (confirmingDelete && !isMouseOverCard(mouseX, mouseY)) {
            cancelDeleteConfirmation()
        }
        //? if >=26.1 {
        super.extractRenderState(graphics, mouseX, mouseY, delta)
        //?} else {
        /*super.renderWidget(graphics, mouseX, mouseY, delta)
        *///?}
        updateSpringBack()

        val margin = client.font.lineHeight / 2
        val nameColor = if (this.active) 0xFFFFFFFF.toInt() else 0xFF808080.toInt()
        val textWidth = client.font.width(entry.displayName)
        val textX = x + (width - textWidth) / 2
        val textY = y + margin
        graphics.vanilla().enableScissor(x + margin, textY, x + width - margin, textY + client.font.lineHeight)
        graphics.vanilla().text(client.font, Component.nullToEmpty(entry.displayName), textX, textY, nameColor)
        graphics.vanilla().disableScissor()

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
                graphics.vanilla(), previewLeft, previewTop, previewRight, previewBottom,
                size, skinTextures, previewYaw, previewPitch
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
