package fr.raconteur.simpleskinswapper.gui

import com.mojang.blaze3d.platform.InputConstants
import fr.raconteur.simpleskinswapper.SimpleSkinSwapperClient
import fr.raconteur.simpleskinswapper.overlayMessage
import fr.raconteur.simpleskinswapper.changeskin.SkinChange
import fr.raconteur.simpleskinswapper.changeskin.SkinSwapperState
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.core.ClientAsset
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvents
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.PlayerModelType
import net.minecraft.world.entity.player.PlayerSkin
import org.joml.Matrix3x2f

class SkinWheelScreen(private val parent: Screen?) : Screen(Component.empty()) {

    private val entries: List<SkinEntry> = loadWheelEntries()
    private var selectedIndex = -1
    private val hoverAnimFactors = FloatArray(entries.size)
    private var lastHoverAnimUpdateNanos = 0L

    override fun isPauseScreen(): Boolean = false

    //override fun renderBlurredBackground(context: GuiGraphicsExtractor) {
    //    // No blur — wheel is a transparent overlay
    //}

    override fun extractBackground(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        // No background — wheel is a transparent overlay
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    //? if >=26.1 {
    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
    //?} else {
    /*override fun render(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
    *///?}
        val cx = this.width / 2.0f
        val cy = this.height / 2.0f

        selectedIndex = getSelectedIndex(mouseX, mouseY, cx, cy)

        val n = entries.size
        if (n == 0) {
            context.centeredText(
                font,
                Component.translatable("simpleskinswapper.screen.carousel.no_skins"),
                cx.toInt(), cy.toInt(), COLOR_TEXT
            )
            //? if >=26.1 {
            //? if >=26.1 {
        super.extractRenderState(context, mouseX, mouseY, delta)
        //?} else {
        /*super.render(context, mouseX, mouseY, delta)
        *///?}
            //?} else {
            /*super.render(context, mouseX, mouseY, delta)
            *///?}
            return
        }

        // Draw pie sector backgrounds
        for (i in 0..<n) {
            drawSector(context, cx, cy, i, n, i == selectedIndex)
        }

        // Center fill circle (on top of sectors)
        fillCircle(context, cx, cy, 28f, COLOR_CENTER_BG)

        updateHoverAnimations()

        // Skin previews — painter's order: top (smallest py) first
        val sectorSize2 = 2 * Math.PI / n
        val angleOffset2 = -Math.PI / 2 - sectorSize2 / 2.0
        val previewDist2 = OUTER_RADIUS * 0.60
        val order = Array(n) { it }
        order.sortBy { i ->
            cy + previewDist2 * Math.sin(angleOffset2 + sectorSize2 * i + sectorSize2 / 2.0)
        }
        for (i in order) {
            drawSectorPreview(context, cx, cy, i, n)
        }

        // Selected skin name above the wheel
        if (selectedIndex >= 0) {
            context.centeredText(
                font,
                Component.nullToEmpty(entries[selectedIndex].displayName),
                cx.toInt(), (cy - OUTER_RADIUS).toInt() - font.lineHeight - 6, COLOR_TEXT
            )
        }

        //? if >=26.1 {
        super.extractRenderState(context, mouseX, mouseY, delta)
        //?} else {
        /*super.render(context, mouseX, mouseY, delta)
        *///?}
    }

    // -------------------------------------------------------------------------
    // Sector geometry
    // -------------------------------------------------------------------------

    private fun getSelectedIndex(mouseX: Int, mouseY: Int, cx: Float, cy: Float): Int {
        val dx = mouseX - cx
        val dy = mouseY - cy
        val dist = Math.sqrt((dx * dx + dy * dy).toDouble())
        if (dist < 10 || dist > OUTER_RADIUS * 1.1) return -1

        val n = entries.size
        val sectorSize = 2 * Math.PI / n
        val angleOffset = -Math.PI / 2 - sectorSize / 2.0

        val angle = Math.atan2(dy.toDouble(), dx.toDouble())
        val adjusted = ((angle - angleOffset) % (2 * Math.PI) + 2 * Math.PI) % (2 * Math.PI)
        val idx = (adjusted / sectorSize).toInt()
        return if (idx >= 0 && idx < n) idx else -1
    }

    private fun drawSector(context: GuiGraphicsExtractor, cx: Float, cy: Float, index: Int, n: Int, hovered: Boolean) {
        val sectorSize = 2 * Math.PI / n
        val angleOffset = -Math.PI / 2 - sectorSize / 2.0
        val baseAngle = angleOffset + sectorSize * index
        val startAngle = baseAngle + GAP_HALF_ANGLE
        val endAngle = baseAngle + sectorSize - GAP_HALF_ANGLE
        val color = if (hovered) COLOR_SECTOR_HOVER else COLOR_SECTOR
        submitSectorFill(context, cx, cy, OUTER_RADIUS, startAngle.toFloat(), endAngle.toFloat(), color)
    }

    private fun fillCircle(context: GuiGraphicsExtractor, cx: Float, cy: Float, radius: Float, color: Int) {
        submitSectorFill(context, cx, cy, radius, 0.0f, (2 * Math.PI).toFloat(), color)
    }

