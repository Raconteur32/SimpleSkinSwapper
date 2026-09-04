package fr.raconteur.simpleskinswapper.gui.library

import com.mojang.blaze3d.platform.InputConstants
import fr.raconteur.simpleskinswapper.SimpleSkinSwapper
import fr.raconteur.simpleskinswapper.changeskin.AccountSkinFetcher
import fr.raconteur.simpleskinswapper.gui.EdgeSafeButtonWidget
import fr.raconteur.simpleskinswapper.gui.SkinNameStore
import fr.raconteur.simpleskinswapper.gui.SkinEntry
import fr.raconteur.simpleskinswapper.gui.SkinType
import fr.raconteur.simpleskinswapper.gui.SkinTypeStore
import fr.raconteur.simpleskinswapper.gui.config.YaclConfigScreen
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.util.Mth
import net.minecraft.util.Util
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
import java.nio.file.StandardCopyOption
import java.util.IdentityHashMap

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
    internal var selectedCategory: SkinCategory? = null
    internal val band = CategoryBand(this)

    // Card reorder drag (started by a card's frame/handle zone).
    internal var reorderDraggingCard: SkinLibraryCard? = null
    internal var dragRotatingCard: SkinLibraryCard? = null
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
    private val tabs = TabStripController({ gridTop }, { this.height - 28 })

    // Grid scroll + layout (recomputed in recomputeLayout()).
    internal var scrollY = 0
    private var cols = 3
    private var cellW = 0
    private var cellH = 0
    private var gridOffsetX = 0
    internal var gridTop = 0
    private var gridBottom = 0
    private var maxScroll = 0

    // Widgets
    private lateinit var accountField: EditBox
    private lateinit var addFromFileButton: Button
    private lateinit var addFromAccountButton: Button
    private val watcher = LibraryFileWatcher { this.init() }
    private var pendingAccountUsername = ""
    private var invalidAccountRevertAtMs = 0L

    // ------------------------------------------------------------------
    // Init / layout
    // ------------------------------------------------------------------

    override fun init() {
        super.init()
        reloadView()
        initHeaderRow()
        initBandAndFooter()
        rebuildCards()
        recomputeLayout()
        reattachOverlays()
        watcher.stop()
        watcher.start()
    }

    /** Header row (over the grid panel): account field + import buttons, clamped so the
     *  row never reaches the title zone at the top-left (verified by layout_check script). */
    private fun initHeaderRow() {
        val addFileWidth = font.width(Component.translatable("simpleskinswapper.screen.carousel.add_from_file")) + 20
        val addAccountWidth = font.width(Component.translatable("simpleskinswapper.screen.carousel.add_from_account")) + 20
        val titleZoneLimit = STRIP_X + 110 + 4
        val accountWidth = Math.min(
            120,
            Math.min(
                this.width / 4,
                this.width - PAD - addFileWidth - 6 - addAccountWidth - 6 - titleZoneLimit
            )
        ).coerceAtLeast(MIN_FIELD_WIDTH)
        var x = this.width - PAD - addFileWidth
        addFromFileButton = Button.builder(
            Component.translatable("simpleskinswapper.screen.carousel.add_from_file")
        ) { addSkinFromFile() }.bounds(x, HEADER_Y, addFileWidth, HEADER_HEIGHT).build()
        x -= addAccountWidth + 6
        addFromAccountButton = Button.builder(
            Component.translatable("simpleskinswapper.screen.carousel.add_from_account")
        ) { addSkinFromAccount() }.bounds(x, HEADER_Y, addAccountWidth, HEADER_HEIGHT).build()
        x -= accountWidth + 6
        accountField = EditBox(
            font, x, HEADER_Y, accountWidth, HEADER_HEIGHT,
            Component.translatable("simpleskinswapper.screen.carousel.account_name")
        )
        accountField.setHint(Component.translatable("simpleskinswapper.screen.carousel.account_name"))
        accountField.setResponder { text -> addFromAccountButton.active = text.isNotBlank() }
        addFromAccountButton.active = false
        addRenderableWidget(accountField)
        addRenderableWidget(addFromAccountButton)
        addRenderableWidget(addFromFileButton)
    }

    /** Band widgets, page footer and the category-creation button under the tab strip. */
    private fun initBandAndFooter() {
        addRenderableWidget(band.nameField)
        addRenderableWidget(band.wheelsMinus)
        addRenderableWidget(band.wheelsPlus)
        addRenderableWidget(band.deleteButton)

        // Footer centered within the grid panel so it never collides with the strip's + button.
        val footerY = this.height - 24
        val bw = 110
        val panelW = this.width - PAD - panelX
        val left = panelX + Math.max(0, (panelW - (bw * 3 + 8)) / 2)
        addRenderableWidget(
            Button.builder(
                Component.translatable("simpleskinswapper.screen.carousel.open_folder")
            ) {
                Util.getPlatform().openFile(FabricLoader.getInstance().gameDir.resolve("skins").toFile())
            }.bounds(left, footerY, bw, 20).build()
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
            }.bounds(left + bw + 4, footerY, bw, 20).build()
        )
        addRenderableWidget(
            Button.builder(CommonComponents.GUI_DONE) { onClose() }
                .bounds(left + (bw + 4) * 2, footerY, bw, 20).build()
        )

        // Category creation: pinned under the tab strip, aligned on the footer row (same
        // height, same row) and sized to stay clear of the panel-centered footer buttons.
        addRenderableWidget(
            Button.builder(Component.literal("+")) {
                val category = SkinCategoriesStore.addCategory(
                    Component.translatable("simpleskinswapper.screen.library.add_category").string,
                    SkinCategoryPalette.DEFAULT_HEX
                )
                selectCategory(category)
                band.expanded = true
                band.refreshWidgets()
            }.bounds(STRIP_X, this.height - 24, TAB_W + 4, 20).build()
        )

        // Category deletion confirm overlay buttons live in the band (rendered + routed manually).
        addRenderableWidget(band.confirmOverlayButton)
        addRenderableWidget(band.cancelOverlayButton)
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
        gridBottom = this.height - 28
        // The grid lives inside the page's baked border (8px, measured on the texture)
        // plus a small breathing margin on every side — cards never touch the border.
        val gridLeft = gridLeft()
        val gridRight = gridRight()
        val gridW = gridRight - gridLeft
        val gap = GRID_GAP
        cols = ((gridW - gap) / (MIN_CELL_W + gap)).toInt().coerceIn(3, MAX_COLS)
        cellW = (gridW - gap * (cols - 1)) / cols
        val viewH = gridBottom - gridTop
        cellH = Math.min(cellW * 4 / 3, viewH - GRID_MARGIN * 2).coerceAtLeast(MIN_CELL_H)
        val totalW = cols * cellW + gap * (cols - 1)
        gridOffsetX = gridLeft + (gridW - totalW) / 2
        updateMaxScroll()
    }

    private fun updateMaxScroll() {
        // The config band scrolls away with the content, so it counts toward it.
        // The trailing "+" card occupies one extra cell after the last skin.
        val bandH = if (selectedCategory != null) band.height(true) + GRID_GAP else 0
        val rows = Math.ceil((cards.size + 1) / cols.toDouble()).toInt()
        val contentH = bandH + rows * (cellH + GRID_GAP) - GRID_GAP
        maxScroll = Math.max(0, contentH - (gridBottom - gridTop - GRID_MARGIN * 2))
    }

    /** Card-area inner edges: the page's baked border plus the grid margin. The config band uses them too. */
    internal fun gridLeft(): Int = panelX - 6 + PAGE_BORDER + GRID_MARGIN

    internal fun gridRight(): Int = this.width - PAD - PAGE_BORDER - GRID_MARGIN

    /** Top of the config band inside the viewport; scrolls away with the card content. */

    /** Unscrolled Y where the first grid row sits (right under the band when it is shown). */
    private fun contentStartY(): Int =
        gridTop + GRID_MARGIN + (if (selectedCategory != null) band.height(selectedCategory != null) + GRID_GAP else 0)

    /** Import row (y=8, height 20) plus a 4px margin. */
    private fun contentTop(): Int = HEADER_Y + HEADER_HEIGHT + 4

    private val panelX: Int get() = STRIP_X + TAB_W + 8

    // ------------------------------------------------------------------
    // View data
    // ------------------------------------------------------------------

    private fun reloadView() {
        entries.clear()
        val category = selectedCategory
        if (category == null) {
            entries.addAll(SkinEntry.loadSkins())
        } else {
            val byName = SkinEntry.loadSkins().associateBy { it.file.name }
            for (name in category.skins) {
                byName[name]?.let { entries.add(it) }
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
    }

    fun indexOfCard(card: SkinLibraryCard): Int = cards.indexOf(card)

    /** Category-color ARGB for the allocation marker, or null when the card is not allocated. */
    fun allocationColorFor(card: SkinLibraryCard): Int? {
        val category = selectedCategory ?: return null
        val idx = cards.indexOf(card)
        return if (idx in 0 until category.maxWheels * WHEEL_SIZE) SkinCategoryPalette.parse(category.colorHex) else null
    }

    fun deleteEntry(entry: SkinEntry) {
        watcher.markSelfTriggered(entry.file.name)
        if (!entry.file.delete()) {
            watcher.unmarkSelfTriggered(entry.file.name)
            SimpleSkinSwapper.LOGGER.warn("Could not delete skin file {}.", entry.file.name)
            return
        }
        SkinTypeStore.removeType(entry.file.name)
        SkinNameStore.removeName(entry.file.name)
        SkinCategoriesStore.removeFromAll(entry.file.name)
        reloadView()
        rebuildCards()
    }

    /** Renames a skin file (no extension), migrating the per-file stores. */
    fun renameEntry(entry: SkinEntry, newName: String): Boolean {
        val sanitized = newName.replace(Regex("[^A-Za-z0-9_\\- ]"), "_").trim()
        if (sanitized.isEmpty()) return false
        val target = File(entry.file.parentFile, "$sanitized.png")
        if (target.path == entry.file.path) return false
        if (target.exists()) return false
        val oldName = entry.file.name
        watcher.markSelfTriggered(oldName)
        watcher.markSelfTriggered(target.name)
        if (!entry.file.renameTo(target)) {
            SimpleSkinSwapper.LOGGER.warn("Could not rename skin file {}.", oldName)
            return false
        }
        SkinTypeStore.renameType(oldName, target.name)
        SkinNameStore.renameKey(oldName, target.name)
        SkinCategoriesStore.renameInAll(oldName, target.name)
        entry.file = target
        entry.textureId = null
        entry.textureLoading = false
        reloadView()
        rebuildCards()
        return true
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

    /** Copies a staged skin into skins/ and registers its stores. False on collision/IO error. */
    fun confirmAddSkin(source: File, name: String, display: String, type: SkinType): Boolean {
        val sanitized = name.replace(Regex("[^A-Za-z0-9_\\- ]"), "_").trim()
        if (sanitized.isEmpty()) return false
        val skinsDir = FabricLoader.getInstance().gameDir.resolve("skins")
        return try {
            Files.createDirectories(skinsDir)
            val target = skinsDir.resolve("$sanitized.png")
            if (Files.exists(target)) return false
            watcher.markSelfTriggered(target.fileName.toString())
            Files.copy(source.toPath(), target, StandardCopyOption.REPLACE_EXISTING)
            SkinTypeStore.setType(target.fileName.toString(), type)
            if (display.isNotBlank()) SkinNameStore.setName(target.fileName.toString(), display)
            reloadView()
            rebuildCards()
            true
        } catch (e: IOException) {
            SimpleSkinSwapper.LOGGER.warn("Could not add skin {}: {}", sanitized, e.message)
            false
        }
    }

    /** Re-points the detail panel at the fresh entry after a reload, closing it if the skin is gone. */
    private fun rebindDetail() {
        val d = detail ?: return
        val name = d.entryFileName ?: return
        val fresh = entries.firstOrNull { it.file.name == name }
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

        //? if >=26.1 {
        super.extractRenderState(graphics, mouseX, mouseY, delta)
        //?} else {
        /*super.render(graphics, mouseX, mouseY, delta)
        *///?}

        // While the detail overlay is open, the base screen stays static underneath:
        // no tab/selection chrome may draw over it (the panel renders itself via super).
        if (detail == null && addPanel == null) {
            // Selected tab sticks out over the page edge, above the cards zone.
            drawTabStripOver(graphics, mouseX, mouseY)

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
            graphics.text(client.font, Component.translatable("simpleskinswapper.title"), STRIP_X, HEADER_Y + (HEADER_HEIGHT - 8) / 2, 0xFFFFFFFF.toInt())

            if (cards.isEmpty()) {
                val messageKey = if (selectedCategory == null) {
                    "simpleskinswapper.screen.carousel.no_skins"
                } else {
                    "simpleskinswapper.screen.library.empty_category"
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

            if (band.confirmingDelete) {
                band.drawDeleteOverlay(graphics, mouseX, mouseY, delta)
            }

            // Tooltip for hovered tab
            if (tabs.tabDragCategoryIndex == -1 && reorderDraggingCard == null && !band.confirmingDelete) {
                val tab = tabs.tabAt(mouseY, mouseX)
                if (tab != null) {
                    val label = if (tab == 0) Component.translatable("simpleskinswapper.screen.library.all_skins")
                    else Component.nullToEmpty(SkinCategoriesStore.all()[tab - 1].name)
                    drawTooltip(graphics, mouseX, mouseY, label)
                }
            }
        }
    }

    /** Tab strip background + unselected tabs, drawn before the grid page so they pass under it. */
    private fun drawTabStripUnder(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val top = tabs.stripTop()
        val bottom = tabs.stripBottom()

        // Tab zone background: the recipe-book frame sprite, darkened, its left border bleeding
        // off the screen edge and its right side sliding underneath the grid page.
        drawBookPanel(graphics, -PANEL_BLEED, top, STRIP_X + TAB_W + 2 + PANEL_BLEED + 8, bottom - top, lit = false)

        // Unselected tabs, clipped to the strip — All Skins is a tab like the others.
        graphics.enableScissor(STRIP_X, top, STRIP_X + TAB_W + 2, bottom)
        for (i in 0..SkinCategoriesStore.all().size) {
            val y = tabs.tabY(i)
            if (y + TAB_H < top || y > bottom) continue
            if (isSelectedTab(i)) continue
            drawTab(graphics, i, y, mouseX, mouseY)
        }
        graphics.disableScissor()
    }

    /** Selected + dragged tab and the insertion line, drawn after the grid page so they overlap it. */
    private fun drawTabStripOver(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val top = tabs.stripTop()
        val bottom = tabs.stripBottom()

        // Selected tab: full-color book panel, flush left, its right edge tucking slightly under
        // the grid page border. Clipped vertically to the strip so it scrolls away like the
        // other tabs, while still overflowing to the right over the page.
        val selected = selectedTabIndex()
        if (selected >= 0) {
            val y = tabs.tabY(selected)
            if (y + TAB_H >= top && y <= bottom) {
                graphics.enableScissor(-PANEL_BLEED, top, STRIP_X + TAB_W + TAB_SELECTED_STICKOUT, bottom)
                drawBookPanel(graphics, -PANEL_BLEED, y, STRIP_X + TAB_W + TAB_SELECTED_STICKOUT + PANEL_BLEED, TAB_H, lit = true)
                drawTabContent(graphics, selected, y)
                graphics.disableScissor()
            }
        }

        // Dragged tab follows the cursor vertically as a floating full-color panel,
        // clipped to the strip zone the same way.
        if (tabs.tabDragActive && tabs.tabDragCategoryIndex > 0) {
            val y = (tabs.tabDragCursorY - TAB_H / 2).coerceIn(top, bottom - TAB_H)
            graphics.enableScissor(-PANEL_BLEED, top, STRIP_X + TAB_W + TAB_SELECTED_STICKOUT, bottom)
            drawBookPanel(graphics, -PANEL_BLEED, y, STRIP_X + TAB_W + TAB_SELECTED_STICKOUT + PANEL_BLEED, TAB_H, lit = true)
            drawTabContent(graphics, tabs.tabDragCategoryIndex, y)
            graphics.disableScissor()
        }
        // Insertion line: after [tabInsertionIndex] categories (pre-removal space).
        if (tabs.tabDragActive && tabs.tabInsertionIndex >= 0) {
            val lineY = tabs.insertionLineY()
            if (lineY >= top && lineY <= bottom) {
                graphics.fill(STRIP_X, lineY - 1, STRIP_X + TAB_W, lineY + 1, 0xFFFFFFFF.toInt())
            }
        }
    }

    private fun isSelectedTab(index: Int): Boolean =
        if (index == 0) selectedCategory == null else selectedCategory === SkinCategoriesStore.all().getOrNull(index - 1)

    private fun selectedTabIndex(): Int {
        val category = selectedCategory ?: return 0
        val idx = SkinCategoriesStore.all().indexOf(category)
        return if (idx >= 0) idx + 1 else -1
    }

    private fun drawTab(graphics: GuiGraphicsExtractor, index: Int, y: Int, mouseX: Int, mouseY: Int) {
        val hovered = mouseX >= STRIP_X && mouseX < STRIP_X + TAB_W && mouseY >= y && mouseY < y + TAB_H
        if (hovered) {
            graphics.fill(STRIP_X, y, STRIP_X + TAB_W, y + TAB_H, 0x30FFFFFF)
        }
        drawTabContent(graphics, index, y)
    }

    private fun drawTabContent(graphics: GuiGraphicsExtractor, index: Int, y: Int) {
        val label = if (index == 0) Component.translatable("simpleskinswapper.screen.library.all_skins")
        else Component.nullToEmpty(SkinCategoriesStore.all().getOrNull(index - 1)?.name ?: "")
        val nameX = if (index == 0) STRIP_X + 6 else STRIP_X + 16
        val nameRight = STRIP_X + TAB_W - 3
        val textY = y + (TAB_H - font.lineHeight) / 2
        // Clip the name to the tab; guard the scissor (zero-size scissors crash MC 26.2).
        if (nameRight - nameX >= 8) {
            graphics.enableScissor(nameX, y + 2, nameRight, y + TAB_H - 2)
            graphics.text(client.font, Component.nullToEmpty(label.string), nameX, textY, 0xFFFFFFFF.toInt())
            graphics.disableScissor()
        }
        if (index > 0) {
            SkinCategoriesStore.all().getOrNull(index - 1)?.let {
                val s = 8
                val x0 = STRIP_X + 4
                // Center the square on the glyphs' optical center (same line as the text),
                // not on the full tab height — the 9px font renders in the top 7px of its line.
                val y0 = textY + (font.lineHeight - s) / 2
                graphics.fill(x0, y0, x0 + s, y0 + s, SkinCategoryPalette.parse(it.colorHex))
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

    // Complexity debt: drag/easing dispatch over cards, add card and band — deferred
    // to the SkinLibraryScreen Extract Class refactoring change.
    @Suppress("CyclomaticComplexMethod")
    private fun updateCardPositions(mouseX: Int, mouseY: Int) {
        // Band widgets track the scrolled band position every frame.
        band.refreshWidgets()

        val dragged = reorderDraggingCard
        val dragIndex = dragged?.let { cards.indexOf(it) } ?: -1
        cardDrag.updateInsertionIndex(cards.size, mouseX, mouseY)

        val now = System.nanoTime()
        val dt = if (lastCardEaseNanos == 0L) 1.0F else ((now - lastCardEaseNanos) / 1_000_000_000.0F).coerceAtMost(0.1F)
        val t = 1.0F - Math.exp((-CARD_SLIDE_SPEED * dt).toDouble()).toFloat()

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

        // Drop on a tab = cross-category move / unassign.
        val tab = tabs.tabAt(mouseY, mouseX)
        if (tab != null) {
            if (tab == 0 && selectedCategory != null) {
                // Dragging from a category onto All skins = unassign; the file stays in the folder.
                SkinCategoriesStore.removeFromAll(card.entry.file.name)
            } else if (tab > 0) {
                val target = SkinCategoriesStore.all().getOrNull(tab - 1)
                if (target != null && target !== selectedCategory) {
                    SkinCategoriesStore.assignSkin(target, card.entry.file.name)
                }
            }
            reloadView()
            rebuildCards()
            return
        }

        // Grid drop = reorder within the current category (the All view has no explicit order).
        val category = selectedCategory
        if (category != null && cardDrag.insertionIndex in 0..category.skins.size) {
            val from = category.skins.indexOf(card.entry.file.name)
            if (from >= 0) {
                var to = cardDrag.insertionIndex
                if (to > from) to--
                category.skins.removeAt(from)
                category.skins.add(to.coerceIn(0, category.skins.size), card.entry.file.name)
                SkinCategoriesStore.save()
            }
        }
        cardDrag.stop()
        rebuildCards()
    }

    // ------------------------------------------------------------------
    // Tab drag & auto-scroll
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        handleOverlayClick(click, doubled)?.let { return it }

        val mx = click.x().toInt()
        val my = click.y().toInt()
        if (handleChromeClick(mx, my, click, doubled)) return true

        // Grid wheel-scroll area click-through: let children (cards, widgets) handle the rest.
        val result = super.mouseClicked(click, doubled)
        // Deferred: unregister the reorder-dragged card so it only renders via the manual
        // floating pass (and no longer swallows mouse input) once child iteration is over.
        reorderDraggingCard?.let { removeWidget(it) }
        return result
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

    /** Non-card chrome: delete confirmation, tab strip, category band, empty-category zone. */
    private fun handleChromeClick(mx: Int, my: Int, click: MouseButtonEvent, doubled: Boolean): Boolean {
        if (band.confirmingDelete) {
            handleDeleteConfirmClick(click, doubled)
            return true
        }

        // Tab strip: select on click, start a potential drag on press (-1 = none, 0 = All, >0 = category).
        val tab = tabs.tabAt(my, mx)
        if (tab != null && click.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            tabs.press(tab, click.y(), my)
            return true
        }

        // Category band: bar click toggles expansion, swatch click recolors. The part of
        // the band scrolled above the viewport top is not clickable, matching its clipping.
        if (band.handleBarClick(mx, my)) return true
        if (band.handleSwatchClick(mx, my)) return true

        // Empty category: a click anywhere in the card zone opens the add-skin overlay.
        val inGrid = mx >= gridLeft() && mx < gridRight() && my >= gridTop && my < gridBottom
        val emptyCategoryClick = selectedCategory != null && cards.isEmpty() &&
            click.button() == InputConstants.MOUSE_BUTTON_LEFT && inGrid
        if (emptyCategoryClick) {
            openAddPanel()
            return true
        }
        return false
    }

    /** While the delete confirmation overlay is up it swallows every click. */
    private fun handleDeleteConfirmClick(click: MouseButtonEvent, doubled: Boolean) {
        if (band.confirmOverlayButton.mouseClicked(click, doubled) || band.cancelOverlayButton.mouseClicked(click, doubled)) {
            return
        }
    }

    internal fun confirmCategoryDelete() {
        val category = selectedCategory
        band.confirmingDelete = false
        if (category != null) {
            SkinCategoriesStore.removeCategory(category)
            selectCategory(null)
        }
    }

    private fun selectCategory(category: SkinCategory?) {
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
        return super.mouseDragged(click, offsetX, offsetY)
    }

    override fun mouseReleased(click: MouseButtonEvent): Boolean {
        detail?.let { return it.mouseReleased(click) }
        addPanel?.let { return it.mouseReleased(click) }
        val mx = click.x().toInt()
        val my = click.y().toInt()
        if (tabs.tabDragCategoryIndex >= 0) {
            when (val result = tabs.release(click.button() == InputConstants.MOUSE_BUTTON_LEFT)) {
                is TabStripController.Release.Move -> {
                    SkinCategoriesStore.moveCategory(result.from, result.to)
                    rebuildCards()
                }
                is TabStripController.Release.Select -> {
                    if (result.tabIndex == 0) selectCategory(null)
                    else selectCategory(SkinCategoriesStore.all().getOrNull(result.tabIndex - 1))
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
            tabs.scrollBy(vertAmount.toFloat() * TAB_H)
            return true
        }
        scrollY = Mth.clamp(scrollY - (vertAmount * (cellH + GRID_GAP)).toInt(), 0, maxScroll)
        return true
    }

    override fun keyPressed(event: KeyEvent): Boolean {
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

        if (invalidAccountRevertAtMs != 0L && System.currentTimeMillis() >= invalidAccountRevertAtMs) {
            invalidAccountRevertAtMs = 0L
            accountField.setTextColor(DEFAULT_TEXT_COLOR)
            accountField.setValue(pendingAccountUsername)
            addFromAccountButton.active = accountField.value.isNotBlank()
        }

        watcher.pollChanges()
    }

    private fun addSkinFromFile() {
        pickSkinFile { importSkinFile(it) }
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

    private fun importSkinFile(source: File) {
        try {
            val skinsDir = FabricLoader.getInstance().gameDir.resolve("skins")
            Files.createDirectories(skinsDir)
            val dest = AccountSkinFetcher.uniqueFile(skinsDir.resolve(source.name))
            watcher.markSelfTriggered(dest.fileName.toString())
            Files.copy(source.toPath(), dest)
            addImportedEntry(dest.toFile())
        } catch (e: IOException) {
            SimpleSkinSwapper.LOGGER.warn("Could not import skin file {}: {}", source.name, e.message)
        }
    }

    private fun addSkinFromAccount() {
        val username = accountField.value
        if (username.isBlank()) return

        val skinsDir = FabricLoader.getInstance().gameDir.resolve("skins")
        val destination: Path
        try {
            Files.createDirectories(skinsDir)
            destination = AccountSkinFetcher.uniqueFile(
                skinsDir.resolve(AccountSkinFetcher.sanitizeFilename(username) + ".png")
            )
        } catch (e: IOException) {
            SimpleSkinSwapper.LOGGER.warn("Could not prepare skin destination for {}: {}", username, e.message)
            return
        }
        val destName = destination.fileName.toString()
        watcher.markSelfTriggered(destName)

        addFromAccountButton.active = false
        AccountSkinFetcher.fetch(
            username, destination,
            { file ->
                addImportedEntry(file)
                addFromAccountButton.active = accountField.value.isNotBlank()
            },
            {
                watcher.unmarkSelfTriggered(destName)
                showInvalidAccount(username)
            }
        )
    }

    private fun showInvalidAccount(previousText: String) {
        pendingAccountUsername = previousText
        accountField.setTextColor(ERROR_COLOR)
        accountField.setValue(Component.translatable("simpleskinswapper.screen.carousel.invalid_account").string)
        invalidAccountRevertAtMs = System.currentTimeMillis() + INVALID_ACCOUNT_MESSAGE_MS
        addFromAccountButton.active = accountField.value.isNotBlank()
    }

    private fun addImportedEntry(file: File) {
        // Imported skins appear unassigned in All skins; the category store is untouched.
        reloadView()
        rebuildCards()
    }

    companion object {
        private const val PAD = 4
        private const val HEADER_Y = 8
        private const val HEADER_HEIGHT = 20
        private const val MIN_FIELD_WIDTH = 40

        internal const val STRIP_X = 4
        internal const val TAB_W = 100
        internal const val TAB_H = 28

        // How far the selected tab's panel tucks under the grid page border (its right edge is
        // this many px past the tab column).
        private const val TAB_SELECTED_STICKOUT = 6

        // Left offset the tab panels are drawn from, so their left border sits off-screen
        // (the overlay_recipe nine-slice border is 4px).
        private const val PANEL_BLEED = 4

        private const val GRID_GAP = 6
        private const val MIN_CELL_W = 64.0
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

        // Vanilla EditBox default text color; restored after the invalid-account error flash.
        private val DEFAULT_TEXT_COLOR = 0xFFE0E0E0.toInt()
        private val ERROR_COLOR = 0xFFFF5555.toInt()
        private const val INVALID_ACCOUNT_MESSAGE_MS = 1500L
        private const val WHEEL_SIZE = 10
    }
}
