package fr.raconteur.simpleskinswapper.gui.library

import com.mojang.blaze3d.platform.InputConstants
import fr.raconteur.simpleskinswapper.SimpleSkinSwapper
import fr.raconteur.simpleskinswapper.changeskin.AccountSkinFetcher
import fr.raconteur.simpleskinswapper.gui.EdgeSafeButtonWidget
import fr.raconteur.simpleskinswapper.gui.SkinEntry
import fr.raconteur.simpleskinswapper.gui.SkinTypeStore
import fr.raconteur.simpleskinswapper.gui.config.YaclConfigScreen
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
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
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchService
import java.util.IdentityHashMap

/**
 * Category-based skin library: a vertical category tab strip on the left (pinned "All skins"
 * tab, scrollable, drag-reorderable with edge auto-scroll) and a responsive, vertically
 * scrolling grid of skin cards on the right, plus a collapsible per-category config band.
 * Replaces the old horizontal carousel screen.
 */
class SkinLibraryScreen(private val parent: Screen?) : Screen(Component.translatable("simpleskinswapper.title")) {

    private val client get() = minecraft

    // ------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------

    private val entries = ArrayList<SkinEntry>()
    private val cards = ArrayList<SkinLibraryCard>()

    /** Currently selected view: null category = the pinned All skins view. */
    private var selectedCategory: SkinCategory? = null
    private var bandExpanded = false

    // Card reorder drag (started by a card's frame/handle zone).
    internal var reorderDraggingCard: SkinLibraryCard? = null
    internal var dragRotatingCard: SkinLibraryCard? = null
    private var reorderGrabX = 0
    private var reorderGrabY = 0
    private var reorderCursorX = 0
    private var reorderCursorY = 0
    private var insertionIndex = -1
    private val cardDisplay = IdentityHashMap<SkinLibraryCard, FloatArray>()

    // Tab drag-reorder state (5px click-vs-drag threshold).
    private var tabDragCategoryIndex = -1
    private var tabDragActive = false
    private var tabDragStartY = 0.0
    private var tabDragCursorY = 0
    private var tabInsertionIndex = -1
    private var tabAutoScrollNanos = 0L
    private var tabScroll = 0.0F

    // Category deletion confirmation overlay (rendered manually, not registered widgets).
    private var confirmingCategoryDelete = false
    private lateinit var overlayConfirmButton: EdgeSafeButtonWidget
    private lateinit var overlayCancelButton: EdgeSafeButtonWidget

    // Grid scroll + layout (recomputed in recomputeLayout()).
    private var scrollY = 0
    private var cols = 3
    private var cellW = 0
    private var cellH = 0
    private var gridOffsetX = 0
    private var gridTop = 0
    private var gridBottom = 0
    private var maxScroll = 0

    // Widgets
    private lateinit var accountField: EditBox
    private lateinit var addFromFileButton: Button
    private lateinit var addFromAccountButton: Button
    private lateinit var bandNameField: EditBox
    private lateinit var bandWheelsMinus: EdgeSafeButtonWidget
    private lateinit var bandWheelsPlus: EdgeSafeButtonWidget
    private lateinit var bandDeleteButton: EdgeSafeButtonWidget

    private val selfTriggeredFiles = HashMap<String, Long>()
    private var watchService: WatchService? = null
    private var pendingAccountUsername = ""
    private var invalidAccountRevertAtMs = 0L

    // ------------------------------------------------------------------
    // Init / layout
    // ------------------------------------------------------------------

