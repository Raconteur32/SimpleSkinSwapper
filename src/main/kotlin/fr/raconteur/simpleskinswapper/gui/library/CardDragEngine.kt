package fr.raconteur.simpleskinswapper.gui.library

import net.minecraft.util.Mth

/**
 * Card reorder drag state machine and grid math: grab offsets, cursor tracking, insertion
 * index (reading order refined by hovered cell half), slot layout with the insertion gap,
 * and the exponential ease each card uses to slide toward its slot.
 */
internal class CardDragEngine(
    private val cols: () -> Int,
    private val cellW: () -> Int,
    private val cellH: () -> Int,
    private val gridOffsetX: () -> Int,
    private val gridGap: () -> Int,
    private val contentStartY: () -> Int,
    private val scrollY: () -> Int,
    private val gridTop: () -> Int,
    private val gridBottom: () -> Int,
) {

    internal var draggingCard: SkinLibraryCard? = null
    internal var grabX = 0
    internal var grabY = 0
    internal var cursorX = 0
    internal var cursorY = 0
    internal var insertionIndex = -1
        private set

    fun begin(card: SkinLibraryCard, mouseX: Int, mouseY: Int) {
        draggingCard = card
        grabX = mouseX - card.x
        grabY = mouseY - card.y
        cursorX = mouseX
        cursorY = mouseY
        // Unregistration is deferred to the end of mouseClicked: removing a child widget while
        // the screen is still iterating its children would risk a ConcurrentModificationException.
    }

    fun dragTo(mouseX: Int, mouseY: Int) {
        cursorX = mouseX
        cursorY = mouseY
    }

    fun stop() {
        draggingCard = null
    }

    fun isDragging(): Boolean = draggingCard != null

    /** Recomputes the insertion index for the current drag cursor (or resets it to -1). */
    fun updateInsertionIndex(cardCount: Int, mouseX: Int, mouseY: Int) {
        insertionIndex = if (draggingCard == null) -1 else insertionIndexAt(cardCount, mouseX, mouseY)
    }

    /** Slot (x, y) for card [index], skipping the dragged card's slot and reserving the insertion gap. */
    fun slotFor(index: Int, dragIndex: Int): Pair<Int, Int> {
        var displayIndex = index
        if (dragIndex >= 0 && index > dragIndex) displayIndex--
        if (dragIndex >= 0 && insertionIndex >= 0 && displayIndex >= insertionIndex) displayIndex++
        val col = displayIndex % cols()
        val row = displayIndex / cols()
        return (gridOffsetX() + col * (cellW() + gridGap())) to
            (contentStartY() - scrollY() + row * (cellH() + gridGap()))
    }

    /** Eases [display] toward [slot]; returns the rounded on-screen position.
     *  [unpositioned] marks a display array never placed yet (card still at origin). */
    fun easeToward(display: FloatArray, slot: Pair<Int, Int>, t: Float, unpositioned: Boolean): Pair<Int, Int> {
        if (unpositioned) {
            display[0] = slot.first.toFloat()
            display[1] = slot.second.toFloat()
        }
        display[0] = Mth.lerp(t, display[0], slot.first.toFloat())
        display[1] = Mth.lerp(t, display[1], slot.second.toFloat())
        if (Math.abs(display[0] - slot.first) < 0.5F && Math.abs(display[1] - slot.second) < 0.5F) {
            display[0] = slot.first.toFloat()
            display[1] = slot.second.toFloat()
        }
        return Math.round(display[0]) to Math.round(display[1])
    }

    /** Insertion index from the cursor in reading order, refined by which half of the cell is hovered. */
    private fun insertionIndexAt(cardCount: Int, mouseX: Int, mouseY: Int): Int {
        if (mouseX < gridOffsetX() || mouseY < gridTop() || mouseY > gridBottom()) return -1
        val relCol = (mouseX - gridOffsetX()) / (cellW() + gridGap())
        val relRow = (mouseY - contentStartY() + scrollY()) / (cellH() + gridGap())
        if (relCol < 0 || relCol >= cols() || relRow < 0) return -1
        val count = cardCount - if (draggingCard != null) 1 else 0
        var idx = relRow * cols() + relCol
        if (idx > count) idx = count
        val cellLeft = gridOffsetX() + relCol * (cellW() + gridGap())
        if (mouseX > cellLeft + cellW() / 2) idx++
        return idx
    }
}
