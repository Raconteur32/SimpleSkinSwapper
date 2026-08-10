package fr.raconteur.simpleskinswapper.gui

import dev.lambdaurora.spruceui.Position
import dev.lambdaurora.spruceui.render.SpruceGuiGraphics
import dev.lambdaurora.spruceui.screen.SpruceScreen
import dev.lambdaurora.spruceui.widget.SpruceButtonWidget
import dev.lambdaurora.spruceui.widget.text.SpruceTextFieldWidget
import fr.raconteur.simpleskinswapper.SimpleSkinSwapper
import fr.raconteur.simpleskinswapper.changeskin.AccountSkinFetcher
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth
import net.minecraft.util.Util
import org.lwjgl.system.MemoryStack
import org.lwjgl.util.tinyfd.TinyFileDialogs
import java.io.File
import java.io.IOException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchService
import java.util.Collections
import java.util.Locale

class SkinCarouselScreen(private val parent: Screen?) : SpruceScreen(Component.translatable("simpleskinswapper.title")) {

    private val cards = ArrayList<SkinCard>()
    private val allEntries = ArrayList<SkinEntry>()

    // Filenames we wrote/deleted ourselves, mapped to when their grace window expires; the
    // directory watcher ignores matching events during that window instead of triggering a
    // full re-init that would drop UI state (e.g. losing focus/typed text in accountField).
    private val selfTriggeredFiles = HashMap<String, Long>()

    private lateinit var searchField: SpruceTextFieldWidget
    private lateinit var addFromFileButton: SpruceButtonWidget
    private lateinit var addFromAccountButton: SpruceButtonWidget
    private lateinit var accountField: SpruceTextFieldWidget
    private var searchQuery = ""
    private var pendingAccountUsername = ""
    private var invalidAccountRevertAtMs = 0L
    private var accountFieldShowingError = false
    private var cardIndex = 0.0
    private var watchService: WatchService? = null

    private fun markSelfTriggered(filename: String) {
        selfTriggeredFiles[filename] = System.currentTimeMillis() + SELF_TRIGGERED_GRACE_MS
    }

    override fun init() {
        super.init()

        allEntries.clear()
        allEntries.addAll(loadOrderedEntries())

        val searchWidth = Math.min(SEARCH_WIDTH, this.width - getCardGap() * 4)
        val bandTop = font.lineHeight * 3
        val searchY = (bandTop + getCardTop()) / 2 - SEARCH_HEIGHT / 2
        searchField = object : SpruceTextFieldWidget(
            Position.of(getCardGap(), searchY), searchWidth, SEARCH_HEIGHT,
            Component.translatable("simpleskinswapper.screen.carousel.search")
        ) {
            override fun getTextColor(): Int =
                if (this.text.isEmpty()) PLACEHOLDER_COLOR else super.getTextColor()
        }
        searchField.setPlaceholder(Component.translatable("simpleskinswapper.screen.carousel.search"))
        searchField.setText(searchQuery)
        searchField.setChangedListener(this::onSearchChanged)
        addRenderableWidget(searchField)

        val gap = getCardGap()
        val addFileWidth = font.width(Component.translatable("simpleskinswapper.screen.carousel.add_from_file")) + 20
        val addAccountWidth = font.width(Component.translatable("simpleskinswapper.screen.carousel.add_from_account")) + 20

        val addFileX = getCardGap() + searchWidth + gap
        addFromFileButton = SpruceButtonWidget(
            Position.of(addFileX, searchY), addFileWidth, SEARCH_HEIGHT,
            Component.translatable("simpleskinswapper.screen.carousel.add_from_file")
        ) { addSkinFromFile() }
        addRenderableWidget(addFromFileButton)

        val addAccountX = addFileX + addFileWidth + gap
        addFromAccountButton = SpruceButtonWidget(
            Position.of(addAccountX, searchY), addAccountWidth, SEARCH_HEIGHT,
            Component.translatable("simpleskinswapper.screen.carousel.add_from_account")
        ) { addSkinFromAccount() }
        addRenderableWidget(addFromAccountButton)

        val accountFieldX = addAccountX + addAccountWidth + gap
        accountField = object : SpruceTextFieldWidget(
            Position.of(accountFieldX, searchY), ACCOUNT_FIELD_WIDTH, SEARCH_HEIGHT,
            Component.translatable("simpleskinswapper.screen.carousel.account_name")
        ) {
            override fun getTextColor(): Int {
                if (accountFieldShowingError) return ERROR_COLOR
                return if (this.text.isEmpty()) PLACEHOLDER_COLOR else super.getTextColor()
            }
        }
        accountField.setPlaceholder(Component.translatable("simpleskinswapper.screen.carousel.account_name"))
        accountField.setChangedListener { text -> addFromAccountButton.isActive = text.isNotBlank() }
        addRenderableWidget(accountField)
        addFromAccountButton.isActive = false

        rebuildCards()

        addRenderableWidget(
            SpruceButtonWidget(
                Position.of(this.width / 2 - 122, this.height - 24), 120, 20,
                CommonComponents.GUI_CANCEL
            ) { onClose() }
        )
        addRenderableWidget(
            SpruceButtonWidget(
                Position.of(this.width / 2 + 2, this.height - 24), 120, 20,
                Component.translatable("simpleskinswapper.screen.carousel.open_folder")
            ) {
                Util.getPlatform().openFile(
                    FabricLoader.getInstance().gameDir.resolve("skins").toFile()
                )
            }
        )

        stopWatching()
        startWatching()
    }

