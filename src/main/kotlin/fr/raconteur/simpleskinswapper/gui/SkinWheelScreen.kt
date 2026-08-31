package fr.raconteur.simpleskinswapper.gui

import com.mojang.blaze3d.platform.InputConstants
import fr.raconteur.simpleskinswapper.SimpleSkinSwapperClient
import fr.raconteur.simpleskinswapper.config.SimpleSkinSwapperConfig
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
    private val wheels: List<List<SkinEntry>> = entries.chunked(WHEEL_SIZE)
    private val wheelCount: Int = wheels.size

    // Continuous wheel position: wheelPos eases toward the integer targetPos. Both live in an
    // unwrapped space (rendering wraps modulo wheelCount) so a slide can cross the first/last seam.
    private var wheelPos = 0.0F
    private var targetPos = 0
    private var lastWheelPosUpdateNanos = 0L
    private var scrollAccum = 0.0

    private var selectedIndex = -1
    private val hoverAnimFactors = Array(wheelCount) { FloatArray(WHEEL_SIZE) }
    private var lastHoverAnimUpdateNanos = 0L

    init {
        if (wheelCount > 0 && SimpleSkinSwapperConfig.get().rememberWheelPosition) {
            val start = lastWheelPosition.coerceIn(0, wheelCount - 1)
            wheelPos = start.toFloat()
            targetPos = start
        }
    }

    override fun isPauseScreen(): Boolean = false

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

        if (entries.isEmpty()) {
            context.centeredText(
                font,
                Component.translatable("simpleskinswapper.screen.carousel.no_skins"),
                cx.toInt(), cy.toInt(), COLOR_TEXT
            )
            //? if >=26.1 {
            super.extractRenderState(context, mouseX, mouseY, delta)
            //?} else {
            /*super.render(context, mouseX, mouseY, delta)
            *///?}
            return
        }

        updateWheelPosition()
        val base = Math.round(wheelPos)
        val atRest = Math.abs(wheelPos - base) < REST_EPSILON
        val activeWheel = Math.floorMod(base, wheelCount)

        selectedIndex = if (atRest) {
            getSelectedIndex(mouseX, mouseY, cx, cy, wheels[activeWheel].size)
        } else {
            -1
        }

        // Centered wheel plus the peeking neighbor slots (-1 = left edge, +1 = right edge).
        // With two wheels the same neighbor legitimately fills both edge slots (circular wrap).
        for (s in -1..1) {
            if (wheelCount == 1 && s != 0) continue
            drawWheel(context, cx, cy, s - (wheelPos - base), Math.floorMod(base + s, wheelCount), s == 0 && atRest)
        }

        updateHoverAnimations(base, atRest)

        // Hovered skin name above the wheel, only while at rest
        if (atRest && selectedIndex >= 0) {
            context.centeredText(
                font,
                Component.nullToEmpty(wheels[activeWheel][selectedIndex].displayName),
                cx.toInt(), (cy - OUTER_RADIUS).toInt() - font.lineHeight - 6, COLOR_TEXT
            )
        }

        // Pagination feedback below the wheel: dots for few wheels, a counter beyond
        if (atRest && wheelCount > 1) {
            val fy = cy + OUTER_RADIUS + font.lineHeight + 4
            if (wheelCount <= 9) {
                val spacing = 12
                val startX = cx - (wheelCount - 1) * spacing / 2.0f
                for (d in 0..<wheelCount) {
                    val active = d == activeWheel
                    fillCircle(context, startX + d * spacing, fy, 2.0f, if (active) COLOR_TEXT else COLOR_PAGINATION_DIM)
                }
            } else {
                context.centeredText(
                    font,
                    Component.nullToEmpty("${activeWheel + 1}/$wheelCount"),
                    cx.toInt(), (fy - font.lineHeight / 2).toInt(), COLOR_TEXT
                )
            }
        }

        //? if >=26.1 {
        super.extractRenderState(context, mouseX, mouseY, delta)
        //?} else {
        /*super.render(context, mouseX, mouseY, delta)
        *///?}
    }

    // -------------------------------------------------------------------------
    // Wheel layout
    // -------------------------------------------------------------------------

    /** Eases [wheelPos] toward [targetPos]; the slide is interruptible because retargeting is just a number change. */
    private fun updateWheelPosition() {
        val now = System.nanoTime()
        val dt = if (lastWheelPosUpdateNanos == 0L) 0.0F else (now - lastWheelPosUpdateNanos) / 1_000_000_000.0F
        lastWheelPosUpdateNanos = now
        if (wheelPos == targetPos.toFloat()) return
        val t = 1.0F - Math.exp((-WHEEL_SLIDE_SPEED * dt).toDouble()).toFloat()
        val eased = Mth.lerp(t, wheelPos, targetPos.toFloat())
        wheelPos = if (Math.abs(targetPos - eased) < WHEEL_POS_SNAP_EPSILON) targetPos.toFloat() else eased
    }

    /** Renders one wheel slot: [offset] 0 = center (full scale), ±1 = half off-screen at the edges. */
    private fun drawWheel(context: GuiGraphicsExtractor, cx: Float, cy: Float, offset: Float, wheelIndex: Int, interactive: Boolean) {
        val scale = 1.0F - SIDE_WHEEL_SCALE * Math.min(Math.abs(offset), 1.0F)
        val wx = cx + offset * (this.width / 2.0F)
        val radius = OUTER_RADIUS * scale
        val wheel = wheels[wheelIndex]
        val n = wheel.size
        val hovered = interactive && selectedIndex in 0..<n

        // Draw pie sector backgrounds
        for (i in 0..<n) {
            drawSector(context, wx, cy, i, n, radius, hovered && i == selectedIndex)
        }

        // Center fill circle (on top of sectors)
        fillCircle(context, wx, cy, 28f * scale, COLOR_CENTER_BG)

        // Skin previews — painter's order: top (smallest py) first
        val sectorSize = 2 * Math.PI / n
        val angleOffset = -Math.PI / 2 - sectorSize / 2.0
        val previewDist = radius * 0.60F
        val order = Array(n) { it }
        order.sortBy { i ->
            cy + previewDist * Math.sin(angleOffset + sectorSize * i + sectorSize / 2.0)
        }
        for (i in order) {
            drawSectorPreview(context, wx, cy, i, n, previewDist, scale, wheelIndex)
        }
    }

    // -------------------------------------------------------------------------
    // Sector geometry
    // -------------------------------------------------------------------------

    private fun getSelectedIndex(mouseX: Int, mouseY: Int, cx: Float, cy: Float, n: Int): Int {
        val dx = mouseX - cx
        val dy = mouseY - cy
        val dist = Math.sqrt((dx * dx + dy * dy).toDouble())
        if (dist < 10 || dist > OUTER_RADIUS * 1.1) return -1

        val sectorSize = 2 * Math.PI / n
        val angleOffset = -Math.PI / 2 - sectorSize / 2.0

        val angle = Math.atan2(dy.toDouble(), dx.toDouble())
        val adjusted = ((angle - angleOffset) % (2 * Math.PI) + 2 * Math.PI) % (2 * Math.PI)
        val idx = (adjusted / sectorSize).toInt()
        return if (idx >= 0 && idx < n) idx else -1
    }

    private fun drawSector(
        context: GuiGraphicsExtractor, cx: Float, cy: Float, index: Int, n: Int, radius: Float, hovered: Boolean
    ) {
        val sectorSize = 2 * Math.PI / n
        val angleOffset = -Math.PI / 2 - sectorSize / 2.0
        val baseAngle = angleOffset + sectorSize * index
        val color = if (hovered) COLOR_SECTOR_HOVER else COLOR_SECTOR

        // Single sector: a full disc, no gap to inset.
        if (n == 1) {
            submitSectorFill(context, cx, cy, radius, baseAngle.toFloat(), (baseAngle + 2 * Math.PI).toFloat(), color, 0.0F)
            return
        }

        // Constant-width gap: each straight edge is the nominal radius line offset inward by
        // half the gap width (arc endpoints rotated by asin(halfGap/radius), apex pushed out to
        // halfGap/sin(halfSpan)), so the separator stays a hairline from center to rim instead
        // of a wedge that widens outward.
        val halfGap = GAP_WIDTH / 2f
        val edgeInset = Math.asin((halfGap / radius).toDouble())
        val startAngle = baseAngle + edgeInset
        val endAngle = baseAngle + sectorSize - edgeInset
        val innerRadius = (halfGap / Math.sin(sectorSize / 2.0)).toFloat()
        submitSectorFill(context, cx, cy, radius, startAngle.toFloat(), endAngle.toFloat(), color, innerRadius)
    }

    private fun fillCircle(context: GuiGraphicsExtractor, cx: Float, cy: Float, radius: Float, color: Int) {
        submitSectorFill(context, cx, cy, radius, 0.0f, (2 * Math.PI).toFloat(), color, 0.0F)
    }

    /** Submits one sector as a single triangle-fan mesh — O(1) draw submissions per sector. */
    private fun submitSectorFill(
        context: GuiGraphicsExtractor, cx: Float, cy: Float, radius: Float,
        startAngle: Float, endAngle: Float, color: Int, innerRadius: Float
    ) {
        context.guiRenderState.addGuiElement(
            SectorFillRenderState(
                Matrix3x2f(context.pose()), cx, cy, radius, startAngle, endAngle, color, innerRadius,
                context.scissorStack.peek()
            )
        )
    }

    /** Eases every sector's hover factor toward its target; off-center wheels and slides settle back to 0. */
    private fun updateHoverAnimations(base: Int, atRest: Boolean) {
        val now = System.nanoTime()
        val dt = if (lastHoverAnimUpdateNanos == 0L) 0.0F else (now - lastHoverAnimUpdateNanos) / 1_000_000_000.0F
        lastHoverAnimUpdateNanos = now
        val t = 1.0F - Math.exp((-HOVER_ANIM_SPEED * dt).toDouble()).toFloat()
        val activeWheel = Math.floorMod(base, wheelCount)
        for (w in 0..<wheelCount) {
            for (i in 0..<wheels[w].size) {
                val target = if (atRest && w == activeWheel && i == selectedIndex) 1.0F else 0.0F
                var eased = Mth.lerp(t, hoverAnimFactors[w][i], target)
                if (Math.abs(eased - target) < HOVER_ANIM_SNAP_EPSILON) eased = target
                hoverAnimFactors[w][i] = eased
            }
        }
    }

    private fun drawSectorPreview(
        context: GuiGraphicsExtractor, wx: Float, cy: Float, index: Int, n: Int,
        previewDist: Float, scale: Float, wheelIndex: Int
    ) {
        val sectorSize = 2 * Math.PI / n
        val angleOffset = -Math.PI / 2 - sectorSize / 2.0
        val midAngle = angleOffset + sectorSize * index + sectorSize / 2.0

        val px = (wx + previewDist * Math.cos(midAngle)).toInt()
        val py = (cy + previewDist * Math.sin(midAngle)).toInt()

        val entry = wheels[wheelIndex][index]
        entry.ensureTextureLoaded()

        val halfW = (16 * scale).toInt().coerceAtLeast(1)
        val halfH = (24 * scale).toInt().coerceAtLeast(1)

        // Viewport culling: fully off-screen previews are not submitted at all.
        if (px + halfW < 0 || px - halfW > this.width || py + halfH < 0 || py - halfH > this.height) return

        val textureId = entry.textureId ?: return

        // Every preview is a live entity render; only the hovered one plays the walk animation.
        // Partially off-screen rects are clipped by the scissor stack, keeping the projection intact.
        SkinRenderer.renderPlayer(
            context, px - halfW, py - halfH, px + halfW, py + halfH, halfH,
            buildPlayerSkin(entry, textureId), hoverAnimFactors[wheelIndex][index]
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

    override fun mouseScrolled(mouseX: Double, mouseY: Double, hozAmount: Double, vertAmount: Double): Boolean {
        if (wheelCount > 1) {
            // Each whole notch targets one adjacent wheel. The lead clamp absorbs extra notches so
            // the target never rides more than WHEEL_MAX_LEAD wheels ahead of the rendered position:
            // chained scrolling glides continuously, and stopping lets the position catch up.
            // Reversing mid-slide always works because the clamp is measured from the rendered position.
            scrollAccum += vertAmount
            while (Math.abs(scrollAccum) >= 1.0) {
                val step = if (scrollAccum > 0) 1 else -1
                scrollAccum -= step
                val newTarget = targetPos + step
                if (Math.abs(newTarget - wheelPos) <= WHEEL_MAX_LEAD) targetPos = newTarget
            }
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, hozAmount, vertAmount)
    }

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
        val base = Math.round(wheelPos)
        val atRest = Math.abs(wheelPos - base) < REST_EPSILON
        if (!atRest || selectedIndex < 0) return

        val wheel = wheels[Math.floorMod(base, wheelCount)]
        if (selectedIndex >= wheel.size) return
        val entry = wheel[selectedIndex]

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

    override fun onClose() {
        if (wheelCount > 0) lastWheelPosition = Math.floorMod(Math.round(wheelPos), wheelCount)
        //? if >=26.2 {
        minecraft.gui.setScreen(parent)
        //?} else {
        /*minecraft.setScreen(parent)
        *///?}
    }

    companion object {
        private const val WHEEL_SIZE = 10
        private const val OUTER_RADIUS = 90.0f

        // Constant width, in pixels, of the separator line between adjacent sectors.
        private const val GAP_WIDTH = 3.0f

        // Scale lost at the edge slots: 1.0 at the center, 0.7 half off-screen.
        private const val SIDE_WHEEL_SCALE = 0.3F

        // Slide easing: higher = snappier; the position chases the scroll target and can be retargeted mid-slide.
        private const val WHEEL_SLIDE_SPEED = 10.0F
        private const val WHEEL_POS_SNAP_EPSILON = 0.001F
        private const val REST_EPSILON = 0.05F

        // How far the scroll target may ride ahead of the rendered position: one active slide
        // plus one queued wheel, so chained scrolling glides without unbounded flinging.
        private const val WHEEL_MAX_LEAD = 2.0F

        // Hover animation easing: higher = faster settle back to the neutral pose.
        private const val HOVER_ANIM_SPEED = 10.0F
        private const val HOVER_ANIM_SNAP_EPSILON = 0.05F

        // Session-scoped last active wheel, restored on open when rememberWheelPosition is enabled.
        private var lastWheelPosition = 0

        private val COLOR_SECTOR = 0xCC1A2535.toInt()
        private val COLOR_SECTOR_HOVER = 0xEE2B5F9E.toInt()
        private val COLOR_CENTER_BG = 0xBB0D1627.toInt()
        private val COLOR_TEXT = 0xFFFFFFFF.toInt()
        private val COLOR_PAGINATION_DIM = 0x60FFFFFF.toInt()

        // -------------------------------------------------------------------------
        // Data loading — reads order.txt, no writes
        // -------------------------------------------------------------------------

        private fun loadWheelEntries(): List<SkinEntry> = SkinCarouselScreen.loadOrderedEntries()
    }
}
