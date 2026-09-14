package fr.raconteur.simpleskinswapper.gui.library

import com.mojang.blaze3d.platform.InputConstants
import fr.raconteur.simpleskinswapper.SimpleSkinSwapper
import fr.raconteur.simpleskinswapper.config.SimpleSkinSwapperConfig
import fr.raconteur.simpleskinswapper.gui.EdgeSafeButtonWidget
import fr.raconteur.simpleskinswapper.gui.SkinEntry
import fr.raconteur.simpleskinswapper.gui.SkinType
import fr.raconteur.simpleskinswapper.gui.config.YaclConfigScreen
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.util.Mth
//? if <26.3 {
import net.minecraft.util.Util
//?}
//? if >=26.3 {
/*import com.mojang.blaze3d.Blaze3D
*///?}
import org.lwjgl.system.MemoryStack
//? if >=26.3 {
/*import net.minecraft.client.Minecraft
import org.lwjgl.sdl.SDLDialog
import org.lwjgl.sdl.SDL_DialogFileCallback
import org.lwjgl.sdl.SDL_DialogFileFilter
import org.lwjgl.system.MemoryUtil
*///?} else {
import org.lwjgl.util.tinyfd.TinyFileDialogs
//?}
import java.io.File
import java.io.IOException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.IdentityHashMap
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.roundToInt
import fr.raconteur.simpleskinswapper.library.DeleteAction
import fr.raconteur.simpleskinswapper.library.DeleteDecision
import fr.raconteur.simpleskinswapper.library.DeleteSource
import fr.raconteur.simpleskinswapper.library.LibraryCategory
import fr.raconteur.simpleskinswapper.library.SkinRecord

/**
 * Category-based skin library: a vertical category tab strip on the left (pinned "All skins"
 * tab, scrollable, drag-reorderable with edge auto-scroll) and a responsive, vertically
 * scrolling grid of skin cards on the right, plus a collapsible per-category config band.
 * Replaces the old horizontal carousel screen.
 */
/**
 * A grid widget the screen repositions every frame: dragged along with card easing and
 * clipped to the shared grid viewport (see [SkinLibraryScreen.easeWidgetToSlot]).
 */
internal interface GridSlottedWidget {
    var clipLeft: Int
    var clipTop: Int
    var clipRight: Int
    var clipBottom: Int
    fun overridePosition(newX: Int, newY: Int)
}

class SkinLibraryScreen(private val parent: Screen?) : Screen(Component.translatable("simpleskinswapper.title")) {

    private val client get() = minecraft

    // ------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------

    private val entries = ArrayList<SkinEntry>()
    private val cards = ArrayList<SkinLibraryCard>()

    /** Currently selected view: null category = the pinned All skins view. */
    internal var selectedCategory: LibraryCategory? = null

    /** True when the built-in Uncategorized view is selected ([selectedCategory] is null). */
    internal var uncategorizedSelected = false
    internal val band = CategoryBand(this)

    // Card reorder drag (the whole card body is the grab; started once the press moves).
    internal var reorderDraggingCard: SkinLibraryCard? = null

    // Raised by a card during mouseDragged's children iteration; consumed (drag begins,
    // card unregisters) right after the iteration — same deferred pattern as removeWidget.
    private var pendingReorderStart: Pair<SkinLibraryCard, Pair<Int, Int>>? = null