    override fun init() {
        super.init()
        reloadView()

        // Header row (over the grid panel): account field + import buttons, clamped so the
        // row never reaches the title zone at the top-left (verified by layout_check script).
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

        // Category config band widgets (visible only when a category is selected).
        bandNameField = EditBox(
            font, 0, 0, BAND_NAME_WIDTH, BAND_FIELD_HEIGHT,
            Component.translatable("simpleskinswapper.screen.library.category_name")
        )
        bandNameField.setMaxLength(32)
        bandNameField.setResponder { text -> onCategoryRenamed(text) }
        addRenderableWidget(bandNameField)

        bandWheelsMinus = EdgeSafeButtonWidget(0, 0, 20, BAND_FIELD_HEIGHT, Component.literal("-")) {
            selectedCategory?.let { it.maxWheels = (it.maxWheels - 1).coerceAtLeast(0); SkinCategoriesStore.save() }
        }
        bandWheelsPlus = EdgeSafeButtonWidget(0, 0, 20, BAND_FIELD_HEIGHT, Component.literal("+")) {
            selectedCategory?.let { it.maxWheels = (it.maxWheels + 1).coerceAtLeast(0); SkinCategoriesStore.save() }
        }
        bandDeleteButton = EdgeSafeButtonWidget(0, 0, 20, BAND_FIELD_HEIGHT, Component.literal("✕")) {
            confirmingCategoryDelete = true
        }
        addRenderableWidget(bandWheelsMinus)
        addRenderableWidget(bandWheelsPlus)
        addRenderableWidget(bandDeleteButton)

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
                bandExpanded = true
                refreshBandWidgets()
            }.bounds(STRIP_X, this.height - 24, TAB_W + 4, 20).build()
        )

        // Category deletion confirm overlay buttons (rendered + routed manually).
        overlayConfirmButton = EdgeSafeButtonWidget(0, 0, 100, 20, Component.translatable(
            "simpleskinswapper.screen.library.delete_category_confirm")) { confirmCategoryDelete() }
        overlayCancelButton = EdgeSafeButtonWidget(0, 0, 100, 20, CommonComponents.GUI_CANCEL) {
            confirmingCategoryDelete = false
        }

        rebuildCards()
        recomputeLayout()

        stopWatching()
        startWatching()
    }

    private fun recomputeLayout() {
        // Content starts below the import row (8..28) with a margin, and the grid always
        // keeps a margin under whatever sits above it (import row or config band) — both
        // the strip and the grid align on this top edge (layout_check script mirrors this).
        gridTop = contentTop() + bandHeight() + BAND_GRID_MARGIN
        gridBottom = this.height - 28
        val panelLeft = panelX
        val panelW = this.width - PAD - panelLeft
        val gap = GRID_GAP
        cols = ((panelW - gap) / (MIN_CELL_W + gap)).toInt().coerceIn(3, MAX_COLS)
        cellW = (panelW - gap * (cols - 1)) / cols
        cellH = Math.min(cellW * 4 / 3, gridBottom - gridTop - gap * 2).coerceAtLeast(MIN_CELL_H)
        val totalW = cols * cellW + gap * (cols - 1)
        gridOffsetX = panelLeft + (panelW - totalW) / 2
        updateMaxScroll()
    }

    private fun updateMaxScroll() {
        val rows = Math.ceil(cards.size / cols.toDouble()).toInt()
        val contentH = rows * (cellH + GRID_GAP) - GRID_GAP
        maxScroll = Math.max(0, contentH - (gridBottom - gridTop))
    }

    private fun bandHeight(): Int = if (selectedCategory == null) 0 else if (bandExpanded) BAND_EXPANDED_H else BAND_COLLAPSED_H

    private fun bandY(): Int = contentTop()

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
        recomputeLayout()
        for (entry in entries) {
            val card = SkinLibraryCard(this, entry, cellW, cellH)
            cards.add(card)
            addRenderableWidget(card)
        }
        updateMaxScroll()
        scrollY = Mth.clamp(scrollY, 0, maxScroll)
        refreshBandWidgets()
    }

    fun indexOfCard(card: SkinLibraryCard): Int = cards.indexOf(card)

    /** Category-color ARGB for the allocation marker, or null when the card is not allocated. */
    fun allocationColorFor(card: SkinLibraryCard): Int? {
        val category = selectedCategory ?: return null
        val idx = cards.indexOf(card)
        return if (idx in 0 until category.maxWheels * WHEEL_SIZE) SkinCategoryPalette.parse(category.colorHex) else null
    }

    fun deleteEntry(entry: SkinEntry) {
        markSelfTriggered(entry.file.name)
        if (!entry.file.delete()) {
            selfTriggeredFiles.remove(entry.file.name)
            SimpleSkinSwapper.LOGGER.warn("Could not delete skin file {}.", entry.file.name)
            return
        }
        SkinTypeStore.removeType(entry.file.name)
        SkinCategoriesStore.removeFromAll(entry.file.name)
        reloadView()
        rebuildCards()
    }

    // ------------------------------------------------------------------
    // Band widgets
    // ------------------------------------------------------------------

    private fun refreshBandWidgets() {
        val category = selectedCategory
        val visible = category != null && bandExpanded
        bandNameField.visible = visible
        bandWheelsMinus.visible = visible
        bandWheelsPlus.visible = visible
        bandDeleteButton.visible = visible
        if (category != null && visible) {
            // Two-column layout verified by the layout_check script: swatches left,
            // name field and wheel stepper right, delete at the far right — no overlaps.
            val by = bandY()
            val swatchW = 10 * (BAND_SWATCH_SIZE + BAND_SWATCH_GAP) - BAND_SWATCH_GAP
            val x2 = panelX + 8 + swatchW + 12
            val nameWidth = Math.min(BAND_NAME_WIDTH, this.width - PAD - 24 - 8 - x2)
            bandNameField.setWidth(nameWidth)
            bandNameField.setX(x2); bandNameField.setY(by + 24)
            if (bandNameField.value != category.name) bandNameField.value = category.name
            bandWheelsMinus.setX(x2); bandWheelsMinus.setY(by + 44)
            bandWheelsPlus.setX(x2 + 36); bandWheelsPlus.setY(by + 44)
            bandDeleteButton.setX(this.width - PAD - 24); bandDeleteButton.setY(by + 44)
        }
    }

    private fun onCategoryRenamed(text: String) {
        val category = selectedCategory ?: return
        val trimmed = text.trim()
        if (trimmed.isNotEmpty() && trimmed != category.name) {
            category.name = trimmed
            SkinCategoriesStore.save()
        }
    }

    // ------------------------------------------------------------------
    // Tab strip geometry
    // ------------------------------------------------------------------

    private fun stripTop(): Int = gridTop

    private fun stripBottom(): Int = this.height - 28

    /** Y of tab [index]: 0 = All (pinned), i>0 = category i-1. Accounts for the insertion gap. */
    private fun tabY(index: Int): Int {
        if (index == 0) return stripTop()
        val storeIdx = index - 1
        var y = stripTop() + TAB_H + storeIdx * TAB_H
        // The insertion gap opens after category [tabInsertionIndex], shifting later tabs down.
        if (tabDragActive && tabInsertionIndex >= 0 && storeIdx >= tabInsertionIndex) y += TAB_H
        return y - tabScroll.toInt()
    }
    private fun maxTabScroll(): Int =
        Math.max(0, SkinCategoriesStore.all().size * TAB_H - (stripBottom() - stripTop() - TAB_H))

    /** Tab under the cursor, accounting for the insertion gap; null when none. 0 = All, i>0 = category i-1. */
    private fun tabAt(cursorY: Int, cursorX: Int): Int? {
        if (cursorX < STRIP_X || cursorX > STRIP_X + TAB_W + 4) return null
        if (cursorY < stripTop() || cursorY > stripBottom()) return null
        if (cursorY < stripTop() + TAB_H) return 0
        for (i in 1..SkinCategoriesStore.all().size) {
            val top = tabY(i)
            if (cursorY >= top && cursorY < top + TAB_H) return i
        }
        return null
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    //? if >=26.1 {
    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
    //?} else {
    /*override fun render(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
    *///?}
        graphics.fill(0, 0, this.width, this.height, 0x66000000)

        updateTabAutoScroll(mouseY)

        // Config band background (behind its widgets, which render via super).
        val category = selectedCategory
        if (category != null) {
            val by = bandY()
            val bh = bandHeight()
            graphics.fill(panelX, by, this.width - PAD, by + bh, 0x50101826)
            val arrow = if (bandExpanded) "▾" else "▸"
            val wheelsLabel = Component.translatable("simpleskinswapper.screen.library.wheels").string
            graphics.text(client.font, Component.nullToEmpty("$arrow ${category.name} · ${entries.size} · ${category.maxWheels} $wheelsLabel"), panelX + 8, by + (BAND_COLLAPSED_H - font.lineHeight) / 2, 0xFFFFFFFF.toInt())
            if (bandExpanded) {
                // Color swatch grid (10 hues × 2 rows) at the left; controls column at the right.
                // Geometry mirrored by the layout_check script — do not move without re-running it.
                val categoryColor = SkinCategoryPalette.parse(category.colorHex)
                val sx0 = panelX + 8
                val sy0 = by + 24
                for (i in SkinCategoryPalette.swatches().indices) {
                    val hue = i / 2
                    val row = i % 2
                    val sx = sx0 + hue * (BAND_SWATCH_SIZE + BAND_SWATCH_GAP)
                    val y0 = sy0 + row * (BAND_SWATCH_SIZE + BAND_SWATCH_GAP)
                    val hovered = mouseX >= sx && mouseX < sx + BAND_SWATCH_SIZE && mouseY >= y0 && mouseY < y0 + BAND_SWATCH_SIZE
                    graphics.fill(sx - 1, y0 - 1, sx + BAND_SWATCH_SIZE + 1, y0 + BAND_SWATCH_SIZE + 1,
                        if (SkinCategoryPalette.swatches()[i] == categoryColor) 0xFFFFFFFF.toInt()
                        else if (hovered) 0xFF606060.toInt() else 0xFF202020.toInt())
                    graphics.fill(sx, y0, sx + BAND_SWATCH_SIZE, y0 + BAND_SWATCH_SIZE, SkinCategoryPalette.swatches()[i])
                }
                // Wheel stepper label, right of the [-] count [+] cluster
                val x2 = sx0 + 10 * (BAND_SWATCH_SIZE + BAND_SWATCH_GAP) - BAND_SWATCH_GAP + 12
                graphics.text(client.font, Component.translatable("simpleskinswapper.screen.library.wheels"), x2 + 58, by + 48, 0xFFB0B8C0.toInt())
            }
        }

        //? if >=26.1 {
        super.extractRenderState(graphics, mouseX, mouseY, delta)
        //?} else {
        /*super.render(graphics, mouseX, mouseY, delta)
        *///?}

        updateCardPositions(mouseX, mouseY)

        drawTabStrip(graphics, mouseX, mouseY)

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
            graphics.centeredText(
                font, Component.translatable(messageKey),
                (panelX + this.width - PAD) / 2, (gridTop + gridBottom) / 2 - font.lineHeight / 2, 0xFFAAAAAA.toInt()
            )
        }

        if (confirmingCategoryDelete) {
            drawCategoryDeleteOverlay(graphics, mouseX, mouseY, delta)
        }

        // Tooltip for hovered tab
        if (tabDragCategoryIndex == -1 && reorderDraggingCard == null && !confirmingCategoryDelete) {
            val tab = tabAt(mouseY, mouseX)
            if (tab != null) {
                val label = if (tab == 0) Component.translatable("simpleskinswapper.screen.library.all_skins")
                else Component.nullToEmpty(SkinCategoriesStore.all()[tab - 1].name)
                drawTooltip(graphics, mouseX, mouseY, label)
            }
        }
    }

    private fun drawTabStrip(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val top = stripTop()
        val bottom = stripBottom()
        graphics.fill(STRIP_X - 2, top, STRIP_X + TAB_W + 2, bottom, 0x40101420)

        graphics.enableScissor(STRIP_X - 2, top, STRIP_X + TAB_W + 2, bottom)
        // All skins tab (pinned)
        drawTab(graphics, 0, tabY(0), mouseX, mouseY)
        // Category tabs
        for (i in 1..SkinCategoriesStore.all().size) {
            val y = tabY(i)
            if (y + TAB_H < top || y > bottom) continue
            drawTab(graphics, i, y, mouseX, mouseY)
        }
        graphics.disableScissor()

        // Dragged tab follows the cursor vertically, semi-transparent.
        if (tabDragActive && tabDragCategoryIndex > 0) {
            val category = SkinCategoriesStore.all()[tabDragCategoryIndex - 1]
            val y = (tabDragCursorY - TAB_H / 2).coerceAtLeast(top)
            graphics.fill(STRIP_X, y, STRIP_X + TAB_W, y + TAB_H, 0xB02B5F9E.toInt())
            drawCategoryTabContent(graphics, category, y, 0xB0 shl 24)
        }
        // Insertion line: after [tabInsertionIndex] categories (pre-removal space).
        if (tabDragActive && tabInsertionIndex >= 0) {
            val lineY = stripTop() + TAB_H + tabInsertionIndex * TAB_H - tabScroll.toInt()
            if (lineY >= stripTop() && lineY <= stripBottom()) {
                graphics.fill(STRIP_X, lineY - 1, STRIP_X + TAB_W, lineY + 1, 0xFFFFFFFF.toInt())
            }
        }
    }

    private fun drawTab(graphics: GuiGraphicsExtractor, index: Int, y: Int, mouseX: Int, mouseY: Int) {
        val isSelected = if (index == 0) selectedCategory == null else selectedCategory === SkinCategoriesStore.all().getOrNull(index - 1)
        val hovered = mouseX >= STRIP_X && mouseX < STRIP_X + TAB_W && mouseY >= y && mouseY < y + TAB_H
        val fill = when {
            isSelected -> 0xEE2B5F9E.toInt()
            hovered -> 0x802B5F9E.toInt()
            else -> 0x661A2535.toInt()
        }
        graphics.fill(STRIP_X, y, STRIP_X + TAB_W, y + TAB_H, fill)
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

    private fun drawCategoryTabContent(graphics: GuiGraphicsExtractor, category: SkinCategory, y: Int, alphaOverride: Int) {
        val baseColor = SkinCategoryPalette.parse(category.colorHex)
        val color = if (alphaOverride != -1) (alphaOverride and 0xFF shl 24) or (baseColor and 0xFFFFFF) else baseColor
        val s = 8
        val x0 = STRIP_X + 4
        val textY = y + (TAB_H - font.lineHeight) / 2
        val y0 = textY + (font.lineHeight - s) / 2
        graphics.fill(x0, y0, x0 + s, y0 + s, color)
        val textX = x0 + s + 4
        val nameRight = STRIP_X + TAB_W - 3
        if (nameRight - textX >= 8) {
            graphics.enableScissor(textX, y + 2, nameRight, y + TAB_H - 2)
            graphics.text(client.font, Component.nullToEmpty(category.name), textX, textY, 0xFFFFFFFF.toInt())
            graphics.disableScissor()
        }
    }

    private fun drawCategoryDeleteOverlay(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        graphics.fill(0, 0, this.width, this.height, 0x88000000.toInt())
        val boxW = 260
        val boxH = 80
        val bx = this.width / 2 - boxW / 2
        val by = this.height / 2 - boxH / 2
        graphics.fill(bx, by, bx + boxW, by + boxH, 0xFF1A2535.toInt())
        graphics.fill(bx, by, bx + boxW, by + 1, 0xFFFFFFFF.toInt())
        val question = Component.translatable("simpleskinswapper.screen.library.delete_category_question")
        // Simple word wrap (Component.string + font.width work on every target version).
        val wrapped = ArrayList<String>()
        var currentLine = ""
        for (word in question.string.split(" ")) {
            val candidate = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (font.width(candidate) > boxW - 16 && currentLine.isNotEmpty()) {
                wrapped.add(currentLine)
                currentLine = word
            } else {
                currentLine = candidate
            }
        }
        if (currentLine.isNotEmpty()) wrapped.add(currentLine)
        var ly = by + 8
        for (lineText in wrapped) {
            graphics.text(client.font, Component.nullToEmpty(lineText), bx + 8, ly, 0xFFFFFFFF.toInt())
            ly += font.lineHeight
        }
        overlayConfirmButton.setX(bx + 8)
        overlayConfirmButton.setY(by + boxH - 28)
        overlayCancelButton.setX(bx + boxW - 108)
        overlayCancelButton.setY(by + boxH - 28)
        //? if >=26.1 {
        overlayConfirmButton.extractRenderState(graphics, mouseX, mouseY, delta)
        overlayCancelButton.extractRenderState(graphics, mouseX, mouseY, delta)
        //?} else {
        /*overlayConfirmButton.render(graphics, mouseX, mouseY, delta)
        overlayCancelButton.render(graphics, mouseX, mouseY, delta)
        *///?}
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
        val dragged = reorderDraggingCard
        val dragIndex = dragged?.let { cards.indexOf(it) } ?: -1
        insertionIndex = computeInsertionIndex(mouseX, mouseY)

        val now = System.nanoTime()
        val dt = if (lastCardEaseNanos == 0L) 1.0F else ((now - lastCardEaseNanos) / 1_000_000_000.0F).coerceAtMost(0.1F)
        val t = 1.0F - Math.exp((-CARD_SLIDE_SPEED * dt).toDouble()).toFloat()

        for (i in cards.indices) {
            val card = cards[i]
            val slot = slotFor(i, dragIndex)
            if (card === dragged) {
                card.overridePosition(reorderCursorX - reorderGrabX, reorderCursorY - reorderGrabY)
                card.visible = true
                continue
            }
            // Keep the card's visible/clickable bounds inside the grid viewport.
            card.clipTop = gridTop
            card.clipBottom = gridBottom
            val display = cardDisplay.getOrPut(card) { FloatArray(2) }
            if (display[0] == 0.0F && display[1] == 0.0F && card.x == 0 && card.y == 0) {
                display[0] = slot.first.toFloat()
                display[1] = slot.second.toFloat()
            }
            display[0] = Mth.lerp(t, display[0], slot.first.toFloat())
            display[1] = Mth.lerp(t, display[1], slot.second.toFloat())
            if (Math.abs(display[0] - slot.first) < 0.5F && Math.abs(display[1] - slot.second) < 0.5F) {
                display[0] = slot.first.toFloat()
                display[1] = slot.second.toFloat()
            }
            card.overridePosition(Math.round(display[0]), Math.round(display[1]))
            val visible = display[1] + cellH > gridTop && display[1] < gridBottom
            card.visible = visible
        }
        lastCardEaseNanos = now
    }

    /** Slot (x, y) for card [index], skipping the dragged card's slot and reserving the insertion gap. */
    private fun slotFor(index: Int, dragIndex: Int): Pair<Int, Int> {
        var displayIndex = index
        if (dragIndex >= 0 && index > dragIndex) displayIndex--
        if (dragIndex >= 0 && insertionIndex >= 0 && displayIndex >= insertionIndex) displayIndex++
        val col = displayIndex % cols
        val row = displayIndex / cols
        return (gridOffsetX + col * (cellW + GRID_GAP)) to (gridTop - scrollY + row * (cellH + GRID_GAP))
    }

    /** Insertion index from the cursor in reading order, refined by which half of the cell is hovered. */
    private fun computeInsertionIndex(mouseX: Int, mouseY: Int): Int {
        if (reorderDraggingCard == null) return -1
        if (mouseX < gridOffsetX || mouseY < gridTop || mouseY > gridBottom) return -1
        val relCol = (mouseX - gridOffsetX) / (cellW + GRID_GAP)
        val relRow = (mouseY - gridTop + scrollY) / (cellH + GRID_GAP)
        if (relCol < 0 || relCol >= cols || relRow < 0) return -1
        val count = cards.size - if (reorderDraggingCard != null) 1 else 0
        var idx = relRow * cols + relCol
        if (idx > count) idx = count
        val cellLeft = gridOffsetX + relCol * (cellW + GRID_GAP)
        if (mouseX > cellLeft + cellW / 2) idx++
        return idx
    }

    // ------------------------------------------------------------------
    // Reorder drag (cards)
    // ------------------------------------------------------------------

    internal fun beginCardReorder(card: SkinLibraryCard, mouseX: Int, mouseY: Int) {
        reorderDraggingCard = card
        reorderGrabX = mouseX - card.x
        reorderGrabY = mouseY - card.y
        reorderCursorX = mouseX
        reorderCursorY = mouseY
        // Unregistration is deferred to the end of mouseClicked: removing a child widget while
        // the screen is still iterating its children would risk a ConcurrentModificationException.
    }

    private fun finishCardReorder(mouseX: Int, mouseY: Int) {
        val card = reorderDraggingCard ?: return
        reorderDraggingCard = null
        if (cards.indexOf(card) < 0) return

        // Drop on a tab = cross-category move / unassign.
        val tab = tabAt(mouseY, mouseX)
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
        if (category != null && insertionIndex in 0..category.skins.size) {
            val from = category.skins.indexOf(card.entry.file.name)
            if (from >= 0) {
                var to = insertionIndex
                if (to > from) to--
                category.skins.removeAt(from)
                category.skins.add(to.coerceIn(0, category.skins.size), card.entry.file.name)
                SkinCategoriesStore.save()
            }
        }
        rebuildCards()
    }

    // ------------------------------------------------------------------
    // Tab drag & auto-scroll
    // ------------------------------------------------------------------

    private fun updateTabAutoScroll(mouseY: Int) {
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
        if (mouseY > bottom - AUTO_SCROLL_BAND && mouseY <= bottom + TAB_H) {
            speed = -MAX_TABS_PER_SEC * (1.0F - (bottom - mouseY) / AUTO_SCROLL_BAND.toFloat())
        } else if (mouseY < top + TAB_H + AUTO_SCROLL_BAND && mouseY >= top && tabScroll > 0.0F) {
            speed = MAX_TABS_PER_SEC * (1.0F - (mouseY - top - TAB_H) / AUTO_SCROLL_BAND.toFloat())
        }
        if (speed != 0.0F) {
            tabScroll = Mth.clamp(tabScroll + speed * TAB_H * dt, 0.0F, maxTabScroll().toFloat())
        }
    }

    /** Insertion point p in [0..count]: the gap sits after p categories (pre-removal space). */
    private fun updateTabInsertion() {
        val count = SkinCategoriesStore.all().size
        var p = count
        for (storeIdx in 0 until count) {
            val yTop = stripTop() + TAB_H + storeIdx * TAB_H - tabScroll.toInt()
            if (tabDragCursorY < yTop + TAB_H / 2) {
                p = storeIdx
                break
            }
        }
        tabInsertionIndex = p.coerceIn(0, count)
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        val mx = click.x().toInt()
        val my = click.y().toInt()

        if (confirmingCategoryDelete) {
            if (overlayConfirmButton.mouseClicked(click, doubled) || overlayCancelButton.mouseClicked(click, doubled)) {
                return true
            }
            return true
        }

        // Tab strip: select on click, start a potential drag on press (-1 = none, 0 = All, >0 = category).
        val tab = tabAt(my, mx)
        if (tab != null && click.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            tabDragCategoryIndex = tab
            tabDragActive = false
            tabDragStartY = click.y()
            tabDragCursorY = my
            return true
        }

        // Category band: clicking the collapsed bar toggles expansion (this branch must run
        // whether or not the band is already expanded — it used to be gated on bandExpanded,
        // which made the band impossible to open). Swatches live below the bar and are only
        // hit-tested while expanded.
        if (selectedCategory != null && my >= bandY() && my < bandY() + BAND_COLLAPSED_H && mx >= panelX) {
            bandExpanded = !bandExpanded
            refreshBandWidgets()
            recomputeLayout()
            return true
        }
        if (bandExpanded && selectedCategory != null) {
            val swatch = swatchAt(mx, my)
            if (swatch != null) {
                selectedCategory?.colorHex = SkinCategoryPalette.toHex(swatch)
                SkinCategoriesStore.save()
                return true
            }
        }

        // Grid wheel-scroll area click-through: let children (cards, widgets) handle the rest.
        val result = super.mouseClicked(click, doubled)
        // Deferred: unregister the reorder-dragged card so it only renders via the manual
        // floating pass (and no longer swallows mouse input) once child iteration is over.
        reorderDraggingCard?.let { removeWidget(it) }
        return result
    }

    private fun swatchAt(mx: Int, my: Int): Int? {
        val by = bandY()
        if (!bandExpanded || selectedCategory == null) return null
        val x0 = panelX + 8
        val y0 = by + 24
        if (mx < x0 || my < y0) return null
        val hue = (mx - x0) / (BAND_SWATCH_SIZE + BAND_SWATCH_GAP)
        val row = (my - y0) / (BAND_SWATCH_SIZE + BAND_SWATCH_GAP)
        if (hue < 0 || hue >= 10 || row < 0 || row > 1) return null
        val swatches = SkinCategoryPalette.swatches()
        val idx = hue * 2 + row
        return if (idx in swatches.indices) swatches[idx] else null
    }

    private fun confirmCategoryDelete() {
        val category = selectedCategory
        confirmingCategoryDelete = false
        if (category != null) {
            SkinCategoriesStore.removeCategory(category)
            selectCategory(null)
        }
    }

    private fun selectCategory(category: SkinCategory?) {
        selectedCategory = category
        bandExpanded = false
        scrollY = 0
        reloadView()
        rebuildCards()
        recomputeLayout()
    }

    override fun mouseDragged(click: MouseButtonEvent, offsetX: Double, offsetY: Double): Boolean {
        val mx = click.x().toInt()
        val my = click.y().toInt()
        if (tabDragCategoryIndex > 0 && click.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            if (!tabDragActive && Math.abs(click.y() - tabDragStartY) > TAB_DRAG_THRESHOLD) {
                tabDragActive = true
            }
            if (tabDragActive) {
                tabDragCursorY = my
                updateTabInsertion()
                return true
            }
        }
        if (reorderDraggingCard != null && click.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            reorderCursorX = mx
            reorderCursorY = my
            return true
        }
        return super.mouseDragged(click, offsetX, offsetY)
    }

    override fun mouseReleased(click: MouseButtonEvent): Boolean {
        val mx = click.x().toInt()
        val my = click.y().toInt()
        if (tabDragCategoryIndex >= 0 && click.button() == InputConstants.MOUSE_BUTTON_LEFT) {
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
                val to = (if (from < insertion) insertion - 1 else insertion).coerceIn(0, SkinCategoriesStore.all().size - 1)
                if (to != from) SkinCategoriesStore.moveCategory(from, to)
                rebuildCards()
            } else if (!wasActive) {
                // Click without movement: select.
                if (tabIndex == 0) selectCategory(null) else selectCategory(SkinCategoriesStore.all().getOrNull(tabIndex - 1))
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
        val mx = mouseX.toInt()
        val my = mouseY.toInt()
        if (mx < STRIP_X + TAB_W + 4 && my >= stripTop() && my <= stripBottom()) {
            tabScroll = Mth.clamp(tabScroll - vertAmount.toFloat() * TAB_H, 0.0F, maxTabScroll().toFloat())
            return true
        }
        scrollY = Mth.clamp(scrollY - (vertAmount * (cellH + GRID_GAP)).toInt(), 0, maxScroll)
        return true
    }

    // ------------------------------------------------------------------
    // Lifecycle: close, tick, watcher, imports (carried over from the carousel)
    // ------------------------------------------------------------------

    override fun onClose() {
        stopWatching()
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

        val service = watchService ?: return
        val key = service.poll() ?: return
        var changed = false
        val now = System.currentTimeMillis()
        for (event in key.pollEvents()) {
            val p = event.context() as? Path ?: continue
            if (!p.toString().lowercase().endsWith(".png")) continue
            val expiry = selfTriggeredFiles[p.toString()]
            if (expiry != null && now < expiry) continue
            changed = true
        }
        key.reset()
        if (changed) {
            this.init()
        }
    }

    private fun startWatching() {
        val skinsDir = FabricLoader.getInstance().gameDir.resolve("skins")
        try {
            val service = FileSystems.getDefault().newWatchService()
            skinsDir.register(
                service,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY
            )
            watchService = service
        } catch (e: IOException) {
            SimpleSkinSwapper.LOGGER.warn("Could not watch skins folder: {}", e.message)
            watchService = null
        }
    }

    private fun stopWatching() {
        watchService?.let {
            try {
                it.close()
            } catch (ignored: IOException) {
            }
            watchService = null
        }
    }

    private fun markSelfTriggered(filename: String) {
        selfTriggeredFiles[filename] = System.currentTimeMillis() + SELF_TRIGGERED_GRACE_MS
    }

    private fun addSkinFromFile() {
        //? if >=26.3 {
        /*openSkinFileDialog()
        *///?} else {
        val selected = openSkinFileDialog() ?: return
        importSkinFile(selected)
        //?}
    }

    //? if >=26.3 {
    /*// SDL file dialogs are asynchronous (tinyfd was synchronous): the callback fires from the
    // event pump, possibly after this screen closed — the result is marshalled to the main
    // thread and only applied if this screen is still open.
    private var dialogCallback: SDL_DialogFileCallback? = null

    private fun openSkinFileDialog() {
        dialogCallback?.free()
        val callback = SDL_DialogFileCallback.create { _, filelist, _ ->
            if (filelist != 0L) {
                val first = MemoryUtil.memGetAddress(filelist)
                if (first != 0L) {
                    val path = MemoryUtil.memUTF8(first)
                    Minecraft.getInstance().execute {
                        if (Minecraft.getInstance().gui.screen() === this) importSkinFile(File(path))
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
            markSelfTriggered(dest.fileName.toString())
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
        markSelfTriggered(destName)

        addFromAccountButton.active = false
        AccountSkinFetcher.fetch(
            username, destination,
            { file ->
                addImportedEntry(file)
                addFromAccountButton.active = accountField.value.isNotBlank()
            },
            {
                selfTriggeredFiles.remove(destName)
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

        private const val STRIP_X = 4
        private const val TAB_W = 100
        private const val TAB_H = 28
        private const val TAB_DRAG_THRESHOLD = 5.0

        private const val GRID_GAP = 6
        private const val MIN_CELL_W = 64.0
        private const val MAX_COLS = 10
        private const val MIN_CELL_H = 56

        private const val BAND_COLLAPSED_H = 18
        private const val BAND_EXPANDED_H = 72
        private const val BAND_NAME_WIDTH = 140
        private const val BAND_FIELD_HEIGHT = 16
        private const val BAND_SWATCH_SIZE = 12
        private const val BAND_SWATCH_GAP = 2
        private const val BAND_GRID_MARGIN = 6

        // Card slot easing + tab strip auto-scroll
        private const val CARD_SLIDE_SPEED = 14.0F
        private const val AUTO_SCROLL_BAND = 16
        private const val MAX_TABS_PER_SEC = 2.0F

        private var lastCardEaseNanos = 0L

        // Vanilla EditBox default text color; restored after the invalid-account error flash.
        private val DEFAULT_TEXT_COLOR = 0xFFE0E0E0.toInt()
        private val ERROR_COLOR = 0xFFFF5555.toInt()
        private const val INVALID_ACCOUNT_MESSAGE_MS = 1500L
        private const val SELF_TRIGGERED_GRACE_MS = 1000L
        private const val WHEEL_SIZE = 10
    }
}