    override fun onClose() {
        stopWatching()
        //? if >=26.2 {
        this.minecraft?.gui?.setScreen(parent)
        //?} else {
        /*this.minecraft?.setScreen(parent)
        *///?}
    }

    override fun tick() {
        super.tick()

        if (invalidAccountRevertAtMs != 0L && System.currentTimeMillis() >= invalidAccountRevertAtMs) {
            invalidAccountRevertAtMs = 0L
            accountFieldShowingError = false
            accountField.setText(pendingAccountUsername)
            addFromAccountButton.isActive = accountField.text.isNotBlank()
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

    //? if >=26.1 {
    override fun extractRenderState(graphics: SpruceGuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
    //?} else {
    /*override fun render(graphics: SpruceGuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
    *///?}
        graphics.fill(0, font.lineHeight * 3, this.width, this.height - font.lineHeight * 3, 0x7F000000)

        val cardW = getCardWidth()
        val gap = getCardGap()
        val cardAreaWidth = cardW + gap
        val cardTop = getCardTop()

        val startX = gap

        for (i in cards.indices) {
            val card = cards[i]
            val cardX = (startX + (i - cardIndex) * cardAreaWidth).toInt()
            card.overridePosition(cardX, cardTop)
        }

        //? if >=26.1 {
        super.extractRenderState(graphics, mouseX, mouseY, delta)
        //?} else {
        /*renderWidgets(graphics, mouseX, mouseY, delta)
        *///?}

        if (getMaxCardIndex() > 0) {
            renderScrollbar(graphics, cardIndex)
        }

        graphics.vanilla().centeredText(
            font,
            title.visualOrderText,
            this.width / 2, font.lineHeight, 0xFFFFFFFF.toInt()
        )

        // "No skins found" hint when directory is empty
        if (cards.isEmpty()) {
            graphics.vanilla().centeredText(
                font,
                Component.translatable("simpleskinswapper.screen.carousel.no_skins"),
                this.width / 2, this.height / 2 - font.lineHeight / 2, 0xFFAAAAAA.toInt()
            )
        }
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, hozAmount: Double, vertAmount: Double): Boolean {
        scroll((-vertAmount - hozAmount) / 4.0)
        return true
    }

    private var isDraggingScrollbar = false
    private var scrollbarDragOffsetX = 0

    private fun sbTrackX(): Int = getCardGap()
    private fun sbTrackW(): Int = this.width - getCardGap() * 2
    private fun sbTrackY(): Int = this.height - font.lineHeight * 3 - SCROLLBAR_HEIGHT - 4
    private fun sbThumbW(): Int = Math.max(20, sbTrackW() / Math.max(1, cards.size))
    private fun sbThumbX(index: Double): Int {
        val thumbRange = sbTrackW() - sbThumbW()
        val maxIdx = getMaxCardIndex()
        if (thumbRange <= 0 || maxIdx <= 0) return sbTrackX()
        return sbTrackX() + (index / maxIdx * thumbRange).toInt()
    }

    private fun renderScrollbar(graphics: SpruceGuiGraphics, index: Double) {
        val trackY = sbTrackY()
        val trackX = sbTrackX()
        val trackW = sbTrackW()
        val thumbW = sbThumbW()
        val thumbX = sbThumbX(index)

        graphics.vanilla().fill(trackX, trackY, trackX + trackW, trackY + SCROLLBAR_HEIGHT, SCROLLBAR_TRACK_COLOR)
        graphics.vanilla().fill(thumbX, trackY, thumbX + thumbW, trackY + SCROLLBAR_HEIGHT, SCROLLBAR_THUMB_COLOR)
    }

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        if (getMaxCardIndex() > 0 && click.button() == 0) {
            val trackY = sbTrackY()
            val trackX = sbTrackX()
            val trackW = sbTrackW()
            val hitY1 = trackY - SCROLLBAR_HIT_PADDING
            val hitY2 = trackY + SCROLLBAR_HEIGHT + SCROLLBAR_HIT_PADDING
            if (click.y() >= hitY1 && click.y() <= hitY2 && click.x() >= trackX && click.x() <= trackX + trackW) {
                val thumbX = sbThumbX(cardIndex)
                val thumbW = sbThumbW()
                if (click.x() >= thumbX && click.x() <= thumbX + thumbW) {
                    scrollbarDragOffsetX = click.x().toInt() - thumbX
                } else {
                    scrollbarDragOffsetX = thumbW / 2
                    updateScrollFromMouseX(click.x().toInt())
                }
                isDraggingScrollbar = true
                return true
            }
        }
        return super.mouseClicked(click, doubled)
    }

    override fun mouseDragged(click: MouseButtonEvent, offsetX: Double, offsetY: Double): Boolean {
        if (isDraggingScrollbar && click.button() == 0) {
            updateScrollFromMouseX(click.x().toInt())
            return true
        }
        return super.mouseDragged(click, offsetX, offsetY)
    }

    override fun mouseReleased(click: MouseButtonEvent): Boolean {
        if (isDraggingScrollbar && click.button() == 0) {
            isDraggingScrollbar = false
            return true
        }
        return super.mouseReleased(click)
    }

    private fun updateScrollFromMouseX(mouseX: Int) {
        val trackX = sbTrackX()
        val thumbW = sbThumbW()
        val thumbRange = sbTrackW() - thumbW
        if (thumbRange <= 0) return
        val newThumbX = mouseX - scrollbarDragOffsetX - trackX
        val fraction = Mth.clamp(newThumbX.toDouble() / thumbRange, 0.0, 1.0)
        setCardIndex(fraction * getMaxCardIndex())
    }

    private fun scroll(amount: Double) {
        if (getMaxCardIndex() <= 0) return
        val newIndex = Mth.clamp(cardIndex + amount, 0.0, getMaxCardIndex())
        setCardIndex(newIndex)
    }

    private fun setCardIndex(index: Double) {
        cardIndex = index
    }

    private fun getCardWidth(): Int {
        val naturalWidth = this.width / 5
        val minWidth = (getCardHeight() * MIN_CARD_ASPECT).toInt()
        return Math.max(naturalWidth, minWidth)
    }

    private fun getCardHeight(): Int = (this.height / 1.5).toInt()

    private fun getCardTop(): Int = sbTrackY() - CARD_BOTTOM_GAP - getCardHeight()

    private fun getCardGap(): Int = 10

    /**
     * Maximum card index we can scroll to, so that the last card
     * ends up in the last visible slot on the right (not further left).
     */
    private fun getMaxCardIndex(): Double {
        val cardW = getCardWidth()
        val gap = getCardGap()
        val cardAreaWidth = cardW + gap
        // Exact number of card-widths that fit between the left margin and the right margin
        // so that the last card's right edge lands exactly at (screenWidth - gap)
        val slotsFromLeft = (this.width - 2 * gap - cardW).toDouble() / cardAreaWidth
        return Math.max(0.0, cards.size - 1 - slotsFromLeft)
    }

    fun moveCard(card: SkinCard, direction: Int) {
        val idx = cards.indexOf(card)
        val newIdx = idx + direction
        if (newIdx < 0 || newIdx >= cards.size) return
        Collections.swap(cards, idx, newIdx)
        saveCurrentOrder()
        updateAllArrowStates()
    }

    fun deleteEntry(entry: SkinEntry) {
        markSelfTriggered(entry.file.name)
        if (!entry.file.delete()) {
            selfTriggeredFiles.remove(entry.file.name)
            SimpleSkinSwapper.LOGGER.warn("Could not delete skin file {}.", entry.file.name)
            return
        }
        SkinTypeStore.removeType(entry.file.name)
        allEntries.remove(entry)
        rebuildCards()

        val orderFile = FabricLoader.getInstance().gameDir.resolve("skins").resolve("order.txt")
        saveOrder(allEntries, orderFile)
    }

    private fun addSkinFromFile() {
        val selected = openSkinFileDialog() ?: return
        importSkinFile(selected)
    }

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
        val username = accountField.text
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
        // Reserve the filename before the async write happens, so the directory watcher
        // ignores the resulting creation event instead of racing a full re-init against it.
        val destName = destination.fileName.toString()
        markSelfTriggered(destName)

        addFromAccountButton.isActive = false
        AccountSkinFetcher.fetch(
            username, destination,
            { file ->
                addImportedEntry(file)
                addFromAccountButton.isActive = accountField.text.isNotBlank()
            },
            {
                selfTriggeredFiles.remove(destName)
                showInvalidAccount(username)
            }
        )
    }