    internal fun requestCardReorder(card: SkinLibraryCard, mouseX: Int, mouseY: Int) {
        pendingReorderStart = card to (mouseX to mouseY)
    }
    private val cardDrag = CardDragEngine(
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

    // Open skin detail overlay (null = closed). Registered as a widget while open.
    internal var detail: SkinDetailPanel? = null

    // Open add-skin overlay (null = closed), opened from the trailing "+" card.
    internal var addPanel: SkinAddPanel? = null

    // Trailing "+" pseudo-card shown after the last card of every list.
    private var addCard: SkinAddCard? = null
    private val addCardDisplay = IdentityHashMap<SkinAddCard, FloatArray>()
    private val cardDisplay = IdentityHashMap<SkinLibraryCard, FloatArray>()

    // Tab strip scroll/drag/insertion state machine.
    private val tabs = TabStripController({ stripZoneTop }, { stripZoneBottom }, { tabH })

    // Grid scroll + layout (recomputed in recomputeLayout()).
    internal var scrollY = 0
    private var cols = 3
    private var cellW = 0
    private var cellH = 0
    private var gridOffsetX = 0
    internal var gridTop = 0
    private var gridBottom = 0
    /** Dynamic tab height: whole tabs tiling the strip (~28px density, stretched when
     *  the category list is shorter than the strip). */
    internal var tabH = 28
    /** Tab strip display band: whole-slot band centered in the card page's vertical span. */
    private var stripZoneTop = 0
    private var stripZoneBottom = 0
    private var maxScroll = 0

    // Widgets
    private val watcher = LibraryFileWatcher { this.init() }

    // ------------------------------------------------------------------
    // Init / layout
    // ------------------------------------------------------------------

    override fun init() {
        super.init()
        migrateLegacyLibraryIfNeeded()
        reloadView()
        initBandAndFooter()
        rebuildCards()
        recomputeLayout()
        reattachOverlays()
        watcher.stop()
        watcher.start()
    }

    /** Where the detail-panel delete would act from (drives the dynamic dialog). */
    internal fun deleteSource(): DeleteSource = when {
        selectedCategory != null -> DeleteSource.CATEGORY
        uncategorizedSelected -> DeleteSource.UNCATEGORIZED
        else -> DeleteSource.ALL_SKINS
    }

    // The shared destructive-action confirmation, above every panel while open.
    internal var confirmPopup: ConfirmPopup? = null
        private set

    /** Shows the shared confirmation popup; any previous popup is replaced. */
    internal fun openConfirmPopup(popup: ConfirmPopup) {
        confirmPopup = popup
        popup.show()
    }

    internal fun closeConfirmPopup() {
        confirmPopup = null
    }

    /**
     * Builds the delete confirmation for [entry] from the current view: message lines
     * from the DeleteDecision counts, action buttons from its branch mapping. When
     * [panel] is set (delete from the detail overlay) the popup opens over it and a
     * confirm closes the panel instantly without committing its pending edits.
     */
    internal fun openDeletePopup(entry: SkinEntry, panel: SkinDetailPanel?) {
        val source = deleteSource()
        val decision = DeleteDecision.of(source, SkinCategories.categoriesOf(entry.skinId).size)
        // Same context texts the detail panel's info line used, per view.
        val message = when (source) {
            DeleteSource.CATEGORY ->
                if (decision.otherCategories > 0) {
                    Component.translatable("simpleskinswapper.screen.detail.delete_context_category_others", decision.otherCategories)
                } else {
                    Component.translatable("simpleskinswapper.screen.detail.delete_context_category_last")
                }
            DeleteSource.ALL_SKINS ->
                if (decision.totalCategories > 0) {
                    Component.translatable("simpleskinswapper.screen.detail.delete_context_all", decision.totalCategories)
                } else {
                    Component.translatable("simpleskinswapper.screen.detail.delete_context_uncategorized")
                }
            DeleteSource.UNCATEGORIZED ->
                Component.translatable("simpleskinswapper.screen.detail.delete_context_uncategorized")
        }
        val buttons = decision.actions().map { action ->
            ConfirmPopup.PopupButton(
                when (action) {
                    DeleteAction.REMOVE_CARD_HERE -> Component.translatable("simpleskinswapper.screen.detail.remove_card")
                    DeleteAction.DELETE_EVERYWHERE -> Component.translatable("simpleskinswapper.screen.detail.delete")
                }
            ) {
                panel?.close(instant = true)
                when (action) {
                    DeleteAction.REMOVE_CARD_HERE -> removeCardOf(entry)
                    DeleteAction.DELETE_EVERYWHERE -> deleteEntry(entry)
                }
            }
        }
        openConfirmPopup(
            ConfirmPopup(
                this,
                Component.translatable("simpleskinswapper.screen.delete_popup.title"),
                listOf(message),
                buttons
            )
        )
    }

    /** Opens the category delete confirmation on the shared popup component. */
    internal fun openCategoryDeletePopup() {
        openConfirmPopup(
            ConfirmPopup(
                this,
                null,
                listOf(Component.translatable("simpleskinswapper.screen.library.delete_category_question")),
                listOf(
                    ConfirmPopup.PopupButton(
                        Component.translatable("simpleskinswapper.screen.library.delete_category_confirm")
                    ) { confirmCategoryDelete() }
                )
            )
        )
    }

    /** Removes the card for [entry] from the current category; the skin itself stays. */
    internal fun removeCardOf(entry: SkinEntry) {
        selectedCategory?.let { SkinCategories.removeCard(it, entry.skinId) }
        reloadView()
        rebuildCards()
    }

    /** Why switching [entry] to [targetModel] is disabled, or null when allowed. The
     *  sibling check is registry-wide: category views block only when the sibling already
     *  holds a card here; the derived views block on any existing sibling. */
    internal fun switchBlockedReason(entry: SkinEntry, targetModel: SkinType): Component? {
        val record = SkinRecords.findById(entry.skinId)
            ?: return Component.translatable("simpleskinswapper.screen.detail.switch_exists")
        val target = targetModel.mojangVariant
        if (record.model == target) return null
        val sibling = SkinRecords.find(record.textureHash, target) ?: return null
        val category = selectedCategory ?: return Component.translatable("simpleskinswapper.screen.detail.switch_exists")
        return if (category.cards.any { it.skinId == sibling.id }) {
            Component.translatable("simpleskinswapper.screen.detail.switch_in_category")
        } else {
            null
        }
    }

    /** Moves every card of [fromId] over to [toId], keeping per-category custom names. */
    private fun transferCards(fromId: String, toId: String) {
        for (holder in SkinCategories.categoriesOf(fromId)) {
            val customName = holder.cards.firstOrNull { it.skinId == fromId }?.name ?: ""
            SkinCategories.removeCard(holder, fromId)
            SkinCategories.addCard(holder, toId)
            if (customName.isNotBlank()) SkinCategories.setCardName(holder, toId, customName)
        }
    }

    /** Commits the model switch of [entry] to [targetModel] (the panel previews it first;
     *  this runs when the panel closes). The sibling skin is created on the fly and
     *  REPLACES the original: a category view swaps the current card (custom name kept),
     *  a derived view makes the sibling inherit every card; the original is deleted when
     *  nothing references it anymore. */
    internal fun switchModel(entry: SkinEntry, targetModel: SkinType): SkinEntry? {
        if (switchBlockedReason(entry, targetModel) != null) return null
        val record = SkinRecords.findById(entry.skinId) ?: return null
        val target = targetModel.mojangVariant
        if (record.model == target) return entry
        val sibling = SkinRecords.find(record.textureHash, target)
            ?: SkinRecords.create(record.textureHash, target, record.name, record.file)
            ?: return null
        val category = selectedCategory
        if (category != null) {
            val customName = category.cards.firstOrNull { it.skinId == record.id }?.name ?: ""
            SkinCategories.removeCard(category, record.id)
            SkinCategories.addCard(category, sibling.id)
            if (customName.isNotBlank()) SkinCategories.setCardName(category, sibling.id, customName)
        } else {
            // Derived view: the sibling takes the original's place everywhere it was filed.
            transferCards(record.id, sibling.id)
        }
        if (SkinCategories.categoriesOf(record.id).isEmpty()) {
            SkinLifecycle.removeSkin(record.id)
            SkinCategories.removeEverywhere(record.id)
        }
        watcher.markSelfTriggered(sibling.file)
        reloadView()
        val fresh = SkinEntry.fromRecord(SkinRecords.findById(sibling.id) ?: sibling)
        // Re-point the open panel at the sibling BEFORE the rebuild: rebindDetail matches
        // by skin id and would force-close the panel over the vanished original id.
        if (detail?.entrySkinId == record.id) detail?.rebind(fresh)
        rebuildCards()
        // Fold the closing panel into the sibling's fresh card slot, not the removed
        // original's slot (fresh cards carry no position until the next render pass).
        val siblingIndex = cards.indexOfFirst { it.entry.skinId == sibling.id }
        if (siblingIndex >= 0) {
            val slot = cardDrag.slotFor(siblingIndex, -1)
            detail?.retargetTo(slot.first, slot.second, cellW, cellH)
        }
        return fresh
    }

    /** One-shot legacy migration, then pruning of skins whose texture vanished externally. */
    private fun migrateLegacyLibraryIfNeeded() {
        LibraryServices.migrator.migrate()
        for (id in SkinLifecycle.pruneMissingTextures()) SkinCategories.removeEverywhere(id)
    }

    /** Band widgets, page footer and the category-creation button under the tab strip. */
    private fun initBandAndFooter() {
        addRenderableWidget(band.nameField)
        addRenderableWidget(band.wheelsMinus)
        addRenderableWidget(band.wheelsPlus)
        addRenderableWidget(band.deleteButton)

        // Footer spread across the full screen width: open folder left, config center,
        // done right. Same 110px vanilla buttons; the grid stops above the reserved
        // footer band (see gridBottom): the buttons center between the card zone's
        // background edge and the bottom of the screen. Derived from height — gridBottom
        // is still 0 here (recomputeLayout runs after widget creation).
        val footerY = this.height - FOOTER_BAND + (FOOTER_BAND - 20) / 2
        val bw = 110
        val btnLeft = PAD
        val btnCenter = (this.width - bw) / 2
        val btnRight = this.width - bw - PAD
        addRenderableWidget(
            Button.builder(
                Component.translatable("simpleskinswapper.screen.carousel.open_folder")
            ) {
                //? if >=26.3 {
                /*Blaze3D.openPath(FabricLoader.getInstance().gameDir.resolve("skins").toFile().toPath())
                *///?} else {
                Util.getPlatform().openFile(FabricLoader.getInstance().gameDir.resolve("skins").toFile())
                //?}
            }.bounds(btnLeft, footerY, bw, 20).build()
        )
        addRenderableWidget(
            Button.builder(
                Component.translatable("simpleskinswapper.screen.carousel.config")
            ) {
                //? if >=26.2 {
                this.minecraft.gui.setScreen(YaclConfigScreen.create(this))
                //?} else {
                /*this.minecraft.setScreen(YaclConfigScreen.create(this))
                *///?}
            }.bounds(btnCenter, footerY, bw, 20).build()
        )
        addRenderableWidget(
            Button.builder(CommonComponents.GUI_DONE) { onClose() }
                .bounds(btnRight, footerY, bw, 20).build()
        )

        // Category creation lives in the tab strip itself (add-category entry, drawn and
        // hit-tested by the strip) — no vanilla widget, no ghost styling to fight.

        // Confirmation popups (category delete, skin deletes) are drawn and routed
        // manually at the end of render / mouseClicked — never registered as screen
        // widgets, or vanilla would render them twice and leave stale click targets.
    }

    /** init() also runs on window resize (rebuildWidgets clears every widget first):
     *  re-attach the overlays so they survive the resize instead of turning into
     *  ghosts that swallow all input without rendering. */
    private fun reattachOverlays() {
        detail?.let {
            it.onScreenResized(this.width, this.height)
            addRenderableWidget(it)
        }
        addPanel?.let {
            it.onScreenResized(this.width, this.height)
            addRenderableWidget(it)
        }
    }

    internal fun recomputeLayout() {
        // Constant viewport, whatever sits inside it (import row above, config band inside
        // the page) — switching category or expanding the band never moves the layout.
        gridTop = contentTop() + BAND_GRID_MARGIN
        gridBottom = this.height - FOOTER_BAND
        // Tab strip zone = the strip's own recessed footprint ([gridTop, gridBottom] —
        // 8px inside the card page so the strip reads as behind the page). The band tiles
        // that zone with whole slots — density ~28px, or the actual slot count when the
        // list is shorter — and centers the rounding remainder instead of showing it.
        val zoneTop = gridTop
        val zoneHeight = gridBottom - zoneTop
        // Ghost placeholders pad the strip to a minimum of five slots (All + Uncategorized
        // + three category slots), so the strip never collapses with no categories.
        val slots = tabs.visibleCategorySlots + 3
        val densitySlots = 1.coerceAtLeast((zoneHeight / 28f).roundToInt())
        val fillSlots = slots.coerceAtMost(densitySlots)
        tabH = (zoneHeight + TAB_OVERLAP * (fillSlots - 1)) / fillSlots
        val bandH = (fillSlots - 1) * (tabH - TAB_OVERLAP) + tabH
        stripZoneTop = zoneTop + (zoneHeight - bandH) / 2
        stripZoneBottom = stripZoneTop + bandH
        // The grid lives inside the page's baked border (8px, measured on the texture)
        // plus a small breathing margin on every side — cards never touch the border.
        val gridLeft = gridLeft()
        val gridRight = gridRight()
        val gridW = gridRight - gridLeft
        val gap = GRID_GAP
        // Minimum card width is user-configurable: raised, fewer columns fit and every
        // card widens (then keeps the 4:3 height ratio below).
        val minCellW = SimpleSkinSwapperConfig.get().minCardWidth.toDouble()
        cols = ((gridW - gap) / (minCellW + gap)).toInt().coerceIn(3, MAX_COLS)
        cellW = (gridW - gap * (cols - 1)) / cols
        val viewH = gridBottom - gridTop
        cellH = (cellW * 4 / 3).coerceAtMost(viewH - GRID_MARGIN * 2).coerceAtLeast(MIN_CELL_H)
        val totalW = cols * cellW + gap * (cols - 1)
        gridOffsetX = gridLeft + (gridW - totalW) / 2
        updateMaxScroll()
    }

    private fun updateMaxScroll() {
        // The config band scrolls away with the content, so it counts toward it.
        // The trailing "+" card occupies one extra cell after the last skin.
        val bandH = if (selectedCategory != null) band.height(true) + GRID_GAP else 0
        val rows = ceil((cards.size + 1) / cols.toDouble()).toInt()
        val contentH = bandH + rows * (cellH + GRID_GAP) - GRID_GAP
        maxScroll = 0.coerceAtLeast(contentH - (gridBottom - gridTop - GRID_MARGIN * 2))
    }

    /** Card-area inner edges: the page's baked border plus the grid margin. The config band uses them too. */
    internal fun gridLeft(): Int = panelX - 6 + PAGE_BORDER + GRID_MARGIN

    internal fun gridRight(): Int = this.width - PAD - PAGE_BORDER - GRID_MARGIN

    /** Top of the config band inside the viewport; scrolls away with the card content. */

    /** Unscrolled Y where the first grid row sits (right under the band when it is shown). */
    private fun contentStartY(): Int =
        gridTop + GRID_MARGIN + (if (selectedCategory != null) band.height(selectedCategory != null) + GRID_GAP else 0)

    /** Title zone bottom: the title draws alone at the top-left, the page starts below it. */
    private fun contentTop(): Int = TITLE_ZONE_BOTTOM

    private val panelX: Int get() = STRIP_X + TAB_W + 8

    // ------------------------------------------------------------------
    // View data
    // ------------------------------------------------------------------

    private fun reloadView() {
        entries.clear()
        val registry = SkinRecords.all()
        val category = selectedCategory
        if (category == null) {
            // All skins: every registry entry — Uncategorized: the ones no category holds.
            val source = if (uncategorizedSelected) {
                registry.filter { SkinCategories.categoriesOf(it.id).isEmpty() }
            } else {
                registry
            }
            for (record in source) entries.add(SkinEntry.fromRecord(record))
        } else {
            val byId = registry.associateBy { it.id }
            for (card in category.cards) {
                byId[card.skinId]?.let { record ->
                    val entry = SkinEntry.fromRecord(record)
                    if (card.name.isNotBlank()) entry.displayNameOverride = card.name
                    entries.add(entry)
                }
            }
        }
    }

    private fun rebuildCards() {
        for (card in cards) removeWidget(card)
        cards.clear()
        cardDisplay.clear()
        addCard?.let { removeWidget(it) }
        addCardDisplay.clear()
        recomputeLayout()
        for (entry in entries) {
            val card = SkinLibraryCard(this, entry, cellW, cellH)
            cards.add(card)
            addRenderableWidget(card)
        }
        // Trailing "+" card at the end of every list.
        val newAddCard = SkinAddCard(this, cellW, cellH)
        addCard = newAddCard
        addRenderableWidget(newAddCard)
        updateMaxScroll()
        scrollY = Mth.clamp(scrollY, 0, maxScroll)
        band.refreshWidgets()
        rebindDetail()
        // Cards were re-added after the overlays: raise the open ones back to the top
        // of the widget order, or they would render (and hit) behind the fresh cards.
        raiseOverlays()
    }

    /** Re-appends the open overlays so they stay last in the widget order (on top). */
    private fun raiseOverlays() {
        detail?.let { removeWidget(it); addRenderableWidget(it) }
        addPanel?.let { removeWidget(it); addRenderableWidget(it) }
    }

    fun indexOfCard(card: SkinLibraryCard): Int = cards.indexOf(card)

    /** Category-color ARGB for the allocation marker, or null when the card is not allocated. */
    fun allocationColorFor(card: SkinLibraryCard): Int? {
        val category = selectedCategory ?: return null
        val idx = cards.indexOf(card)
        return if (idx in 0 until category.maxWheels * WHEEL_SIZE) SkinCategoryPalette.colorOf(category.dye) else null
    }

    fun deleteEntry(entry: SkinEntry) {
        watcher.markSelfTriggered(entry.file.name)
        SkinLifecycle.removeSkin(entry.skinId)
        SkinCategories.removeEverywhere(entry.skinId)
        reloadView()
        rebuildCards()
    }

    /** Commits the detail panel's pending text edits (global + per-category names). */
    internal fun commitEntryNames(entry: SkinEntry, display: String, categoryName: String) {
        SkinRecords.rename(entry.skinId, display.trim())
        selectedCategory?.let { SkinCategories.setCardName(it, entry.skinId, categoryName.trim()) }
        reloadView()
        rebuildCards()
    }

    internal fun openDetail(card: SkinLibraryCard) {
        if (detail != null || addPanel != null) return
        val panel = SkinDetailPanel(this)
        panel.open(card)
        detail = panel
        addRenderableWidget(panel)
    }

    /** Opens the add-skin overlay from the trailing "+" card. */
    internal fun openAddPanel() {
        if (detail != null || addPanel != null) return
        val card = addCard ?: return
        val panel = SkinAddPanel(this)
        panel.open(card)
        addPanel = panel
        addRenderableWidget(panel)
    }

    /** Drops an overlay that was cleared by a widget rebuild or fully closed itself. */
    private fun <T> pruned(panel: T?): T? where T : net.minecraft.client.gui.components.AbstractWidget, T : SkinOverlayPanel {
        if (panel == null) return null
        if (!children().contains(panel)) return null
        if (panel.isRemovePending) {
            removeWidget(panel)
            return null
        }
        return panel
    }

    /** Ingests the staged skin through the registry (dedup by texture value); adding from
     *  a selected category adds a card there — copy semantics, other categories untouched. */
    fun confirmAddSkin(source: File, display: String, type: SkinType): Boolean {
        val bytes = try {
            Files.readAllBytes(source.toPath())
        } catch (e: IOException) {
            SimpleSkinSwapper.LOGGER.warn("Could not read staged skin: {}", e.message)
            return false
        }
        val record = SkinLifecycle.createSkin(bytes, type.mojangVariant, display) ?: return false
        watcher.markSelfTriggered(record.file)
        selectedCategory?.let { SkinCategories.addCard(it, record.id) }
        reloadView()
        rebuildCards()
        return true
    }

    /** Re-points the detail panel at the fresh entry after a reload, closing it if the skin is gone. */
    private fun rebindDetail() {
        val d = detail ?: return
        val id = d.entrySkinId ?: return
        val fresh = entries.firstOrNull { it.skinId == id }
        if (fresh == null) d.close(instant = true) else d.rebind(fresh)
    }

    // ------------------------------------------------------------------
    // Band widgets
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // Tab strip geometry
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    //? if >=26.1 {
    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
    //?} else {
    /*override fun render(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
    *///?}
        graphics.fill(0, 0, this.width, this.height, 0x66000000)

        // Fully-closed overlays are unregistered outside of their own render pass. An
        // overlay that is no longer a screen child (cleared by a widget rebuild without
        // init) would swallow all input invisibly — drop it too.
        detail = pruned(detail)
        addPanel = pruned(addPanel)

        tabs.updateTabAutoScroll(mouseY)
        // Tab strip background + unselected tabs first: they pass under the grid page.
        drawTabStripUnder(graphics, mouseX, mouseY)

        // Book-style page panel behind the card grid (the "main page" surface), on top of the
        // strip background, vanilla recipe-book style.
        drawPagePanel(graphics, panelX - 6, gridTop - PAGE_BORDER, this.width - PAD - panelX + 6, gridBottom - gridTop + PAGE_BORDER * 2)

        // Config band, inside the viewport: scrolls away with the content like a card row,
        // clipped by the same page-inner rect as the cards.
        if (selectedCategory != null) {
            graphics.enableScissor(panelX - 6 + PAGE_BORDER, gridTop, this.width - PAD - PAGE_BORDER, gridBottom)
            band.draw(graphics, entries.size, mouseX, mouseY)
            graphics.disableScissor()
        }

        // Position + viewport-clip every card BEFORE rendering them (inside super), so the
        // scissors and slots are never a frame behind the cursor.
        updateCardPositions(mouseX, mouseY)

        // Front pass of the strip: over the page normally; with an overlay open it renders
        // before the widgets so the panel covers it instead of the tab drawing over it.
        drawStripFrontPass(graphics, mouseX, mouseY, beforeWidgets = true)

        //? if >=26.1 {
        super.extractRenderState(graphics, mouseX, mouseY, delta)
        //?} else {
        /*super.render(graphics, mouseX, mouseY, delta)
        *///?}

        drawStripFrontPass(graphics, mouseX, mouseY, beforeWidgets = false)

        // While the detail overlay is open, the base chrome stays static under the panel.
        if (detail == null && addPanel == null) {
            // "Reorder-dragged" card floats above everything else.
            val dragged = reorderDraggingCard
            if (dragged != null) {
                //? if >=26.1 {
                dragged.extractRenderState(graphics, mouseX, mouseY, delta)
                //?} else {
                /*dragged.render(graphics, mouseX, mouseY, delta)
                *///?}
            }

            // Title, top-left, on the same text line as the header buttons (vanilla centers
            // button labels at y + (height-8)/2 — same formula here for optical alignment).
            graphics.text(client.font, Component.translatable("simpleskinswapper.title"), STRIP_X, TITLE_Y, 0xFFFFFFFF.toInt())

            drawEmptyStateMessage(graphics)

            // Tooltip for hovered tab
            if (tabs.tabDragCategoryIndex == -1 && reorderDraggingCard == null && confirmPopup == null) {
                tabs.tabAt(mouseY, mouseX)?.let { tab ->
                    drawTooltip(graphics, mouseX, mouseY, tabTooltipLabel(tab))
                }
                // Tooltip for hovered dye picker cell in the expanded band
                band.hoveredDyeTooltip(mouseX, mouseY)?.let { drawTooltip(graphics, mouseX, mouseY, it) }
            }
        }

        // Shared confirmation popup floats above everything, panels included (it dims
        // the panel it was opened from).
        confirmPopup?.draw(graphics, mouseX, mouseY, delta)
    }

