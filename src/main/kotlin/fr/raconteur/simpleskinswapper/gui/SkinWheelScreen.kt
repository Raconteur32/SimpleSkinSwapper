package fr.raconteur.simpleskinswapper.gui

import com.mojang.blaze3d.platform.InputConstants
import fr.raconteur.simpleskinswapper.SimpleSkinSwapperClient
import fr.raconteur.simpleskinswapper.config.AllSkinsWheelMode
import fr.raconteur.simpleskinswapper.config.SimpleSkinSwapperConfig
import fr.raconteur.simpleskinswapper.gui.library.SkinCategories
import fr.raconteur.simpleskinswapper.library.LibraryCategory
import fr.raconteur.simpleskinswapper.library.SkinRecords
import fr.raconteur.simpleskinswapper.library.SkinCategoryPalette
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
import fr.raconteur.simpleskinswapper.SkinType
import kotlin.math.cos
import kotlin.math.sin

class SkinWheelScreen(private val parent: Screen?) : Screen(Component.empty()) {

    /** Owner of a wheel: a library category, or the all-skins pseudo-group. */
    private sealed interface WheelOwner {
        data class Category(val category: LibraryCategory) : WheelOwner
        data object AllSkins : WheelOwner
    }

    /** Press → elastic stretch → reorder → commit/cancel machine. */
    private sealed interface DragState {
        data object Idle : DragState

        /** Press held on a filled sector: the slice stretches outward under the cursor. */
        data class Pending(
            val wheel: Int,
            val slot: Int,
            val entry: SkinEntry,
            val pressX: Double,
            val pressY: Double,
        ) : DragState

        /** Reorder mode: the slice rides the nearest filled slot until a click commits. */
        data class Reorder(
            val owner: WheelOwner,
            val firstWheel: Int,
            val flatIndex: Int,
            val skins: List<SkinEntry>,
            val originWheel: Int,
            val originSlot: Int,
        ) : DragState

        /** Ease-back animation after a cancel; the layout is already restored. */
        data class Canceling(
            val originWheel: Int,
            val originSlot: Int,
            val startNanos: Long,
            val fromAngle: Double,
            val fromPull: Float,
        ) : DragState
    }

    private val client get() = minecraft

    private val byId: Map<String, SkinEntry> = SkinRecords.all().associate { it.id to SkinEntry.fromRecord(it) }

    // Wheels composed from wheel groups — the all-skins pseudo-group and the categories —
    // each contributing consecutive wheels of ten. wheelOwners[w] owns wheels[w].
    private val wheelBuild = buildWheels()
    private var wheels: List<List<SkinEntry>> = wheelBuild.wheels
    private var wheelOwners: List<WheelOwner> = wheelBuild.owners
    private val wheelCount: Int get() = wheels.size

    private val layout = WheelGroupLayout()
    private var dragState: DragState = DragState.Idle

    /** Filled (global wheel, slot) the reorder slice currently rides; refreshed each frame. */
    private var reorderTarget: Pair<Int, Int>? = null

    /** Last rendered slice pose (mid angle, outward pull) — the ease-back start on cancel. */
    private var lastSlicePose: Pair<Double, Float>? = null

    // Continuous wheel position: wheelPos eases toward the integer targetPos. Both live in an
    // unwrapped space (rendering wraps modulo wheelCount) so a slide can cross the first/last seam.
    private var wheelPos = 0.0F
    private var targetPos = 0
    private var lastWheelPosUpdateNanos = 0L
    private var scrollAccum = 0.0

    private var selectedIndex = -1
    private val hoverAnimFactors = Array(wheelCount) { FloatArray(WHEEL_SIZE) }
    private var lastHoverAnimUpdateNanos = 0L
    private var lastLayoutUpdateNanos = 0L

    // Hovered pagination dot index at rest, or -1; used for the tooltip and dot clicks.
    private var hoverDot = -1
    private val paginationDots = ArrayList<Pair<Float, Float>>()

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

        if (wheelCount == 0) {
            drawEmptyState(context, cx, cy, mouseX, mouseY, delta)
            return
        }

        updateWheelPosition()
        val base = Math.round(wheelPos)
        val atRest = Math.abs(wheelPos - base) < REST_EPSILON
        val activeWheel = Math.floorMod(base, wheelCount)
        val dragging = dragState is DragState.Reorder

        // A reorder drag cannot follow the scroll target into another group.
        cancelReorderLeavingGroup()

