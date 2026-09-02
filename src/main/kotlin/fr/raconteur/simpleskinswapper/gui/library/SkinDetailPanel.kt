package fr.raconteur.simpleskinswapper.gui.library

import com.mojang.blaze3d.platform.InputConstants
import fr.raconteur.simpleskinswapper.gui.EdgeSafeButtonWidget
import fr.raconteur.simpleskinswapper.gui.SkinEntry
import fr.raconteur.simpleskinswapper.gui.SkinNameStore
import fr.raconteur.simpleskinswapper.gui.SkinRenderer
import fr.raconteur.simpleskinswapper.gui.SkinType
import fr.raconteur.simpleskinswapper.gui.SkinTypeStore
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.ComponentPath
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.events.ContainerEventHandler
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.navigation.FocusNavigationEvent
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.core.ClientAsset
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.PlayerModelType
import net.minecraft.world.entity.player.PlayerSkin

/**
 * Full-screen detail overlay for one skin. Opens as an animated scale-up of the clicked
 * card into a large rectangle (the base screen stays visible around it). Right side: the
 * skin preview in bulk, drag to rotate. Left side: file-name rename, display name, a
 * wide/slim switch built from the card sprites (dark thin body, full-color square knob
 * sliding over the active side's head: Steve left, Alex right) and a two-step delete.
 */
