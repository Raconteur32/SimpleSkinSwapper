package fr.raconteur.simpleskinswapper.gui.library

import fr.raconteur.simpleskinswapper.config.SimpleSkinSwapperConfig
import fr.raconteur.simpleskinswapper.gui.library.SkinLibraryScreen.Companion.PAD
import fr.raconteur.simpleskinswapper.gui.library.SkinLibraryScreen.Companion.PAGE_BORDER
import java.util.IdentityHashMap
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.util.Mth
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Grid geometry and card placement for [SkinLibraryScreen]: viewport and cell metrics,
 * scroll bounds, the per-frame position/easing pass (cards and the trailing "+" card,
 * clipped by the shared page-inner scissor rect) and the card-reorder drag bookkeeping.
 * The screen owns the widget tree and the drop semantics ([SkinLibraryScreen.finishCardReorder]
 * stays there: a tab drop is a category business decision); the engine owns the mechanics.
 */
internal class GridEngine(
    private val screen: SkinLibraryScreen,
    private val cards: () -> List<SkinLibraryCard>,
    private val addCard: () -> SkinAddCard?,
    private val visibleCategorySlots: () -> Int,
) {

    // Grid scroll + layout (recomputed in recomputeLayout()).
    var scrollY = 0
    var cols = 3
        private set
    var cellW = 0
        private set
    var cellH = 0
        private set
    var gridOffsetX = 0
        private set
    var gridTop = 0
        private set
    var gridBottom = 0
        private set
    var maxScroll = 0
        private set

    // Card reorder drag (the whole card body is the grab; started once the press moves).
    var reorderDraggingCard: SkinLibraryCard? = null
        private set

    // Raised by a card during mouseDragged's children iteration; consumed (drag begins,
    // card unregisters) right after the iteration — same deferred pattern as removeWidget.
    private var pendingReorderStart: Pair<SkinLibraryCard, Pair<Int, Int>>? = null

    private var lastCardEaseNanos = 0L
    private val cardDisplay = IdentityHashMap<SkinLibraryCard, FloatArray>()
    private val addCardDisplay = IdentityHashMap<SkinAddCard, FloatArray>()

    val cardDrag = CardDragEngine(
        cols = { cols },
        cellW = { cellW },
        cellH = { cellH },
        gridOffsetX = { gridOffsetX },
        gridGap = { GRID_GAP },
        contentStartY = { contentStartY() },
        scrollY = { scrollY },
        gridTop = { gridTop },
        gridBottom = { gridBottom },
    )

    internal fun recomputeLayout() {
        // Constant viewport, whatever sits inside it (import row above, config band inside
        // the page) — switching category or expanding the band never moves the layout.
        gridTop = screen.contentTop() + BAND_GRID_MARGIN
        gridBottom = screen.height - SkinLibraryScreen.FOOTER_BAND
        // Tab strip zone = the strip's own recessed footprint ([gridTop, gridBottom] —
        // 8px inside the card page so the strip reads as behind the page). The band tiles
        // that zone with whole slots — density ~28px, or the actual slot count when the
        // list is shorter — and centers the rounding remainder instead of showing it.
        val zoneTop = gridTop
        val zoneHeight = gridBottom - zoneTop
        // Ghost placeholders pad the strip to a minimum of five slots (All + Uncategorized
        // + three category slots), so the strip never collapses with no categories.
        val slots = visibleCategorySlots() + 3
        val densitySlots = 1.coerceAtLeast((zoneHeight / 28f).roundToInt())
        val fillSlots = slots.coerceAtMost(densitySlots)
        screen.tabH = (zoneHeight + SkinLibraryScreen.TAB_OVERLAP * (fillSlots - 1)) / fillSlots
        val bandH = (fillSlots - 1) * (screen.tabH - SkinLibraryScreen.TAB_OVERLAP) + screen.tabH
        screen.stripZoneTop = zoneTop + (zoneHeight - bandH) / 2
        screen.stripZoneBottom = screen.stripZoneTop + bandH
        // The grid lives inside the page's baked border (8px, measured on the texture)
        // plus a small breathing margin on every side — cards never touch the border.
        val left = gridLeft()
        val right = gridRight()
        val gridW = right - left
        val gap = GRID_GAP
        // Minimum card width is user-configurable: raised, fewer columns fit and every
        // card widens (then keeps the 4:3 height ratio below).
        val minCellW = SimpleSkinSwapperConfig.get().minCardWidth.toDouble()
        cols = ((gridW - gap) / (minCellW + gap)).toInt().coerceIn(3, MAX_COLS)
        cellW = (gridW - gap * (cols - 1)) / cols
        val viewH = gridBottom - gridTop
        cellH = (cellW * 4 / 3).coerceAtMost(viewH - GRID_MARGIN * 2).coerceAtLeast(MIN_CELL_H)
        val totalW = cols * cellW + gap * (cols - 1)
        gridOffsetX = left + (gridW - totalW) / 2
        updateMaxScroll()
    }

    internal fun updateMaxScroll() {
        // The config band scrolls away with the content, so it counts toward it.
        // The trailing "+" card occupies one extra cell after the last skin.
        val bandH = if (screen.selectedCategory != null) screen.band.height(true) + GRID_GAP else 0
        val rows = ceil((cards().size + 1) / cols.toDouble()).toInt()
        val contentH = bandH + rows * (cellH + GRID_GAP) - GRID_GAP
        maxScroll = 0.coerceAtLeast(contentH - (gridBottom - gridTop - GRID_MARGIN * 2))
    }

    /** Card-area inner edges: the page's baked border plus the grid margin. The config band uses them too. */
    internal fun gridLeft(): Int = screen.panelX - 6 + PAGE_BORDER + GRID_MARGIN

    internal fun gridRight(): Int = screen.width - PAD - PAGE_BORDER - GRID_MARGIN

    /** Unscrolled Y where the first grid row sits (right under the band when it is shown). */
    internal fun contentStartY(): Int =
        gridTop + GRID_MARGIN + (if (screen.selectedCategory != null) screen.band.height(screen.selectedCategory != null) + GRID_GAP else 0)

    /** Clears the per-widget easing state; called when the widget tree is rebuilt. */
    internal fun clearPlacementState() {
        cardDisplay.clear()
        addCardDisplay.clear()
    }

    internal fun clampScroll() {
        scrollY = Mth.clamp(scrollY, 0, maxScroll)
    }

    /** One wheel notch = one cell step, clamped to the scroll bounds. */
    internal fun scrollBy(vertAmount: Double) {
        scrollY = Mth.clamp(scrollY - (vertAmount * (cellH + GRID_GAP)).toInt(), 0, maxScroll)
    }

    internal fun updateCardPositions(mouseX: Int, mouseY: Int) {
        val dragged = reorderDraggingCard
        // Every view is reorderable now: category views move within their card list, the
        // derived views (All skins, Uncategorized) reorder the registry order itself.
        // cards() is already the displayed (derived) list, so the insertion index and the
        // clamp below are relative to what is on screen.
        val dragIndex = dragged?.let { cards().indexOf(it) } ?: -1
        if (dragIndex >= 0) cardDrag.updateInsertionIndex(cards().size, mouseX, mouseY)

        val now = System.nanoTime()
        val dt = if (lastCardEaseNanos == 0L) 1.0F else ((now - lastCardEaseNanos) / 1_000_000_000.0F).coerceAtMost(0.1F)
        val t = 1.0F - exp((-CARD_SLIDE_SPEED * dt).toDouble()).toFloat()

        for (i in cards().indices) {
            val card = cards()[i]
            val slot = cardDrag.slotFor(i, dragIndex)
            if (card === dragged) {
                card.overridePosition(cardDrag.cursorX - cardDrag.grabX, cardDrag.cursorY - cardDrag.grabY)
                continue
            }
            easeWidgetToSlot(card, card.x == 0 && card.y == 0, slot, t, cardDisplay.getOrPut(card) { FloatArray(2) })
        }

        updateAddCardPosition(t)
        lastCardEaseNanos = now
    }

    /**
     * Every grid widget renders through the same fixed viewport scissor: widgets sliding in
     * and out are smoothly half-clipped by the page border instead of popping.
     */
    private fun easeWidgetToSlot(widget: GridSlottedWidget, unpositioned: Boolean, slot: Pair<Int, Int>, t: Float, display: FloatArray) {
        widget.clipLeft = screen.panelX - 6 + PAGE_BORDER
        widget.clipTop = gridTop
        widget.clipRight = screen.width - PAD - PAGE_BORDER
        widget.clipBottom = gridBottom
        val (ex, ey) = cardDrag.easeToward(display, slot, t, unpositioned)
        widget.overridePosition(ex, ey)
    }

    /**
     * The trailing "+" card slides like a card but never participates in the reorder:
     * a neutral drag index (-1) keeps it pinned to the slot right after the last skin,
     * with no dragged shift and no insertion-gap shift.
     */
    private fun updateAddCardPosition(t: Float) {
        val ac = addCard() ?: return
        easeWidgetToSlot(ac, ac.x == 0 && ac.y == 0, cardDrag.slotFor(cards().size, -1), t, addCardDisplay.getOrPut(ac) { FloatArray(2) })
    }

    internal fun requestCardReorder(card: SkinLibraryCard, mouseX: Int, mouseY: Int) {
        pendingReorderStart = card to (mouseX to mouseY)
    }

    internal fun beginCardReorder(card: SkinLibraryCard, mouseX: Int, mouseY: Int) {
        cardDrag.begin(card, mouseX, mouseY)
        reorderDraggingCard = card
    }

    /** Consumes a deferred reorder start (see the screen's mouseDragged); returns the card
     *  to unregister from the widget tree, or null when no reorder was pending. */
    internal fun beginPendingReorder(): SkinLibraryCard? {
        val (card, pos) = pendingReorderStart ?: return null
        pendingReorderStart = null
        beginCardReorder(card, pos.first, pos.second)
        return card
    }

    internal fun dragReorderTo(mx: Int, my: Int) {
        cardDrag.dragTo(mx, my)
    }

    internal fun clearReorderDrag() {
        reorderDraggingCard = null
    }

    /** Category switch / view reset: back to the top of the list. */
    internal fun resetScroll() {
        scrollY = 0
    }

    companion object {
        // GRID_GAP: spacing between grid cards, horizontally and vertically.
        private const val GRID_GAP = 6
        private const val MAX_COLS = 10
        private const val GRID_MARGIN = 4
        private const val MIN_CELL_H = 56
        private const val BAND_GRID_MARGIN = 6
        private const val CARD_SLIDE_SPEED = 14.0F
    }
}