    /** Tab tooltip: built-in view names, or the live category name. */
    private fun tabTooltipLabel(tab: Int): Component = when {
        tab == 0 -> Component.translatable("simpleskinswapper.screen.library.all_skins")
        tab == 1 -> Component.translatable("simpleskinswapper.screen.library.uncategorized")
        else -> Component.nullToEmpty(SkinCategories.all().getOrNull(tab - 2)?.name ?: "")
    }

    /** Centered hint when the current view has no skins (never added, or empty category). */
    private fun drawEmptyStateMessage(graphics: GuiGraphicsExtractor) {
        if (cards.isNotEmpty()) return
        val messageKey = when {
            selectedCategory != null -> "simpleskinswapper.screen.library.empty_category"
            uncategorizedSelected -> "simpleskinswapper.screen.library.empty_uncategorized"
            else -> "simpleskinswapper.screen.carousel.no_skins"
        }
        // A "\n" in the translation splits the message into centered lines (the
        // empty-category hint reads better balanced on two lines).
        val lines = Component.translatable(messageKey).string.split("\n")
        val lineHeight = font.lineHeight + 1
        var lineY = (gridTop + gridBottom) / 2 - (lines.size * lineHeight) / 2
        for (line in lines) {
            graphics.centeredText(
                font, Component.literal(line),
                (panelX + this.width - PAD) / 2, lineY, 0xFFAAAAAA.toInt()
            )
            lineY += lineHeight
        }
    }