    private fun showInvalidAccount(previousText: String) {
        pendingAccountUsername = previousText
        accountFieldShowingError = true
        accountField.setText(Component.translatable("simpleskinswapper.screen.carousel.invalid_account").string)
        invalidAccountRevertAtMs = System.currentTimeMillis() + INVALID_ACCOUNT_MESSAGE_MS
        addFromAccountButton.isActive = accountField.text.isNotBlank()
    }

    private fun addImportedEntry(file: File) {
        allEntries.add(SkinEntry(file))
        rebuildCards()

        val orderFile = FabricLoader.getInstance().gameDir.resolve("skins").resolve("order.txt")
        saveOrder(allEntries, orderFile)
    }

    private fun rebuildCards() {
        for (card in cards) {
            removeWidget(card)
        }
        cards.clear()

        val cardW = getCardWidth()
        val cardH = getCardHeight()
        for (entry in filteredEntries()) {
            val card = SkinCard(this, entry, cardW, cardH)
            cards.add(card)
            addRenderableWidget(card)
        }

        if (cards.isNotEmpty()) {
            cardIndex = Mth.clamp(cardIndex, 0.0, getMaxCardIndex())
        }
        updateAllArrowStates()
    }

    private fun filteredEntries(): List<SkinEntry> {
        if (searchQuery.isBlank()) return allEntries
        val needle = searchQuery.lowercase(Locale.ROOT)
        return allEntries.filter { it.displayName.lowercase(Locale.ROOT).contains(needle) }
    }