        updateSelectedIndex(mouseX, mouseY, cx, cy, activeWheel, allow = atRest && !dragging)

        updateLayout()

        // Centered wheel plus the peeking neighbor slots (-1 = left edge, +1 = right edge).
        // With two wheels the same neighbor legitimately fills both edge slots (circular wrap).
        for (s in -1..1) {
            if (wheelCount == 1 && s != 0) continue
            drawWheel(context, cx, cy, s - (wheelPos - base), Math.floorMod(base + s, wheelCount), s == 0 && atRest)
        }

        updateHoverAnimations(base, atRest && !dragging)

        // Group name above the wheel — the direct "which wheel am I looking at" cue —
        // with the hovered skin as a slightly dimmer subtitle. Both only while at rest.
        if (atRest) {
            val categoryY = (cy - OUTER_RADIUS).toInt() - 2 * font.lineHeight - 6
            context.centeredText(font, ownerLabel(wheelOwners[activeWheel]), cx.toInt(), categoryY, COLOR_TEXT)
            if (selectedIndex >= 0) {
                context.centeredText(
                    font,
                    Component.nullToEmpty(wheels[activeWheel][selectedIndex].displayName),
                    cx.toInt(), categoryY + font.lineHeight, COLOR_SUBTITLE
                )
            }
        }

        drawPagination(context, cx, cy, mouseX, mouseY, atRest, activeWheel)

        drawDragSlice(context, cx, cy, mouseX, mouseY, base, atRest)