class SkinDetailPanel(
    private val parent: SkinLibraryScreen
) : AbstractWidget(0, 0, 0, 0, Component.empty()), ContainerEventHandler {

    private val client: Minecraft = Minecraft.getInstance()

    private val panelChildren = ArrayList<GuiEventListener>()
    private var focusedChild: GuiEventListener? = null
    private var dragging = false

    private val fileNameField: EditBox
    private val displayNameField: EditBox
    private val deleteButton: EdgeSafeButtonWidget
    private var deleteArmed = false

    private var entry: SkinEntry? = null
    private var sourceX = 0
    private var sourceY = 0
    private var sourceW = 0
    private var sourceH = 0

    /** 0 = collapsed to the card rect, 1 = fully open. */
    private var progress = 0f
    private var closing = false
    private var removePending = false
    private var lastAnimNanos = 0L

    private var rotatingPreview = false
    private var previewYaw = 25f
    private var previewPitch = 0f

    init {
        fileNameField = EditBox(
            client.font, 0, 0, 100, FIELD_HEIGHT,
            Component.translatable("simpleskinswapper.screen.detail.file_name")
        )
        fileNameField.setMaxLength(64)
        addChild(fileNameField)

        displayNameField = EditBox(
            client.font, 0, 0, 100, FIELD_HEIGHT,
            Component.translatable("simpleskinswapper.screen.detail.display_name")
        )
        displayNameField.setMaxLength(64)
        // Live update: a blank value clears the override so the file name shows again.
        displayNameField.setResponder { text ->
            val e = entry ?: return@setResponder
            e.displayNameOverride = text.trim().ifEmpty { null }
            SkinNameStore.setName(e.file.name, text.trim())
        }
        addChild(displayNameField)

        deleteButton = EdgeSafeButtonWidget(0, 0, DELETE_W, FIELD_HEIGHT + 2, deleteLabel()) {
            onDeleteClicked()
        }
        addChild(deleteButton)
    }

    // ------------------------------------------------------------------
    // Open / close / rebind
    // ------------------------------------------------------------------

    /** Starts the scale-up animation from [card]'s current rect to the full detail rect. */
    fun open(card: SkinLibraryCard) {
        entry = card.entry
        sourceX = card.x
        sourceY = card.y
        sourceW = card.width
        sourceH = card.height
        progress = 0f
        closing = false
        removePending = false
        lastAnimNanos = 0L
        deleteArmed = false
        deleteButton.message = deleteLabel()

        val e = entry!!
        fileNameField.setValue(e.baseName)
        displayNameField.setValue(e.displayNameOverride ?: "")
        previewYaw = 25f
        previewPitch = 0f

        setX(0)
        setY(0)
        setWidth(parent.width)
        setHeight(parent.height)
    }

    fun close(instant: Boolean = false) {
        setFocused(null)
        rotatingPreview = false
        if (instant) {
            removePending = true
            return
        }
        if (!closing) {
            closing = true
            lastAnimNanos = 0L
        }
    }

    /** Re-points the panel at a fresh entry after a reload (file renamed / list rebuilt). */
    fun rebind(fresh: SkinEntry) {
        entry = fresh
        fileNameField.setValue(fresh.baseName)
        displayNameField.setValue(fresh.displayNameOverride ?: "")
    }

    val entryFileName: String?
        get() = entry?.file?.name

    val isRemovePending: Boolean
        get() = removePending

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    /** Current animated panel rect (x, y, w, h). */
    private fun rect(): IntArray {
        val t = targetRect()
        val x = Mth.lerp(progress, sourceX.toFloat(), t[0].toFloat())
        val y = Mth.lerp(progress, sourceY.toFloat(), t[1].toFloat())
        val w = Mth.lerp(progress, sourceW.toFloat(), t[2].toFloat())
        val h = Mth.lerp(progress, sourceH.toFloat(), t[3].toFloat())
        return intArrayOf(x.toInt(), y.toInt(), w.toInt(), h.toInt())
    }

    /** Nearly the whole screen; the base screen stays visible around it. */
    private fun targetRect(): IntArray = intArrayOf(
        DETAIL_MARGIN, DETAIL_MARGIN,
        parent.width - DETAIL_MARGIN * 2, parent.height - DETAIL_MARGIN * 2
    )

    private fun leftWidth(t: IntArray): Int = (t[2] * 0.45f).toInt() - PANEL_PAD - SEPARATOR_GAP + PANEL_PAD

    private fun splitX(t: IntArray): Int = t[0] + (t[2] * 0.45f).toInt()

    private fun previewRect(t: IntArray): IntArray = intArrayOf(
        splitX(t) + SEPARATOR_GAP, t[1] + PANEL_PAD,
        t[0] + t[2] - PANEL_PAD, t[1] + t[3] - PANEL_PAD
    )

    private fun labelY(row: Int): Int = targetRect()[1] + PANEL_PAD + row * (LABEL_LINE + FIELD_HEIGHT + ROW_GAP)

    private fun switchRect(t: IntArray): IntArray = intArrayOf(
        t[0] + PANEL_PAD,
        labelY(2) + (SWITCH_ZONE - SWITCH_BODY_H) / 2,
        SWITCH_BODY_W, SWITCH_BODY_H
    )

    private fun repositionChildren() {
        val t = targetRect()
        val leftW = leftWidth(t)
        fileNameField.setWidth(leftW)
        displayNameField.setWidth(leftW)
        fileNameField.setPosition(t[0] + PANEL_PAD, labelY(0) + LABEL_LINE)
        displayNameField.setPosition(t[0] + PANEL_PAD, labelY(1) + LABEL_LINE)
        deleteButton.setPosition(t[0] + PANEL_PAD, labelY(2) + SWITCH_ZONE)
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    private fun isOnSwitch(mouseX: Int, mouseY: Int): Boolean {
        val s = switchRect(targetRect())
        return mouseX >= s[0] - SWITCH_KNOB && mouseX < s[0] + s[2] + SWITCH_KNOB &&
            mouseY >= s[1] - SWITCH_KNOB / 2 - 2 && mouseY < s[1] + s[3] + SWITCH_KNOB / 2 + 2
    }

    /** Knob x for the current type: over Steve (left) when classic, over Alex (right) when slim. */
    private fun knobX(e: SkinEntry, s: IntArray): Int =
        if (e.skinType == SkinType.CLASSIC) s[0] - 4
        else s[0] + s[2] - SWITCH_KNOB + 4

    private fun toggleType() {
        val e = entry ?: return
        e.skinType = if (e.skinType == SkinType.CLASSIC) SkinType.SLIM else SkinType.CLASSIC
        SkinTypeStore.setType(e.file.name, e.skinType)
    }

    private fun onDeleteClicked() {
        val e = entry ?: return
        if (!deleteArmed) {
            deleteArmed = true
            deleteButton.message = Component.translatable("simpleskinswapper.screen.detail.delete_confirm")
            return
        }
        deleteArmed = false
        close(instant = true)
        parent.deleteEntry(e)
    }

    private fun deleteLabel(): Component =
        Component.translatable("simpleskinswapper.screen.detail.delete")

    private fun commitRename() {
        val e = entry ?: return
        val value = fileNameField.value.trim()
        if (value.isEmpty() || value == e.baseName) {
            fileNameField.setValue(e.baseName)
            return
        }
        if (parent.renameEntry(e, value)) {
            fileNameField.setValue(e.file.nameWithoutExtension)
        } else {
            fileNameField.setValue(e.baseName)
        }
    }

    private fun disarmDelete() {
        if (deleteArmed) {
            deleteArmed = false
            deleteButton.message = deleteLabel()
        }
    }

    // ------------------------------------------------------------------
    // Ticking
    // ------------------------------------------------------------------

    private fun tickAnimation() {
        val now = System.nanoTime()
        val dt = if (lastAnimNanos == 0L) 0f else ((now - lastAnimNanos) / 1_000_000_000f).coerceAtMost(0.1f)
        lastAnimNanos = now
        if (dt == 0f) return
        progress = Mth.clamp(progress + (if (closing) -dt else dt) * OPEN_SPEED, 0f, 1f)
        // The screen unregisters the panel (detail = null + removeWidget) once it sees
        // isRemovePending — this field must stay reachable until then.
        if (closing && progress <= 0f) removePending = true
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    //? if >=26.1 {
    override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
    //?} else {
    /*override fun renderWidget(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
    *///?}
        val e = entry ?: return
        tickAnimation()
        repositionChildren()
        val r = rect()
        // The big card: same dark frame sprite as idle cards, nine-sliced to any size.
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SkinLibraryScreen.CARD_SPRITE_ACCESS, r[0], r[1], r[2], r[3])
        if (progress < 0.999f) return

        val t = targetRect()
        // Thin separator between the controls column and the preview.
        graphics.fill(splitX(t) - 1, t[1] + PANEL_PAD, splitX(t) + 1, t[1] + t[3] - PANEL_PAD, 0xFF202028.toInt())

        drawPreview(graphics, e, t, mouseX, mouseY)
        drawLabels(graphics, t)
        drawSwitch(graphics, e, t)

        for (child in panelChildren) {
            if (child !is AbstractWidget) continue
            //? if >=26.1 {
            child.extractRenderState(graphics, mouseX, mouseY, delta)
            //?} else {
            /*child.render(graphics, mouseX, mouseY, delta)
            *///?}
        }
    }

    private fun drawPreview(graphics: GuiGraphicsExtractor, e: SkinEntry, t: IntArray, mouseX: Int, mouseY: Int) {
        val p = previewRect(t)
        e.ensureTextureLoaded()
        val textureId = e.textureId ?: return
        val ph = p[3] - p[1]
        val size = (ph * 0.8f).toInt()
        val skinTextures = PlayerSkin(
            ClientAsset.DownloadedTexture(textureId, ""), null, null,
            if (e.skinType == SkinType.SLIM) PlayerModelType.SLIM else PlayerModelType.WIDE,
            true
        )
        val hovered = mouseX >= p[0] && mouseX < p[2] && mouseY >= p[1] && mouseY < p[3]
        SkinRenderer.renderPlayerRotatable(
            graphics, p[0], p[1], p[2], p[3], size, skinTextures,
            previewYaw, previewPitch, if (hovered && !rotatingPreview) 0.35f else 0f
        )
    }

    private fun drawLabels(graphics: GuiGraphicsExtractor, t: IntArray) {
        val x = t[0] + PANEL_PAD
        graphics.text(client.font, Component.translatable("simpleskinswapper.screen.detail.file_name"), x, labelY(0), 0xFFB0B8C0.toInt())
        graphics.text(client.font, Component.translatable("simpleskinswapper.screen.detail.display_name"), x, labelY(1), 0xFFB0B8C0.toInt())
    }

    private fun drawSwitch(graphics: GuiGraphicsExtractor, e: SkinEntry, t: IntArray) {
        val s = switchRect(t)
        // Body: the darkened card frame sprite, a thin rectangle.
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SkinLibraryScreen.CARD_SPRITE_ACCESS, s[0], s[1], s[2], s[3])
        // Heads on the exposed ends: Steve (wide) left, Alex (slim) right.
        graphics.blit(RenderPipelines.GUI_TEXTURED, STEVE_TEXTURE, s[0] + 5, s[1] + (s[3] - HEAD) / 2, 8f, 8f, HEAD, HEAD, 8, 8, 64, 64)
        graphics.blit(RenderPipelines.GUI_TEXTURED, ALEX_TEXTURE, s[0] + s[2] - 5 - HEAD, s[1] + (s[3] - HEAD) / 2, 8f, 8f, HEAD, HEAD, 8, 8, 64, 64)
        // Knob: the full-color square overlay, sliding over the active side.
        val kx = knobX(e, s)
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SkinLibraryScreen.PANEL_SPRITE_ACCESS, kx, s[1] + (s[3] - SWITCH_KNOB) / 2, SWITCH_KNOB, SWITCH_KNOB)
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (entry == null) return false
        val mx = event.x().toInt()
        val my = event.y().toInt()
        val r = rect()
        if (mx < r[0] || mx >= r[0] + r[2] || my < r[1] || my >= r[1] + r[3]) {
            close()
            return true
        }
        for (child in panelChildren) {
            if (child.mouseClicked(event, doubleClick)) {
                // Blur-commit a pending rename from the other field, then focus the child.
                // setFocused also flips the widget's own focused flag — EditBox only
                // consumes keyboard input when it is set (vanilla Screen.setFocused is
                // bypassed by the screen's manual forwarding).
                if (focusedChild != null && focusedChild !== child) commitRename()
                setFocused(child)
                setDragging(true)
                return true
            }
        }
        // Clicking anywhere but the delete button disarms the pending confirmation.
        if (!isOnDelete(mx, my)) disarmDelete()
        // Click away from the fields: blur + commit.
        if (focusedChild != null) commitRename()
        setFocused(null)
        if (isOnSwitch(mx, my)) {
            toggleType()
            return true
        }
        val p = previewRect(targetRect())
        if (mx >= p[0] && mx < p[2] && my >= p[1] && my < p[3]) {
            rotatingPreview = true
            return true
        }
        return true
    }

    private fun isOnDelete(mx: Int, my: Int): Boolean =
        mx >= deleteButton.x && mx < deleteButton.x + deleteButton.width &&
            my >= deleteButton.y && my < deleteButton.y + deleteButton.height

    override fun mouseDragged(event: MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean {
        if (rotatingPreview && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            previewYaw = Mth.wrapDegrees((previewYaw - deltaX.toFloat() * DRAG_SENSITIVITY).toDouble()).toFloat()
            previewPitch = Mth.clamp(previewPitch - deltaY.toFloat() * DRAG_SENSITIVITY, -40f, 40f)
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
            return true
        }
        if (dragging) {
            dragging = false
        }
        var handled = false
        for (child in panelChildren) {
            if (child.mouseReleased(event)) handled = true
        }
        return handled
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, hozAmount: Double, vertAmount: Double): Boolean = true

    override fun keyPressed(event: KeyEvent): Boolean {
        if (entry == null) return false
        when (event.key()) {
            InputConstants.KEY_ESCAPE -> {
                close()
                return true
            }
            InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> {
                commitRename()
                return true
            }
        }
        return focusedChild?.keyPressed(event) ?: false
    }

    override fun charTyped(event: CharacterEvent): Boolean =
        focusedChild?.charTyped(event) ?: false

    // ------------------------------------------------------------------
    // Container plumbing (mirrors SkinLibraryCard)
    // ------------------------------------------------------------------

    private fun addChild(listener: GuiEventListener) {
        panelChildren.add(listener)
    }

    override fun children(): List<GuiEventListener> = panelChildren

    override fun isDragging(): Boolean = dragging

    override fun setDragging(dragging: Boolean) {
        this.dragging = dragging
    }

    override fun getFocused(): GuiEventListener? = focusedChild

    /** Keeps the focused child's AbstractWidget flag in sync — EditBox gates all input on it. */
    override fun setFocused(focused: GuiEventListener?) {
        val prev = focusedChild
        if (prev === focused) return
        (prev as? AbstractWidget)?.setFocused(false)
        focusedChild = focused
        (focused as? AbstractWidget)?.setFocused(true)
    }

    override fun isFocused(): Boolean = super<AbstractWidget>.isFocused()

    override fun setFocused(focused: Boolean) = super<AbstractWidget>.setFocused(focused)

    override fun nextFocusPath(event: FocusNavigationEvent): ComponentPath? =
        super<AbstractWidget>.nextFocusPath(event)

    override fun updateWidgetNarration(output: NarrationElementOutput) = defaultButtonNarrationText(output)

    companion object {
        private const val DETAIL_MARGIN = 24
        private const val PANEL_PAD = 14
        private const val LABEL_LINE = 10
        private const val FIELD_HEIGHT = 14
        private const val ROW_GAP = 10
        private const val SEPARATOR_GAP = 24

        // Switch: dark body (thin rectangle), full-color square knob sliding over the
        // active side's head. The body is shorter than the knob and barely longer.
        private const val SWITCH_BODY_W = 32
        private const val SWITCH_BODY_H = 14
        private const val SWITCH_KNOB = 20
        private const val SWITCH_ZONE = SWITCH_KNOB + 4
        private const val HEAD = 10
        private const val DRAG_SENSITIVITY = 0.6f
        private const val OPEN_SPEED = 7f

        private const val DELETE_W = 70

        private val STEVE_TEXTURE = Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png")
        private val ALEX_TEXTURE = Identifier.withDefaultNamespace("textures/entity/player/slim/alex.png")
    }
}