    private fun onSearchChanged(text: String) {
        searchQuery = text
        rebuildCards()
    }

    // Reordering saves the visible `cards` list as the new skin order; while a filter hides
    // entries, that would drop them from order.txt, so disable reordering until the filter clears.
    private fun updateAllArrowStates() {
        val reorderable = searchQuery.isBlank()
        for (i in cards.indices) {
            cards[i].updateArrowStates(reorderable && i > 0, reorderable && i < cards.size - 1)
        }
    }

    private fun saveCurrentOrder() {
        val orderFile = FabricLoader.getInstance().gameDir.resolve("skins").resolve("order.txt")
        val content = cards.joinToString(",") { it.getEntry().file.name }
        try {
            Files.writeString(orderFile, content)
        } catch (e: IOException) {
            SimpleSkinSwapper.LOGGER.warn("Could not save skin order: {}", e.message)
        }
    }

    companion object {
        private const val SEARCH_HEIGHT = 20
        private const val SEARCH_WIDTH = 200
        private const val ACCOUNT_FIELD_WIDTH = 120
        private val PLACEHOLDER_COLOR = 0xFF707070.toInt()
        private val ERROR_COLOR = 0xFFFF5555.toInt()
        private const val INVALID_ACCOUNT_MESSAGE_MS = 1500L
        private const val CARD_BOTTOM_GAP = 12
        private const val MIN_CARD_ASPECT = 0.5F

        // A single write can emit several watch events (e.g. CREATE then MODIFY), so a self-triggered
        // filename is ignored for this whole window rather than just the first matching event.
        private const val SELF_TRIGGERED_GRACE_MS = 1000L

        private const val SCROLLBAR_HEIGHT = 4
        private const val SCROLLBAR_HIT_PADDING = 6
        private const val SCROLLBAR_TRACK_COLOR = 0x4FFFFFFF
        private val SCROLLBAR_THUMB_COLOR = 0xCCFFFFFF.toInt()

        @JvmStatic
        fun loadOrderedEntries(): MutableList<SkinEntry> {
            val allEntries = SkinEntry.loadSkins()
            val orderFile = FabricLoader.getInstance().gameDir.resolve("skins").resolve("order.txt")

            if (!Files.exists(orderFile)) {
                saveOrder(allEntries, orderFile)
                return allEntries.toMutableList()
            }

            try {
                val content = Files.readString(orderFile).trim()
                if (content.isEmpty()) {
                    saveOrder(allEntries, orderFile)
                    return allEntries.toMutableList()
                }

                val orderedNames = content.split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }

                val byName = LinkedHashMap<String, SkinEntry>()
                for (e in allEntries) byName[e.file.name] = e

                val result = ArrayList<SkinEntry>()
                for (name in orderedNames) {
                    val e = byName.remove(name)
                    if (e != null) result.add(e)
                }
                result.addAll(byName.values)

                saveOrder(result, orderFile)
                return result
            } catch (e: IOException) {
                SimpleSkinSwapper.LOGGER.warn("Could not read skin order: {}", e.message)
                return allEntries.toMutableList()
            }
        }

        @JvmStatic
        fun saveOrder(entries: List<SkinEntry>, orderFile: Path) {
            val content = entries.joinToString(",") { it.file.name }
            try {
                Files.writeString(orderFile, content)
            } catch (e: IOException) {
                SimpleSkinSwapper.LOGGER.warn("Could not save skin order: {}", e.message)
            }
        }
    }
}