    /** Tab strip background + unselected tabs, drawn before the grid page so they pass under it. */
    private fun drawTabStripUnder(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val top = tabs.stripTop()
        val tabBottom = tabs.stripAlignedBottom()

        // No zone-wide background: every tab wears its own panel, and their stacked
        // borders read as separators. The recessed margins show the screen backdrop.

        // Unselected tabs, clipped to the strip — All Skins is a tab like the others.
        graphics.enableScissor(-PANEL_BLEED, top, STRIP_X + TAB_W + 2, tabBottom)
        for (i in 0..SkinCategories.all().size + 1) {
            val y = tabs.tabY(i)
            // Whole tabs only: a partially-visible tab at the band's bottom edge would
            // show a dangling overlap border past the last full tab.
            if (y + tabH !in top..tabBottom) continue
            if (isSelectedTab(i)) continue
            drawTab(graphics, i, y)
        }
        // Ghost placeholders pad the category slots up to three: inert, dimmed, no label.
        for (i in SkinCategories.all().size + 2..tabs.visibleCategorySlots + 1) {
            val y = tabs.tabY(i)
            if (y + tabH !in top..tabBottom) continue
            drawBookPanel(graphics, -PANEL_BLEED, y, STRIP_X + TAB_W + 2 + PANEL_BLEED, tabH, lit = false)
            graphics.fill(-PANEL_BLEED, y, STRIP_X + TAB_W + 2 + PANEL_BLEED, y + tabH, 0x66000000.toInt())
        }
        drawAddCategoryEntry(graphics, mouseX, mouseY)
        graphics.disableScissor()
    }

