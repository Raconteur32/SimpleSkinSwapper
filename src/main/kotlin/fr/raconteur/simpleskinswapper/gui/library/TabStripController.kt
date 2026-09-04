package fr.raconteur.simpleskinswapper.gui.library

import net.minecraft.util.Mth

/**
 * Tab strip state machine: scroll, drag-to-reorder with insertion gap and edge auto-scroll.
 * Selection is applied by the screen via [Release] — the controller owns no selection state.
 * Tab indexes are 0 = All, i>0 = category i-1 (same convention everywhere).
 */
class   TabStripController(
    internal val stripTop: () -> Int,
    internal val stripBottom: () -> Int,
    private val tabH: () -> Int,
) {

    // Press/drag state (-1 = none, 0 = All, >0 = category index + 1).
    internal var tabDragCategoryIndex = -1
        private set
    internal var tabDragActive = false
        private set
    internal var tabDragCursorY = 0
        private set
    internal var tabInsertionIndex = -1
        private set

    private var tabDragStartY = 0.0
    private var tabAutoScrollNanos = 0L
    internal var tabScroll = 0.0F
        private set

    /** Tab under the cursor during a press, or -1. Starts every press. */
    fun press(tabIndex: Int, clickY: Double, cursorY: Int) {
        tabDragCategoryIndex = tabIndex
        tabDragActive = false
        tabDragStartY = clickY
        tabDragCursorY = cursorY
    }

    /** Tracks an in-progress drag; false when no drag is in progress for this button. */
    fun drag(tabIndex: Int, isLeftButton: Boolean, cursorY: Int): Boolean {
        if (tabIndex <= 0 || !isLeftButton) return false
        if (!tabDragActive && Math.abs(cursorY - tabDragStartY) > TAB_DRAG_THRESHOLD) {
            tabDragActive = true
        }
        if (tabDragActive) {
            tabDragCursorY = cursorY
            updateTabInsertion()
            return true
        }
        return false
    }

    /** Releases a press/drag: reorder, select or nothing. */
    fun release(isLeftButton: Boolean): Release {
        if (tabDragCategoryIndex < 0 || !isLeftButton) return Release.None
        val wasActive = tabDragActive
        val tabIndex = tabDragCategoryIndex
        val insertion = tabInsertionIndex
        tabDragCategoryIndex = -1
        tabDragActive = false
        tabAutoScrollNanos = 0L
        tabInsertionIndex = -1
        if (wasActive && tabIndex > 0 && insertion >= 0) {
            // Convert the pre-removal insertion point to moveCategory's post-removal target.
            val from = tabIndex - 1
            val to = (if (from < insertion) insertion - 1 else insertion)
                .coerceIn(0, SkinCategoriesStore.all().size - 1)
            return if (to != from) Release.Move(from, to) else Release.None
        }
        if (!wasActive) {
            // Click without movement: select.
            return Release.Select(tabIndex)
        }
        return Release.None
    }

    fun scrollBy(amount: Float) {
        tabScroll = Mth.clamp(tabScroll - amount, 0.0F, maxTabScroll().toFloat())
    }

    /** Y of tab [index]: 0 = All, i>0 = category i-1. All tabs scroll alike; the insertion gap shifts later tabs. */
    internal fun tabY(index: Int): Int {
        var y = stripTop() + index * tabH()
        // The insertion gap opens after category [tabInsertionIndex], shifting later tabs down.
        if (tabDragActive && tabInsertionIndex >= 0 && index >= tabInsertionIndex + 1) y += tabH()
        return y - tabScroll.toInt()
    }

    internal fun maxTabScroll(): Int {
        // Whole-tab scroll steps: the range is a multiple of TAB_H, so no tab is ever
        // caught half-hidden at the scroll limit (the strip shows whole tabs only).
        val visibleTabs = Math.max(1, (stripBottom() - stripTop()) / tabH())
        return Math.max(0, SkinCategoriesStore.all().size + 2 - visibleTabs) * tabH()
    }

    /** Bottom edge aligned to whole tabs: tabs clip here, never half-shown. The strip
     *  zone below this line is frame-only background (breathing room, no tab pixels). */
    internal fun stripAlignedBottom(): Int =
        stripTop() + Math.max(1, (stripBottom() - stripTop()) / tabH()) * tabH()

    /** Y of the add-category entry: the strip slot after the last category tab. */
    internal fun addEntryY(): Int = tabY(SkinCategoriesStore.all().size + 1)

    /** True when the cursor sits on the add-category entry slot at the end of the strip. */
    internal fun addEntryAt(cursorY: Int, cursorX: Int): Boolean {
        if (cursorX < SkinLibraryScreen.STRIP_X || cursorX > SkinLibraryScreen.STRIP_X + SkinLibraryScreen.TAB_W + 4) return false
        if (cursorY < stripTop() || cursorY >= stripAlignedBottom()) return false
        val top = addEntryY()
        return cursorY >= top && cursorY < top + tabH()
    }

    /** Tab under the cursor, accounting for the insertion gap; null when none. 0 = All, i>0 = category i-1. */
    internal fun tabAt(cursorY: Int, cursorX: Int): Int? {
        if (cursorX < SkinLibraryScreen.STRIP_X || cursorX > SkinLibraryScreen.STRIP_X + SkinLibraryScreen.TAB_W + 4) return null
        if (cursorY < stripTop() || cursorY >= stripAlignedBottom()) return null
        for (i in 0..SkinCategoriesStore.all().size) {
            val top = tabY(i)
            if (cursorY >= top && cursorY < top + tabH()) return i
        }
        return null
    }

    /** Edge auto-scroll while a tab drag is active; called every frame with the mouse Y. */
    internal fun updateTabAutoScroll(mouseY: Int) {
        if (!tabDragActive) {
            tabAutoScrollNanos = 0L
            return
        }
        val now = System.nanoTime()
        val dt = if (tabAutoScrollNanos == 0L) 0.0F else (now - tabAutoScrollNanos) / 1_000_000_000.0F
        tabAutoScrollNanos = now
        if (dt == 0.0F) return

        val top = stripTop()
        val bottom = stripBottom()
        var speed = 0.0F
        if (mouseY > bottom - AUTO_SCROLL_BAND && mouseY <= bottom + tabH()) {
            speed = -MAX_TABS_PER_SEC * (1.0F - (bottom - mouseY) / AUTO_SCROLL_BAND.toFloat())
        } else if (mouseY < top + tabH() + AUTO_SCROLL_BAND && mouseY >= top && tabScroll > 0.0F) {
            speed = MAX_TABS_PER_SEC * (1.0F - (mouseY - top - tabH()) / AUTO_SCROLL_BAND.toFloat())
        }
        if (speed != 0.0F) {
            tabScroll = Mth.clamp(tabScroll + speed * tabH() * dt, 0.0F, maxTabScroll().toFloat())
        }
    }

    /** Insertion point p in [0..count]: the gap sits after p categories (pre-removal space). */
    private fun updateTabInsertion() {
        val count = SkinCategoriesStore.all().size
        var p = count
        for (storeIdx in 0 until count) {
            val yTop = stripTop() + (storeIdx + 1) * tabH() - tabScroll.toInt()
            if (tabDragCursorY < yTop + tabH() / 2) {
                p = storeIdx
                break
            }
        }
        tabInsertionIndex = p.coerceIn(0, count)
    }

    /** Outcome of releasing a tab press/drag. */
    sealed class Release {
        /** Click without movement on tab index (0 = All). */
        data class Select(val tabIndex: Int) : Release()

        /** Move category [from] to slot [to] (post-removal indexes). */
        data class Move(val from: Int, val to: Int) : Release()

        data object None : Release()
    }

    internal fun insertionLineY(): Int = stripTop() + tabH() + tabInsertionIndex * tabH() - tabScroll.toInt()

    private companion object {
        const val TAB_DRAG_THRESHOLD = 5.0
        const val AUTO_SCROLL_BAND = 16
        const val MAX_TABS_PER_SEC = 2.0F
    }
}