        //? if >=26.1 {
        super.extractRenderState(context, mouseX, mouseY, delta)
        //?} else {
        /*super.render(context, mouseX, mouseY, delta)
         *///?}
    }

    private fun drawEmptyState(
        context: GuiGraphicsExtractor, cx: Float, cy: Float, mouseX: Int, mouseY: Int, delta: Float
    ) {
        val key = if (byId.isEmpty()) "simpleskinswapper.screen.carousel.no_skins"
        else "simpleskinswapper.screen.wheel.empty"
        context.centeredText(
            font,
            Component.translatable(key),
            cx.toInt(), cy.toInt(), COLOR_TEXT
        )
        //? if >=26.1 {
        super.extractRenderState(context, mouseX, mouseY, delta)
        //?} else {
        /*super.render(context, mouseX, mouseY, delta)
         *///?}
    }

    /**
     * Pagination feedback below the wheel: dots for few wheels, a counter beyond.
     * Multi-group compositions color each dot after its owning group (categories by
     * dye, all-skins neutral), and dots become clickable shortcuts to a group's first wheel.
     */
    private fun drawPagination(
        context: GuiGraphicsExtractor, cx: Float, cy: Float, mouseX: Int, mouseY: Int, atRest: Boolean, activeWheel: Int
    ) {
        hoverDot = -1
        if (!atRest || wheelCount <= 1) return
        val fy = cy + OUTER_RADIUS + font.lineHeight + 4
        if (wheelCount <= 9) {
            val spacing = 12
            val startX = cx - (wheelCount - 1) * spacing / 2.0f
            val multiGroup = wheelOwners.distinct().size > 1
            paginationDots.clear()
            for (d in 0..<wheelCount) {
                val dx = startX + d * spacing
                paginationDots.add(dx to fy)
                if (Math.hypot((mouseX - dx).toDouble(), (mouseY - fy).toDouble()) <= DOT_HIT_RADIUS) hoverDot = d
                val color = ownerColor(wheelOwners[d])
                val dotColor = when {
                    multiGroup && d == activeWheel -> color
                    multiGroup -> (0x80 shl 24) or (color and 0xFFFFFF)
                    d == activeWheel -> COLOR_TEXT
                    else -> COLOR_PAGINATION_DIM
                }
                fillCircle(context, dx, fy, if (d == activeWheel) 2.5f else 2.0f, dotColor)
            }
            if (hoverDot >= 0) {
                drawTooltip(context, mouseX, mouseY, ownerLabel(wheelOwners[hoverDot]))
            }
        } else {
            context.centeredText(
                font,
                Component.nullToEmpty("${activeWheel + 1}/$wheelCount"),
                cx.toInt(), (fy - font.lineHeight / 2).toInt(), COLOR_TEXT
            )
        }
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

    /** Drives the reorder reflow easing. */
    private fun updateLayout() {
        val now = System.nanoTime()
        val dt = if (lastLayoutUpdateNanos == 0L) 0.0F else (now - lastLayoutUpdateNanos) / 1_000_000_000.0F
        lastLayoutUpdateNanos = now
        layout.update(dt)
    }

    /** A reorder drag cannot follow the scroll target into another group. */
    private fun cancelReorderLeavingGroup() {
        val drag = dragState as? DragState.Reorder ?: return
        if (wheelOwners[Math.floorMod(targetPos, wheelCount)] != drag.owner) cancelReorder(animated = true)
    }

    /** Angle hit-test against the padded sector count; filler slots resolve to no
     *  selection — only real skins are selectable. */
    private fun updateSelectedIndex(mouseX: Int, mouseY: Int, cx: Float, cy: Float, activeWheel: Int, allow: Boolean) {
        selectedIndex = if (allow) {
            val hit = getSelectedIndex(mouseX, mouseY, cx, cy, maxOf(wheels[activeWheel].size, MIN_WHEEL_SLOTS))
            if (hit in 0..<wheels[activeWheel].size) hit else -1
        } else {
            -1
        }
    }

    /** Renders one wheel slot: [offset] 0 = center (full scale), ±1 = half off-screen at the edges. */
    private fun drawWheel(context: GuiGraphicsExtractor, cx: Float, cy: Float, offset: Float, wheelIndex: Int, interactive: Boolean) {
        val scale = 1.0F - SIDE_WHEEL_SCALE * Math.min(Math.abs(offset), 1.0F)
        val wx = cx + offset * (this.width / 2.0F)
        val radius = OUTER_RADIUS * scale
        val display = displaySkinsOn(wheelIndex)
        // The rim keeps its at-rest sector count during a reorder drag: the gap opens
        // inside the existing sectors, so a full wheel never re-sectors and a shifted
        // skin can never wrap onto slot 0.
        val n = maxOf(wheels[wheelIndex].size, MIN_WHEEL_SLOTS)
        // Padding starts past the group's window: during a drag the window end is the
        // engine's, otherwise the wheel's own skin count.
        val drag = dragState as? DragState.Reorder
        val paddingFrom = if (drag != null && wheelIndex >= drag.firstWheel) {
            (layout.displaySize - (wheelIndex - drag.firstWheel) * WheelGroupLayout.SLOTS).coerceIn(0, n)
        } else {
            display.size
        }
        val hovered = interactive && drag == null && selectedIndex in 0..<wheels[wheelIndex].size
        val pending = dragState as? DragState.Pending

        // Draw pie sector backgrounds; the elastically pressed slot renders dimmed —
        // the slice has visually left it.
        for (i in 0..<n) {
            val pressedAway = pending != null && wheelIndex == pending.wheel && i == pending.slot
            drawSector(context, wx, cy, i, n, radius, hovered && i == selectedIndex && !pressedAway, empty = i >= paddingFrom || pressedAway)
        }

        // Center fill circle (on top of sectors)
        fillCircle(context, wx, cy, 28f * scale, COLOR_CENTER_BG)

        drawWheelPreviews(context, wx, cy, radius, scale, wheelIndex, n, display, pending)
    }

    /** Skin previews — painter's order: top (smallest py) first. Real slots only. */
    private fun drawWheelPreviews(
        context: GuiGraphicsExtractor, wx: Float, cy: Float, radius: Float, scale: Float,
        wheelIndex: Int, n: Int, display: List<Pair<Double, SkinEntry>>, pending: DragState.Pending?
    ) {
        val previewDist = radius * 0.60F
        val angles = display.map { (slotPos, _) -> WheelGroupLayout.midAngle(slotPos, n) }
        val order = display.indices.sortedBy { k -> cy + previewDist * Math.sin(angles[k]) }
        for (k in order) {
            val (slotPos, entry) = display[k]
            if (pending != null && wheelIndex == pending.wheel && slotPos.toInt() == pending.slot) continue
            val factor = hoverAnimFactors[wheelIndex].getOrNull(slotPos.toInt()) ?: 0f
            drawPreviewAt(context, wx, cy, angles[k], previewDist, scale, entry, factor)
        }
    }

    /**
     * Skins displayed on [wheelIndex] as (mid-angle slot position, entry). The idle
     * layout is the static wheel; while a reorder drag is active the group's wheels
     * show the reflow engine's eased layout — the dragged skin is skipped (it rides
     * the target slot) and other groups' wheels render empty.
     */
    private fun displaySkinsOn(wheelIndex: Int): List<Pair<Double, SkinEntry>> {
        val drag = dragState as? DragState.Reorder
        if (drag == null || !layout.active) return wheels[wheelIndex].mapIndexed { i, e -> i.toDouble() to e }
        val groupWheel = wheelIndex - drag.firstWheel
        val result = ArrayList<Pair<Double, SkinEntry>>()
        if (groupWheel < 0) return result
        for (o in drag.skins.indices) {
            if (o == drag.flatIndex) continue
            val pos = layout.easedPosition(o)
            if (WheelGroupLayout.wheelOf(pos) == groupWheel) {
                result.add((pos - groupWheel * WheelGroupLayout.SLOTS).toDouble() to drag.skins[o])
            }
        }
        return result
    }

    // -------------------------------------------------------------------------
    // Drag slice (elastic stretch, reorder ride, cancel ease-back)
    // -------------------------------------------------------------------------

    private fun drawDragSlice(context: GuiGraphicsExtractor, cx: Float, cy: Float, mouseX: Int, mouseY: Int, base: Int, atRest: Boolean) {
        when (val drag = dragState) {
            is DragState.Pending -> {
                val n = maxOf(wheels[drag.wheel].size, MIN_WHEEL_SLOTS)
                val angle = WheelGroupLayout.midAngle(drag.slot.toDouble(), n)
                val unitX = cos(angle).toFloat()
                val unitY = sin(angle).toFloat()
                val outward = ((mouseX - drag.pressX) * unitX + (mouseY - drag.pressY) * unitY).toFloat()
                if (outward >= PULL_DISTANCE && atRest) {
                    beginReorder(drag)
                    return
                }
                val pull = (outward * ELASTIC_DAMP).coerceIn(0f, ELASTIC_MAX)
                lastSlicePose = angle to pull
                drawPulledSlice(context, cx, cy, drag.wheel, angle, pull, drag.entry)
            }

            is DragState.Reorder -> {
                val activeWheel = Math.floorMod(base, wheelCount)
                val groupWheel = activeWheel - drag.firstWheel
                val display = displaySkinsOn(activeWheel)
                val n = maxOf(wheels[activeWheel].size, MIN_WHEEL_SLOTS)
                val mouseAngle = Math.atan2((mouseY - cy).toDouble(), (mouseX - cx).toDouble())
                val slot = layout.nearestLandingSlot(groupWheel, mouseAngle, n)
                if (slot != null) {
                    layout.setTarget(groupWheel * WheelGroupLayout.SLOTS + slot)
                    reorderTarget = activeWheel to slot
                }
                val dragged = drag.skins[drag.flatIndex]
                if (slot != null) {
                    drawSector(context, cx, cy, slot, n, OUTER_RADIUS, hovered = true, empty = false)
                    val angle = WheelGroupLayout.midAngle(slot.toDouble(), n)
                    lastSlicePose = angle to SECTOR_PULL
                    drawPulledSlice(context, cx, cy, activeWheel, angle, SECTOR_PULL, dragged)
                } else {
                    // Nowhere to land on this wheel: the slice parks on its origin axis.
                    val n0 = maxOf(wheels[drag.originWheel].size, MIN_WHEEL_SLOTS)
                    val angle = WheelGroupLayout.midAngle(drag.originSlot.toDouble(), n0)
                    lastSlicePose = angle to SECTOR_PULL
                    drawPulledSlice(context, cx, cy, drag.originWheel, angle, SECTOR_PULL, dragged)
                }
            }

            is DragState.Canceling -> {
                val progress = ((System.nanoTime() - drag.startNanos).toFloat() / CANCEL_EASE_NANOS).coerceIn(0f, 1f)
                if (progress >= 1f) {
                    dragState = DragState.Idle
                    return
                }
                val ease = 1f - progress * progress
                val n0 = maxOf(wheels[drag.originWheel].size, MIN_WHEEL_SLOTS)
                val homeAngle = WheelGroupLayout.midAngle(drag.originSlot.toDouble(), n0)
                lastSlicePose = (drag.fromAngle + (homeAngle - drag.fromAngle) * ease) to (drag.fromPull * (1f - ease))
                drawPulledSlice(
                    context, cx, cy, drag.originWheel, lastSlicePose!!.first, lastSlicePose!!.second,
                    wheels[drag.originWheel][drag.originSlot]
                )
            }

            is DragState.Idle -> {}
        }
    }

    /** Draws one slice (sector mesh + live preview) shifted [pull] px outward along [angle]. */
    private fun drawPulledSlice(
        context: GuiGraphicsExtractor, cx: Float, cy: Float, wheelIndex: Int,
        angle: Double, pull: Float, entry: SkinEntry
    ) {
        // Same offset as drawWheel: the rendered position of wheelIndex relative to
        // the sliding view (0 = centered).
        val rel = wheelIndex - wheelPos
        val scale = 1.0F - SIDE_WHEEL_SCALE * Math.min(Math.abs(rel), 1.0F)
        val wx = cx + rel * (this.width / 2.0F)
        val radius = OUTER_RADIUS * scale
        val n = maxOf(wheels[wheelIndex].size, MIN_WHEEL_SLOTS)
        val unitX = cos(angle).toFloat()
        val unitY = sin(angle).toFloat()
        drawSectorAt(
            context, wx + unitX * pull, cy + unitY * pull,
            angle - WheelGroupLayout.sectorSize(n) / 2, WheelGroupLayout.sectorSize(n),
            radius, hovered = false, empty = false
        )
        drawPreviewAt(context, wx + unitX * pull, cy + unitY * pull, angle, radius * 0.60F, scale, entry, 0f)
    }

    // -------------------------------------------------------------------------
    // Sector geometry
    // -------------------------------------------------------------------------

    private fun getSelectedIndex(mouseX: Int, mouseY: Int, cx: Float, cy: Float, n: Int): Int {
        val dx = mouseX - cx
        val dy = mouseY - cy
        val dist = Math.sqrt((dx * dx + dy * dy).toDouble())
        if (dist < 10 || dist > OUTER_RADIUS * 1.1) return -1
        return WheelGroupLayout.slotForAngle(Math.atan2(dy.toDouble(), dx.toDouble()), n)
    }

    private fun drawSector(
        context: GuiGraphicsExtractor, cx: Float, cy: Float, index: Int, n: Int, radius: Float,
        hovered: Boolean, empty: Boolean
    ) {
        drawSectorAt(
            context, cx, cy,
            WheelGroupLayout.startAngle(index.toDouble(), n),
            WheelGroupLayout.sectorSize(n), radius, hovered, empty
        )
    }

    /** One sector mesh from its [baseAngle] (start edge) and [span], at an arbitrary center. */
    private fun drawSectorAt(
        context: GuiGraphicsExtractor, cx: Float, cy: Float, baseAngle: Double, span: Double,
        radius: Float, hovered: Boolean, empty: Boolean
    ) {
        val color = when {
            empty -> COLOR_SECTOR_EMPTY
            hovered -> COLOR_SECTOR_HOVER
            else -> COLOR_SECTOR
        }

        // Constant-width gap: each straight edge is the nominal radius line offset inward by
        // half the gap width (arc endpoints rotated by asin(halfGap/radius), apex pushed out to
        // halfGap/sin(halfSpan)), so the separator stays a hairline from center to rim instead
        // of a wedge that widens outward. n is always >= MIN_WHEEL_SLOTS — no single-disc case.
        val halfGap = GAP_WIDTH / 2f
        val edgeInset = Math.asin((halfGap / radius).toDouble())
        val startAngle = baseAngle + edgeInset
        val endAngle = baseAngle + span - edgeInset
        val innerRadius = (halfGap / Math.sin(span / 2.0)).toFloat()
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

    private fun drawPreviewAt(
        context: GuiGraphicsExtractor, wx: Float, cy: Float, midAngle: Double,
        dist: Float, scale: Float, entry: SkinEntry, hoverFactor: Float
    ) {
        val px = (wx + dist * Math.cos(midAngle)).toInt()
        val py = (cy + dist * Math.sin(midAngle)).toInt()

        entry.ensureTextureLoaded()

        val halfW = (16 * scale).toInt().coerceAtLeast(1)
        val halfH = (24 * scale).toInt().coerceAtLeast(1)

        // Viewport culling: fully off-screen previews are not submitted at all.
        val offX = px + halfW < 0 || px - halfW > this.width
        val offY = py + halfH < 0 || py - halfH > this.height
        if (offX || offY) return

        val textureId = entry.textureId ?: return

        // Every preview is a live entity render; only the hovered one plays the walk animation.
        // Partially off-screen rects are clipped by the scissor stack, keeping the projection intact.
        SkinRenderer.renderPlayer(
            context, intArrayOf(px - halfW, py - halfH, px + halfW, py + halfH), halfH,
            buildPlayerSkin(entry, textureId), hoverFactor
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
            // During a reorder drag the group check in the render loop cancels the drag
            // as soon as the target leaves the drag's group.
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
            when (val drag = dragState) {
                is DragState.Reorder ->
                    if (hoverDot >= 0) jumpToDot() else commitReorder()

                else ->
                    if (hoverDot >= 0) {
                        jumpToDot()
                    } else {
                        pressTarget(click.x(), click.y())?.let { (wheel, slot) ->
                            dragState = DragState.Pending(
                                wheel, slot, wheels[wheel][slot], click.x(), click.y()
                            )
                        }
                    }
            }
            return true
        }
        if (click.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            when (dragState) {
                is DragState.Reorder -> cancelReorder(animated = true)
                is DragState.Canceling -> {}
                else -> onClose()
            }
            return true
        }
        return super.mouseClicked(click, doubled)
    }

    override fun mouseReleased(click: MouseButtonEvent): Boolean {
        when (val drag = dragState) {
            is DragState.Pending -> {
                dragState = DragState.Idle
                applyAt(drag.wheel, drag.slot)
                return true
            }

            // The drag's release keeps reorder mode alive; the next click commits.
            is DragState.Reorder -> return true

            else -> {}
        }
        return super.mouseReleased(click)
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        if (input.key() == InputConstants.KEY_ESCAPE && dragState is DragState.Reorder) {
            cancelReorder(animated = true)
            return true
        }
        return super.keyPressed(input)
    }

    override fun keyReleased(input: KeyEvent): Boolean {
        if (SimpleSkinSwapperClient.openWheelKey?.matches(input) == true) {
            when (val drag = dragState) {
                is DragState.Reorder -> cancelReorder(animated = false)
                is DragState.Pending -> dragState = DragState.Idle
                else -> {}
            }
            onClose()
            return true
        }
        return super.keyReleased(input)
    }

    /** (wheel, slot) under the cursor when a press can start a drag: centered wheel,
     *  at rest, over a real skin. Null otherwise — nothing is pressed. */
    private fun pressTarget(x: Double, y: Double): Pair<Int, Int>? {
        val base = Math.round(wheelPos)
        if (Math.abs(wheelPos - base) >= REST_EPSILON) return null
        val wheel = Math.floorMod(base, wheelCount)
        val hit = getSelectedIndex(
            x.toInt(), y.toInt(), width / 2.0f, height / 2.0f,
            maxOf(wheels[wheel].size, MIN_WHEEL_SLOTS)
        )
        if (hit < 0 || hit >= wheels[wheel].size) return null
        return wheel to hit
    }

    /** Pagination-dot shortcut: slides to the dot group's first wheel; a reorder drag
     *  cancels first when the jump leaves its group. */
    private fun jumpToDot() {
        val desiredActive = firstWheelOf(wheelOwners[hoverDot])
        val drag = dragState
        if (drag is DragState.Reorder && wheelOwners[desiredActive] != drag.owner) cancelReorder(animated = true)
        val current = Math.floorMod(Math.round(wheelPos), wheelCount)
        var delta = desiredActive - current
        delta = ((delta + wheelCount / 2 + wheelCount) % wheelCount) - wheelCount / 2
        if (delta != 0) targetPos += delta
        hoverDot = -1
    }

    /** Captures the pressed slot's group and hands the slice to the reflow engine. */
    private fun beginReorder(pending: DragState.Pending) {
        val owner = wheelOwners[pending.wheel]
        val firstWheel = firstWheelOf(owner)
        val flatIndex = (pending.wheel - firstWheel) * WheelGroupLayout.SLOTS + pending.slot
        val skins = ArrayList<SkinEntry>()
        for (w in firstWheel..<wheelCount) {
            if (wheelOwners[w] != owner) break
            skins.addAll(wheels[w])
        }
        layout.begin(skins.size, flatIndex)
        dragState = DragState.Reorder(
            owner, firstWheel, flatIndex, skins, pending.wheel, pending.slot
        )
    }

    /** Cancels the drag: the layout is restored and the slice eases back to its origin slot. */
    private fun cancelReorder(animated: Boolean) {
        val drag = dragState as? DragState.Reorder ?: return
        val pose = lastSlicePose
        dragState = if (animated && pose != null) {
            DragState.Canceling(drag.originWheel, drag.originSlot, System.nanoTime(), pose.first, pose.second)
        } else {
            DragState.Idle
        }
        layout.end()
        reorderTarget = null
    }

    /** Commits the reorder: the dragged skin takes the target position and the
     *  group's store order updates (category card list, or registry order). Landing
     *  back on the origin slot is a no-op. */
    private fun commitReorder() {
        val drag = dragState as? DragState.Reorder ?: return
        val target = reorderTarget
        val base = Math.round(wheelPos)
        val atRest = target != null && Math.abs(wheelPos - base) < REST_EPSILON &&
            Math.floorMod(base, wheelCount) == target.first
        if (target == null || !atRest) {
            cancelReorder(animated = true)
            return
        }
        val position = (target.first - drag.firstWheel) * WheelGroupLayout.SLOTS + target.second
        val moved = moveDraggedSkin(drag, position)
        dragState = DragState.Idle
        layout.end()
        reorderTarget = null
        if (moved) {
            minecraft.player?.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 1.0f)
            refreshWheels()
        }
    }

    /** Applies the order change for the drag's group. The hole travels to the landing
     *  position, so the dragged skin inserts before the skin at the next position —
     *  or after the skin at the previous one when landing at the window's end. */
    private fun moveDraggedSkin(drag: DragState.Reorder, position: Int): Boolean {
        val draggedId = drag.skins[drag.flatIndex].skinId
        if (draggedId.isEmpty() || position == drag.flatIndex) return false
        return when (val owner = drag.owner) {
            is WheelOwner.Category ->
                if (position + 1 < layout.displaySize) {
                    val pivotId = drag.skins[layout.originAt(position + 1)].skinId
                    !pivotId.isEmpty() && SkinCategories.moveCardBefore(owner.category, draggedId, pivotId)
                } else {
                    val pivotId = drag.skins[layout.originAt(position - 1)].skinId
                    !pivotId.isEmpty() && SkinCategories.moveCardAfter(owner.category, draggedId, pivotId)
                }

            WheelOwner.AllSkins ->
                if (position + 1 < layout.displaySize) {
                    val pivotId = drag.skins[layout.originAt(position + 1)].skinId
                    !pivotId.isEmpty() && SkinRecords.moveBefore(draggedId, pivotId)
                } else {
                    val pivotId = drag.skins[layout.originAt(position - 1)].skinId
                    !pivotId.isEmpty() && SkinRecords.moveAfter(draggedId, pivotId)
                }
        }
    }

    private fun applyAt(wheel: Int, slot: Int) {
        val base = Math.round(wheelPos)
        val atRest = Math.abs(wheelPos - base) < REST_EPSILON
        if (!atRest || Math.floorMod(base, wheelCount) != wheel) return
        val entries = wheels[wheel]
        if (slot >= entries.size) return
        val entry = entries[slot]

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

    // -------------------------------------------------------------------------
    // Composition
    // -------------------------------------------------------------------------

    private data class WheelBuild(val wheels: List<List<SkinEntry>>, val owners: List<WheelOwner>)

    /** Builds the wheel sequence from wheel groups: in ALWAYS the all-skins group
     *  comes first — truncated to the configured wheel cap when category wheels
     *  exist, unbounded otherwise (nothing to bury) — and the allocated categories
     *  follow in category order; in FALLBACK the all-skins group substitutes when
     *  no category contributes any wheel. */
    private fun buildWheels(): WheelBuild {
        val list = ArrayList<List<SkinEntry>>()
        val owners = ArrayList<WheelOwner>()

        fun addGroup(ids: List<String>, owner: WheelOwner) {
            for (chunk in ids.chunked(WHEEL_SIZE)) {
                val resolved = chunk.mapNotNull { byId[it] }
                if (resolved.isNotEmpty()) {
                    list.add(resolved)
                    owners.add(owner)
                }
            }
        }

        val config = SimpleSkinSwapperConfig.get()
        val mode = config.allSkinsWheel()

        // Category wheels resolve first: the cap only binds when they exist.
        val categoryWheels = ArrayList<List<SkinEntry>>()
        val categoryOwners = ArrayList<WheelOwner>()
        for ((category, ids) in SkinCategories.wheelComposition()) {
            for (chunk in ids.chunked(WHEEL_SIZE)) {
                val resolved = chunk.mapNotNull { byId[it] }
                if (resolved.isNotEmpty()) {
                    categoryWheels.add(resolved)
                    categoryOwners.add(WheelOwner.Category(category))
                }
            }
        }
        val hasCategoryWheels = categoryWheels.isNotEmpty()

        val registryIds = SkinRecords.all().map { it.id }
        if (mode == AllSkinsWheelMode.ALWAYS) {
            val ids = if (hasCategoryWheels) registryIds.take(config.maxAllSkinsWheels * WHEEL_SIZE) else registryIds
            addGroup(ids, WheelOwner.AllSkins)
        }
        list.addAll(categoryWheels)
        owners.addAll(categoryOwners)
        if (mode == AllSkinsWheelMode.FALLBACK && list.isEmpty()) {
            addGroup(registryIds, WheelOwner.AllSkins)
        }
        return WheelBuild(list, owners)
    }

    /** Rebuilds the composition after a commit — membership never changes, so the
     *  wheel count is stable and the position stays valid. */
    private fun refreshWheels() {
        val build = buildWheels()
        wheels = build.wheels
        wheelOwners = build.owners
    }

    private fun firstWheelOf(owner: WheelOwner): Int = wheelOwners.indexOf(owner)

    private fun ownerLabel(owner: WheelOwner): Component = when (owner) {
        is WheelOwner.Category -> Component.nullToEmpty(owner.category.name)
        WheelOwner.AllSkins -> Component.translatable("simpleskinswapper.screen.library.all_skins")
    }

    private fun ownerColor(owner: WheelOwner): Int = when (owner) {
        is WheelOwner.Category -> SkinCategoryPalette.colorOf(owner.category.dye)
        WheelOwner.AllSkins -> COLOR_ALL_SKINS_DOT
    }

    private fun drawTooltip(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, message: Component) {
        val w = font.width(message) + 8
        val h = font.lineHeight + 4
        var x = mouseX + 8
        var y = mouseY - h - 2
        if (x + w > this.width) x = this.width - w
        if (y < 0) y = mouseY + 12
        context.fill(x, y, x + w, y + h, 0xF0100018.toInt())
        context.fill(x, y, x + w, y + 1, 0xFF505068.toInt())
        context.text(client.font, Component.nullToEmpty(message.string), x + 4, y + 2, 0xFFFFFFFF.toInt())
    }

    companion object {
        private const val WHEEL_SIZE = 10
        private const val OUTER_RADIUS = 90.0f

        // Every wheel renders at least this many sectors (dimmed filler beyond real skins).
        private const val MIN_WHEEL_SLOTS = 5

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

        // Pagination dot hit radius in px (dots are drawn 2-2.5 px radius).
        private const val DOT_HIT_RADIUS = 6.0

        // Elastic press-drag: the slice stretches along its own axis with resistance,
        // following only the outward (away-from-center) component of the movement.
        private const val ELASTIC_DAMP = 0.45F
        private const val ELASTIC_MAX = 26.0F

        // Outward pull (px) that engages reorder mode; the slice then rides the target
        // slot this far outside the rim.
        private const val PULL_DISTANCE = 18.0F
        private const val SECTOR_PULL = 14.0F

        // Duration of the ease-back after a cancel.
        private const val CANCEL_EASE_NANOS = 180_000_000L

        // Session-scoped last active wheel, restored on open when rememberWheelPosition is enabled.
        private var lastWheelPosition = 0

        private val COLOR_SECTOR = 0xCC1A2535.toInt()
        private val COLOR_SECTOR_HOVER = 0xEE2B5F9E.toInt()
        private val COLOR_CENTER_BG = 0xBB0D1627.toInt()

        /** Dimmed filler sectors padding a sparse wheel up to [MIN_WHEEL_SLOTS]. */
        private val COLOR_SECTOR_EMPTY = 0x66101A2B.toInt()
        private val COLOR_TEXT = 0xFFFFFFFF.toInt()
        private val COLOR_PAGINATION_DIM = 0x60FFFFFF.toInt()

        /** Neutral all-skins pagination dot — deliberately not a dye color. */
        private val COLOR_ALL_SKINS_DOT = 0xFFB0B8C0.toInt()

        /** Hovered-skin subtitle under the group title — same muted tone as the
         *  library panels' labels. */
        private val COLOR_SUBTITLE = 0xFFB0B8C0.toInt()
    }
}