    /** Selected + dragged tab and the insertion line, drawn after the grid page so they overlap it. */
    /**
     * Front pass of the tab strip: the selected tab (over the page edge), the dragged
     * tab and the insertion line. Renders BELOW overlay panels — call it before the
     * widgets render when an overlay is open.
     */
    /** Runs the strip front pass exactly once, at the right layer: after the widgets
     *  normally (over the page edge), before them when an overlay panel is open. */
    private fun drawStripFrontPass(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, beforeWidgets: Boolean) {
        val overlayOpen = detail != null || addPanel != null
        if (beforeWidgets != overlayOpen) return
        drawTabStripFront(graphics, mouseX, mouseY)
    }

    private fun drawTabStripFront(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val top = tabs.stripTop()
        val tabBottom = tabs.stripAlignedBottom()

        // Selected tab: full-color book panel, flush left, its right edge tucking slightly under
        // the grid page border. Clipped vertically to the strip so it scrolls away like the
        // other tabs, while still overflowing to the right over the page.
        val selected = selectedTabIndex()
        if (selected >= 0) {
            val y = tabs.tabY(selected)
            // Whole tabs only, mirrored from the under pass: a selected tab scrolled
            // half out of the band's top must not dangle its overlap border inside.
            if (y >= top && y + tabH <= tabBottom) {
                graphics.enableScissor(-PANEL_BLEED, top, STRIP_X + TAB_W + TAB_SELECTED_STICKOUT, tabBottom)
                drawBookPanel(graphics, -PANEL_BLEED, y, STRIP_X + TAB_W + TAB_SELECTED_STICKOUT + PANEL_BLEED, tabH, lit = true)
                drawTabContent(graphics, selected, y)
                graphics.disableScissor()
            }
        }

        // Dragged tab follows the cursor vertically as a floating full-color panel,
        // clipped to the strip zone the same way.
        if (tabs.tabDragActive && tabs.tabDragCategoryIndex > 0) {
            val y = (tabs.tabDragCursorY - tabH / 2).coerceIn(top, tabBottom - tabH)
            graphics.enableScissor(-PANEL_BLEED, top, STRIP_X + TAB_W + TAB_SELECTED_STICKOUT, tabBottom)
            drawBookPanel(graphics, -PANEL_BLEED, y, STRIP_X + TAB_W + TAB_SELECTED_STICKOUT + PANEL_BLEED, tabH, lit = true)
            drawTabContent(graphics, tabs.tabDragCategoryIndex, y)
            graphics.disableScissor()
        }
        // Insertion line: after [tabInsertionIndex] categories (pre-removal space).
        if (tabs.tabDragActive && tabs.tabInsertionIndex >= 0) {
            val lineY = tabs.insertionLineY()
            if (lineY in top..tabBottom) {
                graphics.fill(STRIP_X, lineY - 1, STRIP_X + TAB_W, lineY + 1, 0xFFFFFFFF.toInt())
            }
        }
    }

    /** Add-category entry: the strip's last slot, rendered as a pseudo-tab — its own
     *  unlit panel with a centered "+" (brightening on hover), no frame, no vanilla button. */
    private fun drawAddCategoryEntry(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val top = tabs.stripTop()
        val y = tabs.addEntryY()
        if (y + tabH < top || y + tabH > tabs.stripAlignedBottom()) return
        val hovered = tabs.addEntryAt(mouseY, mouseX)
        val glyph = if (hovered) 0xFFC5C5C5.toInt() else 0xFF999999.toInt()
        drawBookPanel(graphics, -PANEL_BLEED, y, STRIP_X + TAB_W + 2 + PANEL_BLEED, tabH, lit = false)
        graphics.centeredText(font, Component.literal("+"), STRIP_X + TAB_W / 2, y + (tabH - font.lineHeight) / 2, glyph)
    }

    private fun isSelectedTab(index: Int): Boolean =
        when {
            index == 0 -> selectedCategory == null && !uncategorizedSelected
            index == 1 -> selectedCategory == null && uncategorizedSelected
            else -> selectedCategory === SkinCategories.all().getOrNull(index - 2)
        }

