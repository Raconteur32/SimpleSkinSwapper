package fr.raconteur.simpleskinswapper.gui.library

import com.mojang.blaze3d.platform.InputConstants
import fr.raconteur.simpleskinswapper.gui.SkinRenderer
import fr.raconteur.simpleskinswapper.gui.SkinType
import fr.raconteur.simpleskinswapper.gui.SkinUtils
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.ComponentPath
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.events.ContainerEventHandler
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.navigation.FocusNavigationEvent
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.core.ClientAsset
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.player.PlayerModelType
import net.minecraft.world.entity.player.PlayerSkin
import net.minecraft.util.Mth

/**
 * Shared skeleton of the two library overlays (detail + add): animated scale-up from a card
 * rect to a near-fullscreen panel, left controls column / right rotatable preview, the
 * wide/slim switch, click-away blur and the focus plumbing. Concrete panels build their own
 * rows ([repositionChildren], [drawContent]) and decide what toggling the switch means
 * ([currentSkinType], [toggleSkinType]) and what close/enter do ([onCloseRequested],
 * [onEnterPressed]).
 */
abstract class AbstractSkinOverlayPanel(
    protected val parent: SkinLibraryScreen
) : AbstractWidget(0, 0, 0, 0, Component.empty()), ContainerEventHandler, SkinOverlayPanel {

    protected val client: Minecraft = Minecraft.getInstance()

    private val panelChildren = ArrayList<GuiEventListener>()
    protected var focusedChild: GuiEventListener? = null
    private var dragging = false

    /** 0 = collapsed to the source card rect, 1 = fully open. */
    private var progress = 0f
    private var closing = false
    private var removePending = false
    private var lastAnimNanos = 0L

    private var rotatingPreview = false
    protected var previewYaw = REST_YAW
    protected var previewPitch = REST_PITCH
    private var lastSpringNanos = 0L

    private var sourceX = 0
    private var sourceY = 0
    private var sourceW = 0
    private var sourceH = 0

    // ------------------------------------------------------------------
    // Open / close
    // ------------------------------------------------------------------

    /** Starts the scale-up animation from the given card rect. */
    protected fun openFrom(x: Int, y: Int, w: Int, h: Int) {
        sourceX = x
        sourceY = y
        sourceW = w
        sourceH = h
        progress = 0f
        closing = false
        removePending = false
        lastAnimNanos = 0L
        lastSpringNanos = 0L
        previewYaw = REST_YAW
        previewPitch = REST_PITCH
        setX(0)
        setY(0)
        setWidth(parent.width)
        setHeight(parent.height)
    }

    fun close(instant: Boolean = false) {
        onCloseRequested(instant)
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

    override val isRemovePending: Boolean
        get() = removePending

    /** Keeps the widget bounds in sync when the window is resized while open. */
    override fun onScreenResized(width: Int, height: Int) {
        setWidth(width)
        setHeight(height)
    }

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
    protected fun targetRect(): IntArray = intArrayOf(
        DETAIL_MARGIN, DETAIL_MARGIN,
        parent.width - DETAIL_MARGIN * 2, parent.height - DETAIL_MARGIN * 2
    )

    /** Column width: ends SEPARATOR_GAP before the vertical separator line, so the
     *  controls never reach into the preview zone. */
    protected fun leftWidth(t: IntArray): Int = (t[2] * 0.45f).toInt() - SEPARATOR_GAP

    private fun splitX(t: IntArray): Int = t[0] + (t[2] * 0.45f).toInt()

    protected fun previewRect(t: IntArray): IntArray = intArrayOf(
        splitX(t) + SEPARATOR_GAP, t[1] + PANEL_PAD,
        t[0] + t[2] - PANEL_PAD, t[1] + t[3] - PANEL_PAD
    )

    protected fun switchRect(t: IntArray): IntArray = intArrayOf(
        // Indented so the flanking heads (head + gap on each side) stay inside the panel.
        t[0] + PANEL_PAD + HEAD + HEAD_GAP,
        switchRowY(),
        SWITCH_BODY_W, SWITCH_BODY_H
    )

    private fun isOnSwitch(mouseX: Int, mouseY: Int): Boolean {
        val s = switchRect(targetRect())
        return mouseX >= s[0] - HEAD_GAP - HEAD && mouseX < s[0] + s[2] + HEAD_GAP + HEAD &&
            mouseY >= s[1] - SWITCH_KNOB / 2 - 2 && mouseY < s[1] + s[3] + SWITCH_KNOB / 2 + 2
    }

    // ------------------------------------------------------------------
    // Ticking
    // ------------------------------------------------------------------

    /** Eases the preview back to its rest pose when released (same feel as the cards). */
    private fun updateSpringBack() {
        val now = System.nanoTime()
        val dt = if (lastSpringNanos == 0L) 0f else (now - lastSpringNanos) / 1_000_000_000f
        lastSpringNanos = now
        if (dt == 0f) return

        if (rotatingPreview || (previewYaw == REST_YAW && previewPitch == REST_PITCH)) return

        val t = 1.0F - Math.exp((-SPRING_RETURN_SPEED * dt).toDouble()).toFloat()
        previewYaw = Mth.lerp(t, previewYaw, REST_YAW)
        previewPitch = Mth.lerp(t, previewPitch, REST_PITCH)
        if (Math.abs(previewYaw - REST_YAW) < SPRING_SNAP_EPSILON) previewYaw = REST_YAW
        if (Math.abs(previewPitch - REST_PITCH) < SPRING_SNAP_EPSILON) previewPitch = REST_PITCH
    }

    private fun tickAnimation() {
        val now = System.nanoTime()
        val dt = if (lastAnimNanos == 0L) 0f else ((now - lastAnimNanos) / 1_000_000_000f).coerceAtMost(0.1f)
        lastAnimNanos = now
        if (dt == 0f) return
        progress = Mth.clamp(progress + (if (closing) -dt else dt) * OPEN_SPEED, 0f, 1f)
        // The screen unregisters the panel once it sees isRemovePending — this flag must
        // stay reachable until then.
        if (closing && progress <= 0f) removePending = true
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    private fun renderShell(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        tickAnimation()
        tickPanel()
        updateSpringBack()
        repositionChildren()

        val r = rect()
        // The big pseudo-card: same dark frame sprite as idle cards, nine-sliced to any size.
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SkinLibraryScreen.CARD_SPRITE_ACCESS, r[0], r[1], r[2], r[3])
        if (progress < 0.999f) return

        val t = targetRect()
        // Thin separator between the controls column and the preview.
        graphics.fill(splitX(t) - 1, t[1] + PANEL_PAD, splitX(t) + 1, t[1] + t[3] - PANEL_PAD, 0xFF202028.toInt())

        drawContent(graphics, t, mouseX, mouseY)

        for (child in panelChildren) {
            if (child !is AbstractWidget) continue
            //? if >=26.1 {
            child.extractRenderState(graphics, mouseX, mouseY, delta)
            //?} else {
            /*child.render(graphics, mouseX, mouseY, delta)
            *///?}
        }
    }

    /** Switch drawing shared by both panels; [skinType] decides where the knob rests. */
    protected fun drawSwitch(graphics: GuiGraphicsExtractor, t: IntArray, skinType: SkinType) {
        val s = switchRect(t)
        // Body: the darkened card frame sprite, a thin rectangle.
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SkinLibraryScreen.CARD_SPRITE_ACCESS, s[0], s[1], s[2], s[3])
        // Heads OUTSIDE the switch, one on each side, always visible: the option sits on
        // the side the knob must slide to (Steve = wide on the left, Alex = slim on the right).
        graphics.blit(RenderPipelines.GUI_TEXTURED, STEVE_TEXTURE, s[0] - HEAD_GAP - HEAD, s[1] + (s[3] - HEAD) / 2, 8f, 8f, HEAD, HEAD, 8, 8, 64, 64)
        graphics.blit(RenderPipelines.GUI_TEXTURED, ALEX_TEXTURE, s[0] + s[2] + HEAD_GAP, s[1] + (s[3] - HEAD) / 2, 8f, 8f, HEAD, HEAD, 8, 8, 64, 64)
        // Knob: the full-color square overlay, sliding toward the active side's head.
        val kx = if (skinType == SkinType.CLASSIC) s[0] - 4 else s[0] + s[2] - SWITCH_KNOB + 4
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SkinLibraryScreen.PANEL_SPRITE_ACCESS, kx, s[1] + (s[3] - SWITCH_KNOB) / 2, SWITCH_KNOB, SWITCH_KNOB)
    }

    /** Renders the preview the same way the cards do, with the drag rotate feel. */
    protected fun drawRotatablePreview(
        graphics: GuiGraphicsExtractor, p: IntArray, textureId: Identifier, slim: Boolean, mouseX: Int, mouseY: Int
    ) {
        // Same sizing convention as the cards: `size` is roughly the model's pixel height,
        // so half the rect height fills it without clipping head or feet.
        val size = ((p[3] - p[1]) * 0.5f).toInt()
        val skinTextures = PlayerSkin(
            ClientAsset.DownloadedTexture(textureId, ""), null, null,
            if (slim) PlayerModelType.SLIM else PlayerModelType.WIDE,
            true
        )
        val hovered = mouseX >= p[0] && mouseX < p[2] && mouseY >= p[1] && mouseY < p[3]
        SkinRenderer.renderPlayerRotatable(
            graphics, p, size, skinTextures,
            previewYaw, previewPitch, if (hovered) 0.35f else 0f
        )
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    final override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (!acceptsInput()) return false
        val mx = event.x().toInt()
        val my = event.y().toInt()
        val r = rect()
        if (!SkinUtils.inRect(mx, my, r[0], r[1], r[2], r[3])) {
            close()
            return true
        }
        for (child in panelChildren) {
            if (child.mouseClicked(event, doubleClick)) {
                // Blur-commit a pending rename from the other field, then focus the child.
                onChildFocused(focusedChild, child)
                setFocused(child)
                setDragging(true)
                return true
            }
        }
        onBackgroundClick(mx, my)
        // Click away from the fields: blur.
        setFocused(null)
        if (isOnSwitch(mx, my)) {
            toggleSkinType()
            return true
        }
        val p = previewRect(targetRect())
        if (SkinUtils.inRect(mx, my, p[0], p[1], p[2] - p[0], p[3] - p[1])) {
            rotatingPreview = true
            return true
        }
        return true
    }

    final override fun mouseDragged(event: MouseButtonEvent, deltaX: Double, deltaY: Double): Boolean {
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

    final override fun mouseReleased(event: MouseButtonEvent): Boolean {
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

    final override fun mouseScrolled(mouseX: Double, mouseY: Double, hozAmount: Double, vertAmount: Double): Boolean = true

    final override fun keyPressed(event: KeyEvent): Boolean {
        if (!acceptsInput()) return false
        when (event.key()) {
            InputConstants.KEY_ESCAPE -> {
                close()
                return true
            }
            InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> {
                onEnterPressed()
                return true
            }
        }
        return focusedChild?.keyPressed(event) ?: false
    }

    final override fun charTyped(event: CharacterEvent): Boolean =
        focusedChild?.charTyped(event) ?: false

    // ------------------------------------------------------------------
    // Container plumbing
    // ------------------------------------------------------------------

    protected fun addChild(listener: GuiEventListener) {
        panelChildren.add(listener)
    }

    final override fun children(): List<GuiEventListener> = panelChildren

    override fun isDragging(): Boolean = dragging

    override fun setDragging(dragging: Boolean) {
        this.dragging = dragging
    }

    final override fun getFocused(): GuiEventListener? = focusedChild

    /** Keeps the focused child's AbstractWidget flag in sync — EditBox gates all input on it. */
    final override fun setFocused(focused: GuiEventListener?) {
        val prev = focusedChild
        if (prev === focused) return
        (prev as? AbstractWidget)?.setFocused(false)
        focusedChild = focused
        (focused as? AbstractWidget)?.setFocused(true)
    }

    final override fun isFocused(): Boolean = super<AbstractWidget>.isFocused()

    final override fun setFocused(focused: Boolean) = super<AbstractWidget>.setFocused(focused)

    final override fun nextFocusPath(event: FocusNavigationEvent): ComponentPath? =
        super<AbstractWidget>.nextFocusPath(event)

    final override fun updateWidgetNarration(output: NarrationElementOutput) = defaultButtonNarrationText(output)

    //? if >=26.1 {
    final override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        renderShell(graphics, mouseX, mouseY, delta)
    }
    //?} else {
    /*final override fun renderWidget(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        renderShell(graphics, mouseX, mouseY, delta)
    }
    *///?}

    // ------------------------------------------------------------------
    // Panel-specific hooks
    // ------------------------------------------------------------------

    /** Whether the panel currently reacts to input at all (the detail panel needs an entry). */
    protected open fun acceptsInput(): Boolean = true

    /** Per-tick panel logic (field gating, flash timers) — runs before layout and drawing. */
    protected open fun tickPanel() {}

    /** Draws the left column labels/controls and the right preview, progress being 1. */
    protected abstract fun drawContent(graphics: GuiGraphicsExtractor, t: IntArray, mouseX: Int, mouseY: Int)

    /** Repositions this panel's children within the (static) target rect. */
    protected abstract fun repositionChildren()

    /** Y of the switch row inside the left column. */
    protected abstract fun switchRowY(): Int

    /** The skin type the switch currently reflects. */
    protected abstract fun currentSkinType(): SkinType

    /** What toggling the switch means for this panel. */
    protected abstract fun toggleSkinType()

    /** Called first when the panel closes; [instant] closes are programmatic. */
    protected open fun onCloseRequested(instant: Boolean) {}

    /** Called when a child consumed the click, before it takes focus. */
    protected open fun onChildFocused(previous: GuiEventListener?, child: GuiEventListener) {}

    /** Called on clicks that hit the panel but no child, while fields may still hold focus. */
    protected open fun onBackgroundClick(mouseX: Int, mouseY: Int) {}

    /** Enter/return inside the panel. */
    protected open fun onEnterPressed() {}

    protected companion object {
        private const val DETAIL_MARGIN = 24
        protected const val PANEL_PAD = 14
        protected const val LABEL_LINE = 10
        protected const val FIELD_HEIGHT = 14
        protected const val ROW_GAP = 10
        private const val SEPARATOR_GAP = 24

        // Switch: dark body (thin rectangle), full-color square knob sliding toward the
        // active side's head. The body is shorter than the knob and barely longer.
        private const val SWITCH_BODY_W = 32
        protected const val SWITCH_BODY_H = 14
        private const val SWITCH_KNOB = 20
        private const val HEAD = 10
        private const val HEAD_GAP = 8
        private const val DRAG_SENSITIVITY = 0.6f
        private const val OPEN_SPEED = 7f
        private const val REST_YAW = 25f
        private const val REST_PITCH = 0f
        private const val SPRING_RETURN_SPEED = 10.0F
        private const val SPRING_SNAP_EPSILON = 0.05F

        private val STEVE_TEXTURE = Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png")
        private val ALEX_TEXTURE = Identifier.withDefaultNamespace("textures/entity/player/slim/alex.png")
    }
}
