package fr.raconteur.simpleskinswapper.gui

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
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.player.PlayerModelType
import net.minecraft.world.entity.player.PlayerSkin

class SkinWheelScreen(private val parent: Screen?) : Screen(Component.empty()) {

    private val entries: List<SkinEntry> = loadWheelEntries()
    private var selectedIndex = -1

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
        fillSector(context, cx, cy, OUTER_RADIUS, startAngle, endAngle, color)
    }

    private fun fillCircle(context: GuiGraphicsExtractor, cx: Float, cy: Float, radius: Float, color: Int) {
        fillSector(context, cx, cy, radius, 0.0, 2 * Math.PI, color)
    }

    /**
     * Fills a pie sector using context.fill() — one call per pixel column.
     * Uses analytical cross-product tests to compute the y-range per column in O(1),
     * giving O(r) total instead of O(r²) with atan2.
     *
     * For a sector [startAngle, endAngle] with span < 2π:
     *   A point (dx, dy) is inside iff
     *     dy*cos(S) - dx*sin(S) >= 0   (left of start ray)
     *     dy*cos(E) - dx*sin(E) <= 0   (right of end ray)
     * Each constraint is linear in dy → gives yLo / yHi directly.
     */
    private fun fillSector(
        context: GuiGraphicsExtractor, cx: Float, cy: Float, radius: Float,
        startAngle: Double, endAngle: Double, color: Int
    ) {
        val r = Math.ceil(radius.toDouble()).toInt()
        val icx = cx.toInt()
        val icy = cy.toInt()

        val span = ((endAngle - startAngle) % (2 * Math.PI) + 2 * Math.PI) % (2 * Math.PI)
        if (span >= 2 * Math.PI - 1e-9) {
            // Full circle — skip angular tests entirely
            for (dx in -r..r) {
                val dMax = Math.sqrt(Math.max(0.0, (radius * radius - dx * dx).toDouble())).toInt()
                if (dMax > 0) context.fill(icx + dx, icy - dMax, icx + dx + 1, icy + dMax + 1, color)
            }
            return
        }

        val cosS = Math.cos(startAngle)
        val sinS = Math.sin(startAngle)
        val cosE = Math.cos(endAngle)
        val sinE = Math.sin(endAngle)

        for (dx in -r..r) {
            val r2 = (radius * radius - dx * dx).toDouble()
            if (r2 <= 0) continue
            val dyMax = Math.sqrt(r2).toInt()
            var yLo = -dyMax.toDouble()
            var yHi = dyMax.toDouble()

            // Start-ray constraint: dy*cosS - dx*sinS >= 0  →  dy >= dx*sinS/cosS
            val tS = dx * sinS
            if (cosS > 1e-9) yLo = Math.max(yLo, tS / cosS)
            else if (cosS < -1e-9) yHi = Math.min(yHi, tS / cosS)
            else if (tS > 1e-9) continue // column entirely outside start ray

            // End-ray constraint: dy*cosE - dx*sinE <= 0  →  dy <= dx*sinE/cosE
            val tE = dx * sinE
            if (cosE > 1e-9) yHi = Math.min(yHi, tE / cosE)
            else if (cosE < -1e-9) yLo = Math.max(yLo, tE / cosE)
            else if (tE < -1e-9) continue // column entirely outside end ray

            val fillY1 = Math.ceil(yLo).toInt()
            val fillY2 = Math.floor(yHi).toInt()
            if (fillY1 <= fillY2) {
                context.fill(icx + dx, icy + fillY1, icx + dx + 1, icy + fillY2 + 1, color)
            }
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

        val textureId = entry.textureId
        if (textureId != null) {
            val skinTextures = PlayerSkin(
                ClientAsset.DownloadedTexture(textureId, ""), null, null,
                if (entry.skinType == SkinType.SLIM) PlayerModelType.SLIM else PlayerModelType.WIDE,
                true
            )
            SkinRenderer.renderPlayer(context, px - halfW, py - halfH, px + halfW, py + halfH, halfH, skinTextures)
        }
    }

    // -------------------------------------------------------------------------
    // Input handling
    // -------------------------------------------------------------------------

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        if (click.button() == 0) {
            apply()
            return true
        }
        if (click.button() == 1) {
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
                minecraft?.player?.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 1.0f)
                SkinChange.changeSkin(
                    entry.file,
                    entry.skinType,
                    entry.textureId,
                    {
                        minecraft?.player?.overlayMessage(
                            Component.translatable("simpleskinswapper.message.success")
                        )
                    },
                    { err ->
                        minecraft?.player?.overlayMessage(
                            Component.translatable("simpleskinswapper.message.error", err)
                        )
                    }
                )
                minecraft?.player?.overlayMessage(
                    Component.translatable("simpleskinswapper.message.applying")
                )
            }
        }
    }

    override fun onClose() {
        //? if >=26.2 {
        minecraft?.gui?.setScreen(parent)
        //?} else {
        /*minecraft?.setScreen(parent)
        *///?}
    }

    companion object {
        private const val MAX_ENTRIES = 10
        private const val OUTER_RADIUS = 90.0f
        private val GAP_HALF_ANGLE = Math.toRadians(2.0).toFloat()

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