    private fun selectedTabIndex(): Int {
        val category = selectedCategory ?: return if (uncategorizedSelected) 1 else 0
        val idx = SkinCategories.all().indexOf(category)
        return if (idx >= 0) idx + 2 else -1
    }

    private fun drawTab(graphics: GuiGraphicsExtractor, index: Int, y: Int) {
        // Each unselected tab wears its own panel (same dressing as the selected one, but
        // flush with the strip's right edge instead of tucking under the page), so the
        // panel borders read as separators instead of one big background.
        drawBookPanel(graphics, -PANEL_BLEED, y, STRIP_X + TAB_W + 2 + PANEL_BLEED, tabH, lit = false)
        drawTabContent(graphics, index, y)
    }

    private fun drawTabContent(graphics: GuiGraphicsExtractor, index: Int, y: Int) {
        val label = when {
            index == 0 -> Component.translatable("simpleskinswapper.screen.library.all_skins")
            index == 1 -> Component.translatable("simpleskinswapper.screen.library.uncategorized")
            else -> Component.nullToEmpty(SkinCategories.all().getOrNull(index - 2)?.name ?: "")
        }
        // Same text origin for every tab — All skins has no dye icon but stays aligned.
        val nameX = STRIP_X + 16
        val nameRight = STRIP_X + TAB_W - 3
        val textY = y + (tabH - font.lineHeight) / 2
        // Truncate overlong names with an ellipsis instead of hard-clipping mid-glyph;
        // the scissor stays as a safety net (zero-size scissors crash MC 26.2).
        val available = nameRight - nameX
        var text = label.string
        if (available >= 8 && font.width(text) > available) {
            while (text.isNotEmpty() && font.width("$text...") > available) text = text.dropLast(1)
            text += "..."
        }
        if (available >= 8) {
            graphics.enableScissor(nameX, y + 2, nameRight, y + tabH - 2)
            graphics.text(client.font, Component.nullToEmpty(text), nameX, textY, 0xFFFFFFFF.toInt())
            graphics.disableScissor()
        }
        if (index > 1) {
            SkinCategories.all().getOrNull(index - 2)?.let {
                val s = 8
                val x0 = STRIP_X + 4
                // Center the square on the glyphs' optical center (same line as the text),
                // not on the full tab height — the 9px font renders in the top 7px of its line.
                val y0 = textY + (font.lineHeight - s) / 2
                // Categories store the dye NAME: the icon always resolves on this version.
                val entry = SkinCategoryPalette.ENTRIES.firstOrNull { e -> e.dyeName == it.dye }
                if (entry != null) {
                    DyeIcons.draw(graphics, entry.dyeName, x0, y0, s)
                } else {
                    graphics.fill(x0, y0, x0 + s, y0 + s, SkinCategoryPalette.colorOf(it.dye))
                }
            }
        }
    }

    private fun drawTooltip(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, message: Component) {
        val w = font.width(message) + 8
        val h = font.lineHeight + 4
        var x = mouseX + 8
        var y = mouseY - h - 2
        if (x + w > this.width) x = this.width - w
        if (y < 0) y = mouseY + 12
        graphics.fill(x, y, x + w, y + h, 0xF0100018.toInt())
        graphics.fill(x, y, x + w, y + 1, 0xFF505068.toInt())
        graphics.text(client.font, Component.nullToEmpty(message.string), x + 4, y + 2, 0xFFFFFFFF.toInt())
    }

    // ------------------------------------------------------------------
    // Card grid positioning
    // ------------------------------------------------------------------

    private fun updateCardPositions(mouseX: Int, mouseY: Int) {
        // Band widgets track the scrolled band position every frame.
        band.refreshWidgets()

        val dragged = reorderDraggingCard
        // Insertion gap only where a reorder exists: category views. The derived views
        // (All skins, Uncategorized) keep a fixed default order, so nothing shifts there.
        val dragIndex = if (selectedCategory != null) dragged?.let { cards.indexOf(it) } ?: -1 else -1
        if (dragIndex >= 0) cardDrag.updateInsertionIndex(cards.size, mouseX, mouseY)

        val now = System.nanoTime()
        val dt = if (lastCardEaseNanos == 0L) 1.0F else ((now - lastCardEaseNanos) / 1_000_000_000.0F).coerceAtMost(0.1F)
        val t = 1.0F - exp((-CARD_SLIDE_SPEED * dt).toDouble()).toFloat()

        for (i in cards.indices) {
            val card = cards[i]
            val slot = cardDrag.slotFor(i, dragIndex)
            if (card === dragged) {
                card.overridePosition(cardDrag.cursorX - cardDrag.grabX, cardDrag.cursorY - cardDrag.grabY)
                continue
            }
            easeWidgetToSlot(card, card.x == 0 && card.y == 0, slot, t, cardDisplay.getOrPut(card) { FloatArray(2) })
        }

        updateAddCardPosition(dragIndex, t)
        lastCardEaseNanos = now
    }

    /**
     * Every grid widget renders through the same fixed viewport scissor: widgets sliding in
     * and out are smoothly half-clipped by the page border instead of popping.
     */
    private fun easeWidgetToSlot(widget: GridSlottedWidget, unpositioned: Boolean, slot: Pair<Int, Int>, t: Float, display: FloatArray) {
        widget.clipLeft = panelX - 6 + PAGE_BORDER
        widget.clipTop = gridTop
        widget.clipRight = this.width - PAD - PAGE_BORDER
        widget.clipBottom = gridBottom
        val (ex, ey) = cardDrag.easeToward(display, slot, t, unpositioned)
        widget.overridePosition(ex, ey)
    }

    /**
     * The trailing "+" card slides like a card: it sits one slot after the last skin
     * and shifts when a reorder insertion gap opens before it. An empty category
     * hides it entirely — clicking anywhere in the zone opens the add overlay instead.
     */
    private fun updateAddCardPosition(dragIndex: Int, t: Float) {
        val ac = addCard ?: return
        if (selectedCategory != null && cards.isEmpty()) {
            ac.visible = false
            return
        }
        ac.visible = true
        easeWidgetToSlot(ac, ac.x == 0 && ac.y == 0, cardDrag.slotFor(cards.size, dragIndex), t, addCardDisplay.getOrPut(ac) { FloatArray(2) })
    }

    // ------------------------------------------------------------------
    // Reorder drag (cards)
    // ------------------------------------------------------------------

    internal fun beginCardReorder(card: SkinLibraryCard, mouseX: Int, mouseY: Int) {
        cardDrag.begin(card, mouseX, mouseY)
        reorderDraggingCard = card
    }

