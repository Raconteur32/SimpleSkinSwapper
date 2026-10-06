package fr.raconteur.simpleskinswapper.gui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI

/** The radial reorder layout: flat group coordinates, the traveling hole, angle targeting. */
class WheelGroupLayoutTest {

    /** 13 skins on two wheels (10 + 3); the drag holds flat index 2. */
    private fun thirteenDragSecond() = WheelGroupLayout().also { it.begin(13, 2) }

    @Test
    fun `begin opens the hole at the origin slot`() {
        val layout = thirteenDragSecond()
        assertTrue(layout.active)
        assertEquals(13, layout.displaySize)
        // The hole maps to the dragged skin; every other position shows its own skin.
        assertEquals(2, layout.originAt(2))
        assertEquals(0, layout.originAt(0))
        assertEquals(12, layout.originAt(12))
    }

    @Test
    fun `end forgets the drag`() {
        val layout = thirteenDragSecond()
        layout.end()
        assertFalse(layout.active)
        assertEquals(0, layout.displaySize)
    }

    @Test
    fun `insert index lands before the target occupant`() {
        val layout = thirteenDragSecond()
        // Landing past the origin: the dragged skin inserts after the removed one.
        assertEquals(11, layout.insertFlatIndex(10))
        // Landing before the origin: straight insert.
        assertEquals(0, layout.insertFlatIndex(0))
        // Landing back on the origin position: no shift.
        assertEquals(2, layout.insertFlatIndex(2))
    }

    @Test
    fun `the hole shifts only the skins between origin and target`() {
        val layout = thirteenDragSecond()
        layout.setTarget(5)
        layout.update(10f)
        // Skins 3, 4, 5 slide one slot back toward the origin; everything else stays.
        assertEquals(0f, layout.easedPosition(0))
        assertEquals(2f, layout.easedPosition(3))
        assertEquals(3f, layout.easedPosition(4))
        assertEquals(4f, layout.easedPosition(5))
        assertEquals(6f, layout.easedPosition(6))
        assertEquals(12f, layout.easedPosition(12))
    }

    @Test
    fun `targeting before the origin shifts the skins the other way`() {
        val layout = thirteenDragSecond()
        layout.setTarget(0)
        layout.update(10f)
        // Skins 0 and 1 slide one slot out; skin 2's hole is at position 0.
        assertEquals(1f, layout.easedPosition(0))
        assertEquals(2f, layout.easedPosition(1))
        assertEquals(3f, layout.easedPosition(3))
    }

    @Test
    fun `origin at inverts the shifted positions`() {
        val layout = thirteenDragSecond()
        layout.setTarget(5)
        // Position 5 is the hole; 3 and 4 hold the skins that slid back from 4 and 5.
        assertEquals(2, layout.originAt(5))
        assertEquals(4, layout.originAt(3))
        assertEquals(5, layout.originAt(4))
        assertEquals(6, layout.originAt(6))
    }

    @Test
    fun `easing drifts toward the shifted home within a wheel`() {
        val layout = thirteenDragSecond()
        layout.setTarget(5)
        // One small step: skin 5 eases from 5 toward 4 but has not arrived.
        layout.update(0.016f)
        val eased = layout.easedPosition(5)
        assertTrue(eased > 4f && eased < 5f)
        // Settle: it reaches the shifted home.
        layout.update(10f)
        assertEquals(4f, layout.easedPosition(5))
    }

    @Test
    fun `a shift crossing into another wheel snaps instead of sliding`() {
        // 13 skins, drag 11, target 0: skin 9 crosses the wheel boundary (snap) while
        // skin 10 slides within wheel 1 (eases).
        val layout = WheelGroupLayout().also { it.begin(13, 11) }
        layout.setTarget(0)
        layout.update(0.016f)
        assertEquals(10f, layout.easedPosition(9))
        layout.update(10f)
        assertEquals(11f, layout.easedPosition(10))
    }

    @Test
    fun `sparse wheels expose their real slots`() {
        val layout = WheelGroupLayout().also { it.begin(5, 1) }
        // Wheel 1 lies past the group's window; wheel 0 shows all five positions.
        assertNull(layout.nearestLandingSlot(1, 0.0, 5))
        assertEquals(2, layout.nearestLandingSlot(0, WheelGroupLayout.midAngle(2.0, 5), 5))
    }

    @Test
    fun `targeting follows the angle to the nearest landing slot`() {
        val layout = thirteenDragSecond()
        val n = 10
        assertEquals(3, layout.nearestLandingSlot(0, WheelGroupLayout.midAngle(3.0, n), n))
        // Halfway between slots 3 and 4 rounds to the nearer one.
        val half = (WheelGroupLayout.midAngle(3.0, n) + WheelGroupLayout.midAngle(4.0, n)) / 2
        assertEquals(4, layout.nearestLandingSlot(0, half + 0.01, n))
        assertEquals(3, layout.nearestLandingSlot(0, half - 0.01, n))
    }

    @Test
    fun `every position of a full wheel stays a landing including the hole`() {
        // Full wheel of ten: all ten slots accept the drop, the hole itself is a no-op.
        val layout = WheelGroupLayout().also { it.begin(10, 3) }
        val n = 10
        assertEquals(9, layout.nearestLandingSlot(0, WheelGroupLayout.midAngle(9.0, n), n))
    }

    @Test
    fun `padding fillers beyond the group are never targets`() {
        // 4 skins padded to five sectors: slots 0..3 are landings, slot 4 is padding.
        val layout = WheelGroupLayout().also { it.begin(4, 0) }
        val n = 5
        assertEquals(3, layout.nearestLandingSlot(0, WheelGroupLayout.midAngle(3.0, n), n))
        assertEquals(3, layout.nearestLandingSlot(0, WheelGroupLayout.midAngle(3.5, n), n))
        // Past the window the search wraps toward slot 0 — never into the padding.
        assertEquals(0, layout.nearestLandingSlot(0, WheelGroupLayout.midAngle(4.6, n), n))
        // The group's window spans a second wheel with a lone landing position.
        val split = WheelGroupLayout().also { it.begin(11, 0) }
        assertEquals(11, split.displaySize)
        assertEquals(0, split.nearestLandingSlot(1, 0.0, 5))
    }

    @Test
    fun `wheel angles run clockwise from the top`() {
        // Slot 0 points up, a quarter wheel of 4 points right.
        assertEquals(-PI / 2, WheelGroupLayout.midAngle(0.0, 4), 1e-9)
        assertEquals(0.0, WheelGroupLayout.midAngle(1.0, 4), 1e-9)
        // The start edge of slot 0 sits half a sector counterclockwise from its mid angle.
        assertEquals(-PI / 2 - PI / 4, WheelGroupLayout.startAngle(0.0, 4), 1e-9)
    }

    @Test
    fun `slot for angle inverts mid angle`() {
        for (count in listOf(5, 7, 10)) {
            for (slot in 0..<count) {
                assertEquals(slot, WheelGroupLayout.slotForAngle(WheelGroupLayout.midAngle(slot.toDouble(), count), count))
            }
        }
    }
}
