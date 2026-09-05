package fr.raconteur.simpleskinswapper.gui.library

import fr.raconteur.simpleskinswapper.changeskin.SkinChange
import fr.raconteur.simpleskinswapper.changeskin.SkinSwapperState
import fr.raconteur.simpleskinswapper.gui.EdgeSafeButtonWidget
import fr.raconteur.simpleskinswapper.gui.SkinEntry
import fr.raconteur.simpleskinswapper.gui.SkinType
import fr.raconteur.simpleskinswapper.library.DeleteDecision
import fr.raconteur.simpleskinswapper.library.DeleteSource
import fr.raconteur.simpleskinswapper.overlayMessage
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component

/**
 * Full-screen detail overlay for one skin. Opens as an animated scale-up of the clicked
 * card into a large rectangle (the base screen stays visible around it). Right side: the
 * skin preview in bulk, drag to rotate. Left column: the read-only texture file name
 * (hash — never editable), the global display name, the per-category name (category views
 * only), the wide/slim switch — which switches to the sibling skin of the same texture —
 * the dynamic delete controls (remove card here vs delete everywhere) and apply.
 */
class SkinDetailPanel(
    parent: SkinLibraryScreen
) : AbstractSkinOverlayPanel(parent) {

    private val fileNameField: EditBox
    private val displayNameField: EditBox
    private val categoryNameField: EditBox
    private val removeCardButton: EdgeSafeButtonWidget
    private val deleteButton: EdgeSafeButtonWidget
    private val applyButton: EdgeSafeButtonWidget
    private var deleteArmed = false

    private var entry: SkinEntry? = null

    /** Model the entry had when the panel opened; a pending switch back to it is a no-op. */
    private var originalModel: SkinType = SkinType.CLASSIC

    /** Previewed target model — the sibling skin is only created when the panel closes. */
    private var pendingType: SkinType? = null

    /** Why the last toggle was refused (shown under the switch until it changes). */
    private var switchNote: Component? = null

    private val inCategory: Boolean get() = parent.selectedCategory != null

    init {
        fileNameField = EditBox(
            client.font, 0, 0, 100, FIELD_HEIGHT,
            Component.translatable("simpleskinswapper.screen.detail.file_name")
        )
        fileNameField.setMaxLength(64)
        fileNameField.setEditable(false)
        addChild(fileNameField)

        displayNameField = EditBox(
            client.font, 0, 0, 100, FIELD_HEIGHT,
            Component.translatable("simpleskinswapper.screen.detail.display_name")
        )
        displayNameField.setMaxLength(64)
        // Preview only: the registry rename happens when the panel closes.
        displayNameField.setResponder { text ->
            val e = entry ?: return@setResponder
            e.displayNameOverride = text.trim().ifEmpty { null }
        }
        addChild(displayNameField)

        categoryNameField = EditBox(
            client.font, 0, 0, 100, FIELD_HEIGHT,
            Component.translatable("simpleskinswapper.screen.detail.category_name")
        )
        categoryNameField.setMaxLength(64)
        // Preview only: the per-category name is stored when the panel closes.
        categoryNameField.setResponder { text ->
            val e = entry ?: return@setResponder
            e.displayNameOverride = text.trim().ifEmpty { SkinRecords.findById(e.skinId)?.name }
        }
        addChild(categoryNameField)

        removeCardButton = EdgeSafeButtonWidget(0, 0, BTN_W, FIELD_HEIGHT + 2,
            Component.translatable("simpleskinswapper.screen.detail.remove_card")
        ) { removeFromCategory() }
        addChild(removeCardButton)

        deleteButton = EdgeSafeButtonWidget(0, 0, BTN_W, FIELD_HEIGHT + 2, deleteLabel()) {
            onDeleteClicked()
        }
        addChild(deleteButton)

        applyButton = EdgeSafeButtonWidget(0, 0, BTN_W, FIELD_HEIGHT + 2,
            Component.translatable("simpleskinswapper.screen.carousel.apply")
        ) { applySkin() }
        addChild(applyButton)
    }

    // ------------------------------------------------------------------
    // Open / close / rebind
    // ------------------------------------------------------------------

    /** Starts the scale-up animation from [card]'s current rect to the full detail rect. */
    fun open(card: SkinLibraryCard) {
        entry = card.entry
        deleteArmed = false
        originalModel = card.entry.skinType
        pendingType = null
        switchNote = null
        deleteButton.message = deleteLabel()
        refreshFields()
        openFrom(card.x, card.y, card.width, card.height)
    }

    /** Re-points the panel at a fresh entry after a reload (list rebuilt / model switch). */
    fun rebind(fresh: SkinEntry) {
        entry = fresh
        deleteArmed = false
        originalModel = fresh.skinType
        pendingType = null
        switchNote = null
        deleteButton.message = deleteLabel()
        refreshFields()
    }

    val entrySkinId: String?
        get() = entry?.skinId

    private fun refreshFields() {
        val e = entry ?: return
        fileNameField.setValue(e.baseName)
        displayNameField.setValue(e.displayNameOverride ?: "")
        categoryNameField.setValue(
            parent.selectedCategory?.cards?.firstOrNull { it.skinId == e.skinId }?.name ?: ""
        )
        categoryNameField.visible = inCategory
        removeCardButton.visible = inCategory
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    private fun labelY(row: Int): Int = targetRect()[1] + PANEL_PAD + row * (LABEL_LINE + FIELD_HEIGHT + ROW_GAP)

    /** Top of the switch row: one ROW_GAP below the last field row. */
    override fun switchRowY(): Int = labelY(if (inCategory) 2 else 1) + LABEL_LINE + FIELD_HEIGHT + ROW_GAP

    override fun repositionChildren() {
        val t = targetRect()
        val leftW = leftWidth(t)
        fileNameField.setWidth(leftW)
        fileNameField.setPosition(t[0] + PANEL_PAD, labelY(0) + LABEL_LINE)
        displayNameField.setWidth(leftW)
        displayNameField.setPosition(t[0] + PANEL_PAD, labelY(1) + LABEL_LINE)
        categoryNameField.setVisible(inCategory)
        removeCardButton.visible = inCategory
        if (inCategory) {
            categoryNameField.setWidth(leftW)
            categoryNameField.setPosition(t[0] + PANEL_PAD, labelY(2) + LABEL_LINE)
        }
        val buttonY = switchRowY() + SWITCH_BODY_H + ROW_GAP
        if (inCategory) {
            val w = (leftW - 16) / 3
            removeCardButton.setWidth(w)
            removeCardButton.setPosition(t[0] + PANEL_PAD, buttonY)
            deleteButton.setWidth(w)
            deleteButton.setPosition(t[0] + PANEL_PAD + w + 8, buttonY)
            applyButton.setWidth(w)
            applyButton.setPosition(t[0] + PANEL_PAD + (w + 8) * 2, buttonY)
        } else {
            deleteButton.setWidth(BTN_W)
            deleteButton.setPosition(t[0] + PANEL_PAD, buttonY)
            applyButton.setWidth(BTN_W)
            applyButton.setPosition(t[0] + PANEL_PAD + BTN_W + 8, buttonY)
        }
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    private fun onDeleteClicked() {
        val e = entry ?: return
        if (!deleteArmed) {
            deleteArmed = true
            deleteButton.message = Component.translatable("simpleskinswapper.screen.detail.delete_confirm")
            return
        }
        deleteArmed = false
        close(instant = true)
        parent.deleteEntry(e)
    }

    /** Removes this card from the current category only — the skin and its other cards stay. */
    private fun removeFromCategory() {
        val e = entry ?: return
        close(instant = true)
        parent.removeCardOf(e)
    }

    /** Commits a pending model switch (creates the sibling skin, replaces the original).
     *  Called when the panel closes or before applying — the preview alone never mutates. */
    private fun commitPendingSwitch() {
        val target = pendingType ?: return
        pendingType = null
        switchNote = null
        val e = entry ?: return
        entry = parent.switchModel(e, target) ?: e
        refreshFields()
    }

    /** Applies the skin (same flow as the card's replay button), then leaves the screen. */
    private fun applySkin() {
        commitPendingSwitch()
        val e = entry ?: return
        parent.commitEntryNames(e, displayNameField.value, categoryNameField.value)
        if (!SkinSwapperState.beginSwap()) return
        SkinChange.changeSkin(
            e.file, e.skinType, e.textureId,
            { showOverlay(Component.translatable("simpleskinswapper.message.success")) },
            { err -> showOverlay(Component.translatable("simpleskinswapper.message.error", err)) }
        )
        parent.onClose()
        showOverlay(Component.translatable("simpleskinswapper.message.applying"))
    }

    private fun showOverlay(text: Component) {
        client.player?.overlayMessage(text)
    }

    private fun deleteLabel(): Component =
        Component.translatable("simpleskinswapper.screen.detail.delete")

    private fun disarmDelete() {
        if (deleteArmed) {
            deleteArmed = false
            deleteButton.message = deleteLabel()
        }
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    override fun drawContent(graphics: GuiGraphicsExtractor, t: IntArray, mouseX: Int, mouseY: Int) {
        val e = entry ?: return
        drawPreview(graphics, e, t, mouseX, mouseY)
        drawLabels(graphics, t)
        drawSwitch(graphics, t, e.skinType)
        drawContextLine(graphics, e, t)
    }

    private fun drawPreview(graphics: GuiGraphicsExtractor, e: SkinEntry, t: IntArray, mouseX: Int, mouseY: Int) {
        val p = previewRect(t)
        e.ensureTextureLoaded()
        val textureId = e.textureId ?: return
        drawRotatablePreview(graphics, p, textureId, e.skinType == SkinType.SLIM, mouseX, mouseY)
    }

    private fun drawLabels(graphics: GuiGraphicsExtractor, t: IntArray) {
        val x = t[0] + PANEL_PAD
        graphics.text(client.font, Component.translatable("simpleskinswapper.screen.detail.file_name"), x, labelY(0), 0xFFB0B8C0.toInt())
        graphics.text(client.font, Component.translatable("simpleskinswapper.screen.detail.display_name"), x, labelY(1), 0xFFB0B8C0.toInt())
        if (inCategory) {
            graphics.text(client.font, Component.translatable("simpleskinswapper.screen.detail.category_name"), x, labelY(2), 0xFFB0B8C0.toInt())
        }
    }

    /** Under the switch: why the model switch is off, or the delete context line. */
    private fun drawContextLine(graphics: GuiGraphicsExtractor, e: SkinEntry, t: IntArray) {
        val noteY = switchRowY() + SWITCH_BODY_H + 2
        val note = switchNote ?: deleteContext(e) ?: return
        //? if >=26.1 {
        graphics.text(client.font, note, t[0] + PANEL_PAD, noteY, 0xFFB0B8C0.toInt())
        //?} else {
        /*graphics.drawString(client.font, note, t[0] + PANEL_PAD, noteY, 0xFFB0B8C0.toInt())
        *///?}
    }

    /** The dynamic delete text per [DeleteDecision]: cross-category counts or "safe". */
    private fun deleteContext(e: SkinEntry): Component? {
        val source = parent.deleteSource()
        val decision = DeleteDecision.of(source, SkinCategories.categoriesOf(e.skinId).size)
        return when (source) {
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
                    null
                }
            DeleteSource.UNCATEGORIZED ->
                Component.translatable("simpleskinswapper.screen.detail.delete_context_uncategorized")
        }
    }

    // ------------------------------------------------------------------
    // Overlay hooks
    // ------------------------------------------------------------------

    override fun acceptsInput(): Boolean = entry != null

    override fun currentSkinType(): SkinType = entry?.skinType ?: SkinType.CLASSIC

    override fun toggleSkinType() {
        val e = entry ?: return
        val target = if (e.skinType == SkinType.CLASSIC) SkinType.SLIM else SkinType.CLASSIC
        val reason = parent.switchBlockedReason(e, target)
        if (reason != null) {
            switchNote = reason
            return
        }
        switchNote = null
        // Preview only: the sibling skin is created when the panel closes.
        e.skinType = target
        pendingType = if (target == originalModel) null else target
    }

    override fun onCloseRequested(instant: Boolean) {
        if (instant) {
            // Programmatic closes (delete / remove card): the action wins, nothing commits.
            disarmDelete()
            return
        }
        // Quitting the panel (ESC or click beside) is what applies the pending edits.
        commitPendingSwitch()
        val e = entry
        if (e != null) parent.commitEntryNames(e, displayNameField.value, categoryNameField.value)
        disarmDelete()
    }

    override fun onBackgroundClick(mouseX: Int, mouseY: Int) {
        // Clicking beside the panel quits it: pending edits commit via onCloseRequested.
        close()
    }

    private companion object {
        private const val BTN_W = 64
    }
}