    private fun finishCardReorder(mouseX: Int, mouseY: Int) {
        val card = reorderDraggingCard ?: return
        reorderDraggingCard = null
        if (cards.indexOf(card) < 0) return

        // Drop on a category tab = COPY the card there; the source keeps its own and the
        // view tabs (All skins, Uncategorized) are not drop targets.
        val tab = tabs.tabAt(mouseY, mouseX)
        if (tab != null && tab >= 2) {
            val target = SkinCategories.all().getOrNull(tab - 2)
            if (target != null && target !== selectedCategory) {
                SkinCategories.addCard(target, card.entry.skinId)
            }
            cardDrag.stop()
            reloadView()
            rebuildCards()
            return
        }

        // Grid drop in a category = reorder within it (the CardEntry moves whole, keeping
        // its custom name). Derived views keep the default order: the card snaps back.
        val category = selectedCategory
        if (category != null && cardDrag.insertionIndex in 0..category.cards.size) {
            val from = category.cards.indexOfFirst { it.skinId == card.entry.skinId }
            if (from >= 0) {
                var to = cardDrag.insertionIndex
                if (to > from) to--
                val moved = category.cards.removeAt(from)
                category.cards.add(to.coerceIn(0, category.cards.size), moved)
                SkinCategories.save()
            }
        }
        cardDrag.stop()
        reloadView()
        rebuildCards()
    }

    // ------------------------------------------------------------------
    // Tab drag & auto-scroll
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        // The confirmation popup swallows every click while open (buttons run, the rest cancels).
        confirmPopup?.let { return it.handleClick(click, doubled) }

        handleOverlayClick(click, doubled)?.let { return it }

        val mx = click.x().toInt()
        val my = click.y().toInt()
        if (handleChromeClick(mx, my, click, doubled)) return true

