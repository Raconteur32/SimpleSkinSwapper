package fr.raconteur.simpleskinswapper.gui

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.min

/**
 * Pure radial reflow layout for a wheel-group reorder drag — no Minecraft imports,
 * plain JVM testable. The group's skins form one flat list chunked into wheels of
 * [SLOTS]. Pulling a skin out opens a hole at its slot; the hole follows [setTarget],
 * and only the skins between the origin and the target shift by one (eased, snapping
 * when the shift crosses into another wheel) — skins outside that span never move,
 * so wheels beyond the one being manipulated stay still. Drop targets resolve to the
 * slot nearest the pointer angle; landing on the hole itself is a no-op.
 */
class WheelGroupLayout {

    private var size = 0
    private var dragFlatIndex = -1
    private var targetDisplay = -1
    private var eased = FloatArray(0)

    /** True between [begin] and [end]. */
    val active: Boolean get() = dragFlatIndex >= 0

    /** Positions shown across the whole group while the drag is active (hole included). */
    val displaySize: Int get() = if (active) size else 0

    /**
     * Starts a drag: [groupSize] skins in the group, the dragged one sitting at
     * [dragFlatIndex] in the group's flat list (wheel * [SLOTS] + slot coordinates).
     * The hole starts at the origin slot.
     */
    fun begin(groupSize: Int, dragFlatIndex: Int) {
        this.size = groupSize
        this.dragFlatIndex = dragFlatIndex
        targetDisplay = dragFlatIndex
        eased = FloatArray(groupSize) { it.toFloat() }
    }

    /** Ends the drag; the layout forgets everything. */
    fun end() {
        dragFlatIndex = -1
        size = 0
        targetDisplay = -1
        eased = FloatArray(0)
    }

    /** Moves the insertion hole to [displayIndex]. */
    fun setTarget(displayIndex: Int) {
        targetDisplay = displayIndex.coerceIn(0, size - 1)
    }

    /**
     * Original flat index of the skin shown at display position [p]; the hole position
     * maps to [dragFlatIndex] itself (callers treat that as "no skin here").
     */
    fun originAt(position: Int): Int = when {
        position == targetDisplay -> dragFlatIndex
        targetDisplay < dragFlatIndex && position in (targetDisplay + 1)..dragFlatIndex -> position - 1
        targetDisplay > dragFlatIndex && position in dragFlatIndex until targetDisplay -> position + 1
        else -> position
    }

    /**
     * Landing slot of [wheel] whose mid angle is nearest to [angle]; null when the
     * wheel lies past the group's window. Every position inside the window is a
     * landing — including the hole (a no-op) — while the padding fillers beyond the
     * window are never targets.
     */
    fun nearestLandingSlot(wheel: Int, angle: Double, sectorCount: Int): Int? {
        val first = wheel * SLOTS
        if (first >= size) return null
        val count = min(size - first, sectorCount)
        var best = 0
        var bestDistance = Double.MAX_VALUE
        for (slot in 0..<count) {
            val distance = angularDistance(angle, midAngle(slot.toDouble(), sectorCount))
            if (distance < bestDistance) {
                bestDistance = distance
                best = slot
            }
        }
        return best
    }

    /** Per-frame easing: every displayed skin drifts toward its gap-shifted home. */
    fun update(dt: Float) {
        if (!active) return
        val t = 1f - exp(-REFLOW_SPEED * dt.toDouble()).toFloat()
        for (o in eased.indices) {
            if (o == dragFlatIndex) continue
            val home = homeAt(o).toFloat()
            val value = eased[o] + (home - eased[o]) * t
            eased[o] = when {
                wheelOf(value) != wheelOf(home) -> home
                abs(home - value) < SNAP_EPSILON -> home
                else -> value
            }
        }
    }

    /** Current rendered position (group display coordinates, fractional) of a skin. */
    fun easedPosition(originIndex: Int): Float = eased[originIndex]

    /**
     * Original flat insert index that lands the dragged skin at display position
     * [position] — the skins between the origin and the target shift behind it.
     */
    fun insertFlatIndex(position: Int): Int = position + if (position > dragFlatIndex) 1 else 0

    /** Home position of a skin: between the hole and the origin, it shifts one slot
     *  toward the hole; outside that span it stays put. */
    private fun homeAt(originIndex: Int): Int = when {
        targetDisplay < dragFlatIndex && originIndex in targetDisplay until dragFlatIndex -> originIndex + 1
        targetDisplay > dragFlatIndex && originIndex in (dragFlatIndex + 1)..targetDisplay -> originIndex - 1
        else -> originIndex
    }

    companion object {

        /** Skins per wheel — the flat coordinate space is `wheel * SLOTS + slot`. */
        const val SLOTS = 10

        private const val REFLOW_SPEED = 14.0f
        private const val SNAP_EPSILON = 0.01f

        /** Angle span of one sector on a wheel rendered with [sectorCount] sectors. */
        fun sectorSize(sectorCount: Int): Double = 2 * PI / sectorCount

        /** Mid angle of [slot] (fractional allowed) on a wheel with [sectorCount] sectors. */
        fun midAngle(slot: Double, sectorCount: Int): Double = -PI / 2 + sectorSize(sectorCount) * slot

        /** Start edge angle of [slot] (fractional allowed) on a wheel with [sectorCount] sectors. */
        fun startAngle(slot: Double, sectorCount: Int): Double = midAngle(slot, sectorCount) - sectorSize(sectorCount) / 2

        /** Sector index under [angle] on a wheel with [sectorCount] sectors. */
        fun slotForAngle(angle: Double, sectorCount: Int): Int {
            val shifted = (angle + PI / 2 + sectorSize(sectorCount) / 2) % (2 * PI) + 2 * PI
            return (shifted % (2 * PI) / sectorSize(sectorCount)).toInt()
        }

        /** Wheel index of a flat position (fractional allowed). */
        fun wheelOf(position: Float): Int = floor(position / SLOTS).toInt()

        private fun angularDistance(a: Double, b: Double): Double {
            val diff = (a - b) % (2 * PI)
            val wrapped = if (diff < 0) diff + 2 * PI else diff
            return if (wrapped > PI) 2 * PI - wrapped else wrapped
        }
    }
}
