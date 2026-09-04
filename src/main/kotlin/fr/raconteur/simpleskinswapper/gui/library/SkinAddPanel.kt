package fr.raconteur.simpleskinswapper.gui.library

import com.mojang.blaze3d.platform.InputConstants
import fr.raconteur.simpleskinswapper.changeskin.AccountSkinFetcher
import fr.raconteur.simpleskinswapper.gui.EdgeSafeButtonWidget
import fr.raconteur.simpleskinswapper.gui.SkinRenderer
import fr.raconteur.simpleskinswapper.gui.SkinType
import fr.raconteur.simpleskinswapper.gui.SkinUtils
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
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.PlayerModelType
import net.minecraft.world.entity.player.PlayerSkin
import net.fabricmc.loader.api.FabricLoader
import java.io.File
import java.nio.file.Files

/**
 * Add-skin overlay, opened from the trailing "+" card. Same shell as the detail overlay:
 * bulk preview on the right, management controls on the left — but instead of delete and
 * apply, the column starts with the two "pick a skin" sources (file / MC name) and ends
 * with add + cancel. The confirm stays disabled until a skin has actually been staged.
 */
class SkinAddPanel(
    private val parent: SkinLibraryScreen
) : AbstractWidget(0, 0, 0, 0, Component.empty()), ContainerEventHandler, SkinOverlayPanel {

    private val client: Minecraft = Minecraft.getInstance()

    private val panelChildren = ArrayList<GuiEventListener>()
    private var focusedChild: GuiEventListener? = null
    private var dragging = false

    private val usernameField: EditBox
    private val fileNameField: EditBox
    private val displayNameField: EditBox
    private val fromFileButton: EdgeSafeButtonWidget
    private val fromMcNameButton: EdgeSafeButtonWidget
    private val confirmButton: EdgeSafeButtonWidget
    private val cancelButton: EdgeSafeButtonWidget

    private var stagedFile: File? = null
    private var stagedType: SkinType = SkinType.CLASSIC
    private var stagedTextureId: Identifier? = null
    private var fetching = false

    // Invalid-account flash on the username field (same pattern as the header import row).
    private var invalidRevertAtMs = 0L
    private var invalidSavedText = ""

    /** 0 = collapsed to the card rect, 1 = fully open. */
    private var progress = 0f
    private var closing = false
    private var removePending = false
    private var lastAnimNanos = 0L

    private var rotatingPreview = false
    private var previewYaw = 25f
    private var previewPitch = 0f
    private var lastSpringNanos = 0L

    init {
        fromFileButton = EdgeSafeButtonWidget(0, 0, 100, FIELD_HEIGHT + 2,
            Component.translatable("simpleskinswapper.screen.add.from_file")
        ) { parent.pickSkinFile { file -> stage(file, file.nameWithoutExtension) } }
        addChild(fromFileButton)

        usernameField = EditBox(
            client.font, 0, 0, 100, FIELD_HEIGHT,
            Component.translatable("simpleskinswapper.screen.add.username")
        )
        usernameField.setMaxLength(16)
        usernameField.setHint(Component.translatable("simpleskinswapper.screen.add.username"))
        usernameField.setResponder { text ->
            if (!fetching) fromMcNameButton.active = text.isNotBlank()
        }
        addChild(usernameField)

        fromMcNameButton = EdgeSafeButtonWidget(0, 0, 60, FIELD_HEIGHT + 2,
            Component.translatable("simpleskinswapper.screen.add.from_mcname")
        ) { fetchFromAccount() }
        fromMcNameButton.active = false
        addChild(fromMcNameButton)

        displayNameField = EditBox(
            client.font, 0, 0, 100, FIELD_HEIGHT,
            Component.translatable("simpleskinswapper.screen.detail.display_name")
        )
        displayNameField.setMaxLength(64)
        addChild(displayNameField)

        fileNameField = EditBox(
            client.font, 0, 0, 100, FIELD_HEIGHT,
            Component.translatable("simpleskinswapper.screen.detail.file_name")
        )
        fileNameField.setMaxLength(64)
        addChild(fileNameField)

        confirmButton = EdgeSafeButtonWidget(0, 0, 60, FIELD_HEIGHT + 2,
            Component.translatable("simpleskinswapper.screen.add.confirm")
        ) { confirmAdd() }
        confirmButton.active = false
        addChild(confirmButton)

        cancelButton = EdgeSafeButtonWidget(0, 0, 60, FIELD_HEIGHT + 2,
            CommonComponents.GUI_CANCEL
        ) { close() }
        addChild(cancelButton)
    }

    // ------------------------------------------------------------------
    // Open / close
    // ------------------------------------------------------------------

    /** Starts the scale-up animation from [card]'s current rect to the full add rect. */
    fun open(card: SkinAddCard) {
        sourceX = card.x
        sourceY = card.y
        sourceW = card.width
        sourceH = card.height
        progress = 0f
        closing = false
        removePending = false
        lastAnimNanos = 0L
        lastSpringNanos = 0L

        cleanupStaging()
        usernameField.setValue("")
        displayNameField.setValue("")
        fileNameField.setValue("")
        stagedType = SkinType.CLASSIC
        stagedTextureId = null
        previewYaw = REST_YAW
        previewPitch = REST_PITCH
        fetching = false
        invalidRevertAtMs = 0L
        usernameField.setTextColor(0xFFE0E0E0.toInt())
        fromMcNameButton.active = false
        confirmButton.active = false

        setX(0)
        setY(0)
        setWidth(parent.width)
        setHeight(parent.height)
    }

    fun close(instant: Boolean = false) {
        setFocused(null)
        rotatingPreview = false
        cleanupStaging()
        if (instant) {
            removePending = true
            return
        }
        if (!closing) {
            closing = true
            lastAnimNanos = 0L
        }
    }

    /** Keeps the widget bounds in sync when the window is resized while open. */
    override fun onScreenResized(width: Int, height: Int) {
        setWidth(width)
        setHeight(height)
    }

    override val isRemovePending: Boolean
        get() = removePending

    // ------------------------------------------------------------------
    // Staging
    // ------------------------------------------------------------------

    private fun stagingFile(): File =
        FabricLoader.getInstance().gameDir.resolve(".simpleskinswapper-add-staging.png").toFile()

    /** Drops the staged skin (the staging file is temporary by design). */
    private fun cleanupStaging() {
        stagedFile?.delete()
        stagedFile = null
        stagedTextureId = null
    }

    /** Stages a skin for the preview: auto-detects the model and suggests a file name. */
    private fun stage(file: File, suggestedName: String) {
        stagedFile = file
        stagedType = SkinUtils.detectSkinType(file)
        fileNameField.setValue(suggestedName)
        stagedTextureId = null
        SkinUtils.loadSkinTextureAsync(file, "skin/add_staging") { id -> stagedTextureId = id }
    }

    private fun fetchFromAccount() {
        val username = usernameField.value.trim()
        if (username.isEmpty() || fetching) return
        fetching = true
        fromMcNameButton.active = false
        AccountSkinFetcher.fetch(
            username, stagingFile().toPath(),
            { file ->
                fetching = false
                fromMcNameButton.active = usernameField.value.isNotBlank()
                stage(file, AccountSkinFetcher.sanitizeFilename(username))
            },
            {
                fetching = false
                fromMcNameButton.active = usernameField.value.isNotBlank()
                flashInvalidAccount(username)
            }
        )
    }

    private fun flashInvalidAccount(previousText: String) {
        invalidSavedText = previousText
        usernameField.setTextColor(0xFFFF5555.toInt())
        usernameField.setValue(Component.translatable("simpleskinswapper.screen.carousel.invalid_account").string)
        invalidRevertAtMs = System.currentTimeMillis() + 1500L
    }

    private fun tickInvalidFlash() {
        if (invalidRevertAtMs != 0L && System.currentTimeMillis() >= invalidRevertAtMs) {
            invalidRevertAtMs = 0L
            usernameField.setTextColor(0xFFE0E0E0.toInt())
            usernameField.setValue(invalidSavedText)
        }
    }

    /** A skin is staged, the file name is valid and the target does not exist yet. */
    private fun canConfirm(): Boolean {
        val name = sanitize(fileNameField.value)
        if (stagedFile == null || name.isEmpty()) return false
        val target = FabricLoader.getInstance().gameDir.resolve("skins").resolve("$name.png")
        return !Files.exists(target)
    }

    private fun sanitize(value: String): String =
        value.replace(Regex("[^A-Za-z0-9_\\- ]"), "_").trim()

    private fun confirmAdd() {
        val file = stagedFile ?: return
        val name = sanitize(fileNameField.value)
        if (name.isEmpty()) return
        if (parent.confirmAddSkin(file, name, displayNameField.value.trim(), stagedType)) {
            // Ownership of the staging file transferred to the skins folder.
            stagedFile = null
            close(instant = true)
        }
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    private var sourceX = 0
    private var sourceY = 0
    private var sourceW = 0
    private var sourceH = 0

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

    /** Column width: ends SEPARATOR_GAP before the vertical separator line, so the
     *  controls never reach into the preview zone (same math as the detail panel). */
    private fun leftWidth(t: IntArray): Int = (t[2] * 0.45f).toInt() - SEPARATOR_GAP

    private fun splitX(t: IntArray): Int = t[0] + (t[2] * 0.45f).toInt()

    private fun previewRect(t: IntArray): IntArray = intArrayOf(
        splitX(t) + SEPARATOR_GAP, t[1] + PANEL_PAD,
        t[0] + t[2] - PANEL_PAD, t[1] + t[3] - PANEL_PAD
    )

    /** Explicit left-column rows: two button rows, then label+field pairs, switch, buttons.
     *  Every gap is the same ROW_GAP so the column keeps one vertical rhythm. */
    private fun btnRowY(row: Int): Int = targetRect()[1] + PANEL_PAD + row * (FIELD_HEIGHT + 2 + ROW_GAP)

    private fun dispLabelY(): Int = btnRowY(1) + FIELD_HEIGHT + 2 + ROW_GAP

    private fun dispFieldY(): Int = dispLabelY() + LABEL_LINE

    private fun fileLabelY(): Int = dispFieldY() + FIELD_HEIGHT + ROW_GAP

    private fun fileFieldY(): Int = fileLabelY() + LABEL_LINE

    private fun switchRowY(): Int = fileFieldY() + FIELD_HEIGHT + ROW_GAP

    private fun switchRect(t: IntArray): IntArray = intArrayOf(
        // Indented so the flanking heads (head + gap on each side) stay inside the panel.
        t[0] + PANEL_PAD + HEAD + HEAD_GAP,
        switchRowY(),
        SWITCH_BODY_W, SWITCH_BODY_H
    )

    private fun repositionChildren() {
        val t = targetRect()
        val leftW = leftWidth(t)

        fromFileButton.setWidth(leftW)
        fromFileButton.setPosition(t[0] + PANEL_PAD, btnRowY(0))

        // Compact source button (label-sized) so the username field keeps the room.
        val searchW = client.font.width(fromMcNameButton.message) + 12
        val usernameW = leftW - searchW - 8
        usernameField.setWidth(usernameW)
        // The field is 2px shorter than the button: nudge it down so their centerlines align.
        usernameField.setPosition(t[0] + PANEL_PAD, btnRowY(1) + (FIELD_HEIGHT + 2 - FIELD_HEIGHT) / 2)
        fromMcNameButton.setWidth(searchW)
        fromMcNameButton.setPosition(t[0] + PANEL_PAD + usernameW + 8, btnRowY(1))

        displayNameField.setWidth(leftW)
        displayNameField.setPosition(t[0] + PANEL_PAD, dispFieldY())
        fileNameField.setWidth(leftW)
        fileNameField.setPosition(t[0] + PANEL_PAD, fileFieldY())

        // Confirm/cancel split the column in two so cancel never overflows past it.
        val buttonY = switchRowY() + SWITCH_BODY_H + ROW_GAP
        val halfW = (leftW - 8) / 2
        confirmButton.setWidth(halfW)
        confirmButton.setPosition(t[0] + PANEL_PAD, buttonY)
        cancelButton.setWidth(halfW)
        cancelButton.setPosition(t[0] + PANEL_PAD + halfW + 8, buttonY)
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

    //? if >=26.1 {
    override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
    //?} else {
    /*override fun renderWidget(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
    *///?}
        tickAnimation()
        tickInvalidFlash()
        updateSpringBack()
        repositionChildren()
        confirmButton.active = canConfirm()

        val r = rect()
        // The big pseudo-card: same dark frame sprite as idle cards, nine-sliced to any size.
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SkinLibraryScreen.CARD_SPRITE_ACCESS, r[0], r[1], r[2], r[3])
        if (progress < 0.999f) return

        val t = targetRect()
        // Thin separator between the controls column and the preview.
        graphics.fill(splitX(t) - 1, t[1] + PANEL_PAD, splitX(t) + 1, t[1] + t[3] - PANEL_PAD, 0xFF202028.toInt())

        drawPreview(graphics, t, mouseX, mouseY)
        drawLabels(graphics, t)
        drawSwitch(graphics, t)

        for (child in panelChildren) {
            if (child !is AbstractWidget) continue
            //? if >=26.1 {
            child.extractRenderState(graphics, mouseX, mouseY, delta)
            //?} else {
            /*child.render(graphics, mouseX, mouseY, delta)
            *///?}
        }
    }

    private fun drawPreview(graphics: GuiGraphicsExtractor, t: IntArray, mouseX: Int, mouseY: Int) {
        val p = previewRect(t)
        val textureId = stagedTextureId
        if (textureId == null) {
            graphics.centeredText(
                client.font, Component.translatable("simpleskinswapper.screen.add.none"),
                (p[0] + p[2]) / 2, (p[1] + p[3]) / 2 - client.font.lineHeight / 2, 0xFFAAAAAA.toInt()
            )
            return
        }
        val size = ((p[3] - p[1]) * 0.5f).toInt()
        val skinTextures = PlayerSkin(
            ClientAsset.DownloadedTexture(textureId, ""), null, null,
            if (stagedType == SkinType.SLIM) PlayerModelType.SLIM else PlayerModelType.WIDE,
            true
        )
        val hovered = mouseX >= p[0] && mouseX < p[2] && mouseY >= p[1] && mouseY < p[3]
        SkinRenderer.renderPlayerRotatable(
            graphics, p[0], p[1], p[2], p[3], size, skinTextures,
            previewYaw, previewPitch, if (hovered) 0.35f else 0f
        )
    }

    private fun drawLabels(graphics: GuiGraphicsExtractor, t: IntArray) {
        val x = t[0] + PANEL_PAD
        graphics.text(client.font, Component.translatable("simpleskinswapper.screen.detail.display_name"), x, dispLabelY(), 0xFFB0B8C0.toInt())
        graphics.text(client.font, Component.translatable("simpleskinswapper.screen.detail.file_name"), x, fileLabelY(), 0xFFB0B8C0.toInt())
    }

    private fun drawSwitch(graphics: GuiGraphicsExtractor, t: IntArray) {
        val s = switchRect(t)
        // Body: the darkened card frame sprite, a thin rectangle.
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SkinLibraryScreen.CARD_SPRITE_ACCESS, s[0], s[1], s[2], s[3])
        // Heads OUTSIDE the switch, one on each side, always visible: the option sits on
        // the side the knob must slide to (Steve = wide on the left, Alex = slim on the right).
        graphics.blit(RenderPipelines.GUI_TEXTURED, STEVE_TEXTURE, s[0] - HEAD_GAP - HEAD, s[1] + (s[3] - HEAD) / 2, 8f, 8f, HEAD, HEAD, 8, 8, 64, 64)
        graphics.blit(RenderPipelines.GUI_TEXTURED, ALEX_TEXTURE, s[0] + s[2] + HEAD_GAP, s[1] + (s[3] - HEAD) / 2, 8f, 8f, HEAD, HEAD, 8, 8, 64, 64)
        // Knob: the full-color square overlay, sliding toward the active side's head.
        val kx = if (stagedType == SkinType.CLASSIC) s[0] - 4 else s[0] + s[2] - SWITCH_KNOB + 4
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SkinLibraryScreen.PANEL_SPRITE_ACCESS, kx, s[1] + (s[3] - SWITCH_KNOB) / 2, SWITCH_KNOB, SWITCH_KNOB)
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    private fun isOnSwitch(mouseX: Int, mouseY: Int): Boolean {
        val s = switchRect(targetRect())
        return mouseX >= s[0] - HEAD_GAP - HEAD && mouseX < s[0] + s[2] + HEAD_GAP + HEAD &&
            mouseY >= s[1] - SWITCH_KNOB / 2 - 2 && mouseY < s[1] + s[3] + SWITCH_KNOB / 2 + 2
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val mx = event.x().toInt()
        val my = event.y().toInt()
        val r = rect()
        if (!SkinUtils.inRect(mx, my, r[0], r[1], r[2], r[3])) {
            close()
            return true
        }
        for (child in panelChildren) {
            if (child.mouseClicked(event, doubleClick)) {
                setFocused(child)
                setDragging(true)
                return true
            }
        }
        // Click away from the fields: blur.
        setFocused(null)
        if (isOnSwitch(mx, my)) {
            stagedType = if (stagedType == SkinType.CLASSIC) SkinType.SLIM else SkinType.CLASSIC
            return true
        }
        val p = previewRect(targetRect())
        if (SkinUtils.inRect(mx, my, p[0], p[1], p[2] - p[0], p[3] - p[1])) {
            rotatingPreview = true
            return true
        }
        return true
    }

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
        when (event.key()) {
            InputConstants.KEY_ESCAPE -> {
                close()
                return true
            }
            InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> {
                if (canConfirm()) {
                    confirmAdd()
                } else if (focusedChild === usernameField) {
                    fetchFromAccount()
                }
                return true
            }
        }
        return focusedChild?.keyPressed(event) ?: false
    }

    override fun charTyped(event: CharacterEvent): Boolean =
        focusedChild?.charTyped(event) ?: false

    // ------------------------------------------------------------------
    // Container plumbing (mirrors SkinDetailPanel)
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

        // Switch: dark body (thin rectangle), full-color square knob sliding toward the
        // active side's head. The body is shorter than the knob and barely longer.
        private const val SWITCH_BODY_W = 32
        private const val SWITCH_BODY_H = 14
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