        // Grid wheel-scroll area click-through: let children (cards, widgets) handle the rest.
        return super.mouseClicked(click, doubled)
    }

    /** Overlay panels own the click entirely while open; `null` means no overlay consumed it. */
    private fun handleOverlayClick(click: MouseButtonEvent, doubled: Boolean): Boolean? {
        detail?.let { d ->
            val handled = d.mouseClicked(click, doubled)
            if (handled) setFocused(d)
            return handled
        }
        addPanel?.let { d ->
            val handled = d.mouseClicked(click, doubled)
            if (handled) setFocused(d)
            return handled
        }
        return null
    }

    /** Non-card chrome: tab strip, category band, empty-category zone. */
    private fun handleChromeClick(mx: Int, my: Int, click: MouseButtonEvent, doubled: Boolean): Boolean {
        // Tab strip: select on click, start a potential drag on press (-1 = none, 0 = All, >0 = category).
        val tab = tabs.tabAt(my, mx)
        if (tab != null && click.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            tabs.press(tab, click.y(), my)
            return true
        }

        // Add-category entry: immediate creation on click, no drag semantics.
        if (click.button() == InputConstants.MOUSE_BUTTON_LEFT && tabs.addEntryAt(my, mx)) {
            createCategory()
            return true
        }

        // Category band: bar click toggles expansion, swatch click recolors. The part of
        // the band scrolled above the viewport top is not clickable, matching its clipping.
        if (band.handleBarClick(mx, my)) return true
        if (band.handleSwatchClick(mx, my)) return true

        // Empty category: a click anywhere in the card zone opens the add-skin overlay.
        if (isEmptyCategoryAddClick(mx, my, click)) {
            openAddPanel()
            return true
        }
        return false
    }

    /** An empty category turns any left click in the card zone into an add-panel open —
     *  except inside the expanded band, whose widget rows (name, steppers, delete) sit
     *  inside the grid rect and must receive their own clicks through child routing. */
    private fun isEmptyCategoryAddClick(mx: Int, my: Int, click: MouseButtonEvent): Boolean {
        if (selectedCategory == null || cards.isNotEmpty()) return false
        if (click.button() != InputConstants.MOUSE_BUTTON_LEFT) return false
        val bandBottom = band.y() + band.height(selectedCategory != null)
        if (band.expanded && my < bandBottom) return false
        return mx >= gridLeft() && mx < gridRight() && my >= gridTop && my < gridBottom
    }

    internal fun confirmCategoryDelete() {
        val category = selectedCategory
        if (category != null) {
            SkinCategories.removeCategory(category)
            selectCategory(null)
        }
    }

    private fun createCategory() {
        // Categories store the dye NAME; the color derives from the running version.
        val category = SkinCategories.createCategory(nextDefaultCategoryName(), "white")
        selectCategory(category)
        band.expanded = true
        band.refreshWidgets()
    }

    /** "New Category", incremented to the first free suffix among live category names. */
    private fun nextDefaultCategoryName(): String {
        val base = Component.translatable("simpleskinswapper.screen.library.add_category").string
        val taken = SkinCategories.all().mapTo(HashSet()) { it.name }
        if (base !in taken) return base
        var n = 2
        while ("$base $n" in taken) n++
        return "$base $n"
    }

    private fun selectCategory(category: LibraryCategory?) {
        uncategorizedSelected = false
        selectedCategory = category
        band.expanded = false
        scrollY = 0
        reloadView()
        rebuildCards()
        recomputeLayout()
    }

    override fun mouseDragged(click: MouseButtonEvent, offsetX: Double, offsetY: Double): Boolean {
        detail?.let { return it.mouseDragged(click, offsetX, offsetY) }
        addPanel?.let { return it.mouseDragged(click, offsetX, offsetY) }
        val mx = click.x().toInt()
        val my = click.y().toInt()
        if (tabs.drag(tabs.tabDragCategoryIndex, click.button() == InputConstants.MOUSE_BUTTON_LEFT, my)) {
            return true
        }
        if (reorderDraggingCard != null && click.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            cardDrag.dragTo(mx, my)
            return true
        }
        val result = super.mouseDragged(click, offsetX, offsetY)
        // Deferred: a card may have raised a reorder start during children iteration —
        // begin the drag (and unregister the card so it only renders via the manual
        // floating pass and no longer swallows input) once the iteration is over.
        pendingReorderStart?.let { (card, pos) ->
            pendingReorderStart = null
            beginCardReorder(card, pos.first, pos.second)
            removeWidget(card)
        }
        return result
    }

    override fun mouseReleased(click: MouseButtonEvent): Boolean {
        detail?.let { return it.mouseReleased(click) }
        addPanel?.let { return it.mouseReleased(click) }
        val mx = click.x().toInt()
        val my = click.y().toInt()
        if (tabs.tabDragCategoryIndex >= 0) {
            when (val result = tabs.release(click.button() == InputConstants.MOUSE_BUTTON_LEFT)) {
                is TabStripController.Release.Move -> {
                    SkinCategories.moveCategory(result.from, result.to)
                    rebuildCards()
                }
                is TabStripController.Release.Select -> {
                    when {
                        result.tabIndex == 0 -> selectCategory(null)
                        result.tabIndex == 1 -> {
                            selectedCategory = null
                            uncategorizedSelected = true
                            band.expanded = false
                            scrollY = 0
                            reloadView()
                            rebuildCards()
                            recomputeLayout()
                        }
                        else -> selectCategory(SkinCategories.all().getOrNull(result.tabIndex - 2))
                    }
                }
                TabStripController.Release.None -> {}
            }
            return true
        }
        if (reorderDraggingCard != null && click.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            finishCardReorder(mx, my)
            return true
        }
        return super.mouseReleased(click)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, hozAmount: Double, vertAmount: Double): Boolean {
        detail?.let { return it.mouseScrolled(mouseX, mouseY, hozAmount, vertAmount) }
        addPanel?.let { return it.mouseScrolled(mouseX, mouseY, hozAmount, vertAmount) }
        val mx = mouseX.toInt()
        val my = mouseY.toInt()
        if (mx < STRIP_X + TAB_W + TAB_SELECTED_STICKOUT && my >= tabs.stripTop() && my <= tabs.stripBottom()) {
            // One wheel notch = one slot: the wheel must step in slotH units or every
            // notch misaligns the stack by (tabH - overlap) against its own grid.
            tabs.scrollBy(vertAmount.toFloat() * tabs.slotH())
            return true
        }
        scrollY = Mth.clamp(scrollY - (vertAmount * (cellH + GRID_GAP)).toInt(), 0, maxScroll)
        return true
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        confirmPopup?.let { return it.onEsc() }
        detail?.let { return it.keyPressed(event) }
        addPanel?.let { return it.keyPressed(event) }
        return super.keyPressed(event)
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        detail?.let { return it.charTyped(event) }
        addPanel?.let { return it.charTyped(event) }
        return super.charTyped(event)
    }

    // ------------------------------------------------------------------
    // Lifecycle: close, tick, watcher, imports (carried over from the carousel)
    // ------------------------------------------------------------------

    override fun onClose() {
        watcher.stop()
        //? if >=26.2 {
        this.minecraft.gui.setScreen(parent)
        //?} else {
        /*this.minecraft.setScreen(parent)
        *///?}
    }

    override fun tick() {
        super.tick()
        watcher.pollChanges()
    }

    /** Opens the native PNG picker and hands the result to [onPicked] on the main thread. */
    internal fun pickSkinFile(onPicked: (File) -> Unit) {
        //? if >=26.3 {
        /*openSkinFileDialog(onPicked)
        *///?} else {
        val selected = openSkinFileDialog() ?: return
        onPicked(selected)
        //?}
    }

    //? if >=26.3 {
    /*// SDL file dialogs are asynchronous (tinyfd was synchronous): the callback fires from the
    // event pump, possibly after this screen closed — the result is marshalled to the main
    // thread and only applied if this screen is still open.
    private var dialogCallback: SDL_DialogFileCallback? = null

    private fun openSkinFileDialog(onPicked: (File) -> Unit) {
        dialogCallback?.free()
        val callback = SDL_DialogFileCallback.create { _, filelist, _ ->
            if (filelist != 0L) {
                val first = MemoryUtil.memGetAddress(filelist)
                if (first != 0L) {
                    val path = MemoryUtil.memUTF8(first)
                    Minecraft.getInstance().execute {
                        if (Minecraft.getInstance().gui.screen() === this) onPicked(File(path))
                    }
                }
            }
        }
        dialogCallback = callback
        MemoryStack.stackPush().use { stack ->
            val filters = SDL_DialogFileFilter.calloc(1, stack)
            filters.get(0).name(stack.UTF8("PNG")).pattern(stack.UTF8("png"))
            SDLDialog.SDL_ShowOpenFileDialog(callback, 0L, 0L, filters, "", false)
        }
    }
    *///?} else {
    private fun openSkinFileDialog(): File? {
        MemoryStack.stackPush().use { stack ->
            val filters = stack.mallocPointer(1)
            filters.put(stack.UTF8("*.png"))
            filters.flip()
            val path = TinyFileDialogs.tinyfd_openFileDialog(
                Component.translatable("simpleskinswapper.screen.carousel.add_from_file.dialog_title").string,
                "", filters, "PNG", false
            )
            return path?.let { File(it) }
        }
    }
    //?}

    companion object {
        private const val PAD = 4
        /** Rows reserved under the grid for the footer buttons (20px + margins). */
        private const val FOOTER_BAND = 32
        private const val TITLE_Y = 8
        private const val TITLE_ZONE_BOTTOM = 20

        internal const val STRIP_X = 4
        internal const val TAB_W = 100
        internal const val tabHMin = 28
        internal const val TAB_OVERLAP = 2

        // How far the selected tab's panel tucks under the grid page border (its right edge is
        // this many px past the tab column).
        private const val TAB_SELECTED_STICKOUT = 6

        // Left offset the tab panels are drawn from, so their left border sits off-screen
        // (the overlay_recipe nine-slice border is 4px).
        private const val PANEL_BLEED = 4

        // GRID_GAP: spacing between grid cards, horizontally and vertically.
        private const val GRID_GAP = 6
        private const val MAX_COLS = 10

        // Thickness of the page texture's baked border (measured: 8px of bevel on every side).
        // The card viewport is the page rect inset by this; the grid adds a small margin inside.
        private const val PAGE_BORDER = 8
        private const val GRID_MARGIN = 4
        private const val MIN_CELL_H = 56

        private const val BAND_GRID_MARGIN = 6

        // Card slot easing + tab strip auto-scroll
        private const val CARD_SLIDE_SPEED = 14.0F

        private var lastCardEaseNanos = 0L

        // ------------------------------------------------------------------
        // Vanilla recipe-book textures (same blit signature on 1.21.11 and 26.x)
        // ------------------------------------------------------------------

        // The recipe hover-highlight frame, in the GUI atlas with a nine_slice mcmeta
        // (32x32, border 4): blitSprite stretches it as a panel on its own.
        internal val PANEL_SPRITE_ACCESS = Identifier.withDefaultNamespace("recipe_book/overlay_recipe")

        // The book page panel, cropped from gui/recipe_book.png with the search icon erased
        // (nine_slice mcmeta, border 8) — the main grid page surface.
        private val PAGE_SPRITE = Identifier.fromNamespaceAndPath("simpleskinswapper", "library/page")

        // Idle card frame: the tab-zone sprite with its darkening baked in per pixel, so the
        // transparent corners stay transparent (a flat fill would tint them).
        internal val CARD_SPRITE_ACCESS = Identifier.fromNamespaceAndPath("simpleskinswapper", "library/card")

        /** The recipe-book frame sprite as a panel: full color when lit, darkened otherwise. */
        private fun drawBookPanel(graphics: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, lit: Boolean) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, PANEL_SPRITE_ACCESS, x, y, w, h)
            if (!lit) graphics.fill(x, y, x + w, y + h, 0x66000000)
        }

        /** The main grid page: the custom book-page texture (no search icon), full color. */
        private fun drawPagePanel(graphics: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, PAGE_SPRITE, x, y, w, h)
        }

        /** The clickable-recipe frame wrapped around a skin card (highlight variant on hover). */
        internal fun drawCardFrame(graphics: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, hovered: Boolean) {
            // Idle: the dedicated darkened sprite (grayscale of the tab-zone look, transparent
            // corners preserved). Hovered: the full-color sprite, like a selected tab.
            val sprite = if (hovered) PANEL_SPRITE_ACCESS else CARD_SPRITE_ACCESS
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, w, h)
        }

        private const val WHEEL_SIZE = 10
    }
}