    /** Submits one sector as a single triangle-fan mesh — O(1) draw submissions per sector. */
    private fun submitSectorFill(
        context: GuiGraphicsExtractor, cx: Float, cy: Float, radius: Float,
        startAngle: Float, endAngle: Float, color: Int
    ) {
        context.guiRenderState.addGuiElement(
            SectorFillRenderState(
                Matrix3x2f(context.pose()), cx, cy, radius, startAngle, endAngle, color,
                context.scissorStack.peek()
            )
        )
    }

    /** Eases every sector's hover factor toward 1 (hovered) or 0 with an exponential settle. */
    private fun updateHoverAnimations() {
        val now = System.nanoTime()
        val dt = if (lastHoverAnimUpdateNanos == 0L) 0.0F else (now - lastHoverAnimUpdateNanos) / 1_000_000_000.0F
        lastHoverAnimUpdateNanos = now
        val t = 1.0F - Math.exp((-HOVER_ANIM_SPEED * dt).toDouble()).toFloat()
        for (i in hoverAnimFactors.indices) {
            val target = if (i == selectedIndex) 1.0F else 0.0F
            var eased = Mth.lerp(t, hoverAnimFactors[i], target)
            if (Math.abs(eased - target) < HOVER_ANIM_SNAP_EPSILON) eased = target
            hoverAnimFactors[i] = eased
        }
    }

    private fun drawSectorPreview(context: GuiGraphicsExtractor, cx: Float, cy: Float, index: Int, n: Int) {
        val sectorSize = 2 * Math.PI / n
        val angleOffset = -Math.PI / 2 - sectorSize / 2.0
        val midAngle = angleOffset + sectorSize * index + sectorSize / 2.0
        val previewDist = OUTER_RADIUS * 0.60

        val px = (cx + previewDist * Math.cos(midAngle)).toInt()
        val py = (cy + previewDist * Math.sin(midAngle)).toInt()

        val entry = entries[index]
        entry.ensureTextureLoaded()

        val halfW = 16
        val halfH = 24

        val textureId = entry.textureId ?: return

        // Every preview is a live entity render; only the hovered one plays the walk animation.
        SkinRenderer.renderPlayer(
            context, px - halfW, py - halfH, px + halfW, py + halfH, halfH,
            buildPlayerSkin(entry, textureId), hoverAnimFactors[index]
        )
    }

    private fun buildPlayerSkin(entry: SkinEntry, textureId: Identifier): PlayerSkin =
        PlayerSkin(
            ClientAsset.DownloadedTexture(textureId, ""), null, null,
            if (entry.skinType == SkinType.SLIM) PlayerModelType.SLIM else PlayerModelType.WIDE,
            true
        )

    // -------------------------------------------------------------------------
    // Input handling
    // -------------------------------------------------------------------------

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        // Button codes follow the platform: GLFW numbering (left=0) on <=26.2, SDL (left=1) on 26.3+.
        if (click.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            apply()
            return true
        }
        if (click.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            onClose()
            return true
        }
        return super.mouseClicked(click, doubled)
    }

    override fun keyReleased(input: KeyEvent): Boolean {
        if (SimpleSkinSwapperClient.openWheelKey?.matches(input) == true) {
            onClose()
            return true
        }
        return super.keyReleased(input)
    }

    private fun apply() {
        if (selectedIndex >= 0 && selectedIndex < entries.size) {
            val entry = entries[selectedIndex]
            if (SkinSwapperState.beginSwap()) {
                minecraft.player?.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 1.0f)
                SkinChange.changeSkin(
                    entry.file,
                    entry.skinType,
                    entry.textureId,
                    {
                        minecraft.player?.overlayMessage(
                            Component.translatable("simpleskinswapper.message.success")
                        )
                    },
                    { err ->
                        minecraft.player?.overlayMessage(
                            Component.translatable("simpleskinswapper.message.error", err)
                        )
                    }
                )
                minecraft.player?.overlayMessage(
                    Component.translatable("simpleskinswapper.message.applying")
                )
            }
        }
    }

    override fun onClose() {
        //? if >=26.2 {
        minecraft.gui.setScreen(parent)
        //?} else {
        /*minecraft.setScreen(parent)
        *///?}
    }

    companion object {
        private const val MAX_ENTRIES = 10
        private const val OUTER_RADIUS = 90.0f
        private val GAP_HALF_ANGLE = Math.toRadians(2.0).toFloat()

        // Hover animation easing: higher = faster settle back to the neutral pose.
        private const val HOVER_ANIM_SPEED = 10.0F
        private const val HOVER_ANIM_SNAP_EPSILON = 0.05F

        private val COLOR_SECTOR = 0xCC1A2535.toInt()
        private val COLOR_SECTOR_HOVER = 0xEE2B5F9E.toInt()
        private val COLOR_CENTER_BG = 0xBB0D1627.toInt()
        private val COLOR_TEXT = 0xFFFFFFFF.toInt()

        // -------------------------------------------------------------------------
        // Data loading — reads order.txt, no writes
        // -------------------------------------------------------------------------

        private fun loadWheelEntries(): List<SkinEntry> {
            val ordered = SkinCarouselScreen.loadOrderedEntries()
            if (ordered.size <= MAX_ENTRIES) return ordered
            return ArrayList(ordered.subList(0, MAX_ENTRIES))
        }
    }
}
