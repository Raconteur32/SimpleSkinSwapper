package fr.raconteur.simpleskinswapper.gui.library

import fr.raconteur.simpleskinswapper.changeskin.SkinChange
import fr.raconteur.simpleskinswapper.changeskin.SkinSwapperState
import fr.raconteur.simpleskinswapper.gui.EdgeSafeButtonWidget
import fr.raconteur.simpleskinswapper.gui.SkinEntry
import fr.raconteur.simpleskinswapper.gui.SkinNameStore
import fr.raconteur.simpleskinswapper.gui.SkinType
import fr.raconteur.simpleskinswapper.gui.SkinTypeStore
import fr.raconteur.simpleskinswapper.overlayMessage
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.network.chat.Component

/**
 * Full-screen detail overlay for one skin. Opens as an animated scale-up of the clicked
 * card into a large rectangle (the base screen stays visible around it). Right side: the
 * skin preview in bulk, drag to rotate. Left side: file-name rename, display name, a
 * wide/slim switch built from the card sprites (dark thin body, full-color square knob
 * sliding over the active side's head: Steve left, Alex right) and a two-step delete.
 */
class SkinDetailPanel(
    parent: SkinLibraryScreen
) : AbstractSkinOverlayPanel(parent) {

    private val fileNameField: EditBox
    private val displayNameField: EditBox
    private val deleteButton: EdgeSafeButtonWidget
    private val applyButton: EdgeSafeButtonWidget
    private var deleteArmed = false

    private var entry: SkinEntry? = null

    init {
        fileNameField = EditBox(
            client.font, 0, 0, 100, FIELD_HEIGHT,
            Component.translatable("simpleskinswapper.screen.detail.file_name")
        )
        fileNameField.setMaxLength(64)
        addChild(fileNameField)

        displayNameField = EditBox(
            client.font, 0, 0, 100, FIELD_HEIGHT,
            Component.translatable("simpleskinswapper.screen.detail.display_name")
        )
        displayNameField.setMaxLength(64)
        // Live update: a blank value clears the override so the file name shows again.
        displayNameField.setResponder { text ->
            val e = entry ?: return@setResponder
            e.displayNameOverride = text.trim().ifEmpty { null }
            SkinNameStore.setName(e.file.name, text.trim())
        }
        addChild(displayNameField)

        deleteButton = EdgeSafeButtonWidget(0, 0, DELETE_W, FIELD_HEIGHT + 2, deleteLabel()) {
            onDeleteClicked()
        }
        addChild(deleteButton)

        applyButton = EdgeSafeButtonWidget(0, 0, APPLY_W, FIELD_HEIGHT + 2,
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
        deleteButton.message = deleteLabel()

        val e = entry!!
        fileNameField.setValue(e.baseName)
        displayNameField.setValue(e.displayNameOverride ?: "")

        openFrom(card.x, card.y, card.width, card.height)
    }

    /** Re-points the panel at a fresh entry after a reload (file renamed / list rebuilt). */
    fun rebind(fresh: SkinEntry) {
        entry = fresh
        fileNameField.setValue(fresh.baseName)
        displayNameField.setValue(fresh.displayNameOverride ?: "")
    }

    val entryFileName: String?
        get() = entry?.file?.name

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    private fun labelY(row: Int): Int = targetRect()[1] + PANEL_PAD + row * (LABEL_LINE + FIELD_HEIGHT + ROW_GAP)

    /** Top of the switch row: one ROW_GAP below the display-name field — the left column
     *  keeps the same 10 px rhythm between every element (field, switch, buttons). */
    override fun switchRowY(): Int = labelY(1) + LABEL_LINE + FIELD_HEIGHT + ROW_GAP

    override fun repositionChildren() {
        val t = targetRect()
        val leftW = leftWidth(t)
        fileNameField.setWidth(leftW)
        displayNameField.setWidth(leftW)
        fileNameField.setPosition(t[0] + PANEL_PAD, labelY(0) + LABEL_LINE)
        displayNameField.setPosition(t[0] + PANEL_PAD, labelY(1) + LABEL_LINE)
        val buttonY = switchRowY() + SWITCH_BODY_H + ROW_GAP
        deleteButton.setPosition(t[0] + PANEL_PAD, buttonY)
        applyButton.setPosition(t[0] + PANEL_PAD + DELETE_W + 8, buttonY)
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

    /** Applies the skin (same flow as the card's replay button), then leaves the screen. */
    private fun applySkin() {
        val e = entry ?: return
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

    private fun commitRename() {
        val e = entry ?: return
        val value = fileNameField.value.trim()
        if (value.isEmpty() || value == e.baseName) {
            fileNameField.setValue(e.baseName)
            return
        }
        if (parent.renameEntry(e, value)) {
            fileNameField.setValue(e.file.nameWithoutExtension)
        } else {
            fileNameField.setValue(e.baseName)
        }
    }

    private fun disarmDelete() {
        if (deleteArmed) {
            deleteArmed = false
            deleteButton.message = deleteLabel()
        }
    }

    private fun isOnDelete(mx: Int, my: Int): Boolean =
        mx >= deleteButton.x && mx < deleteButton.x + deleteButton.width &&
            my >= deleteButton.y && my < deleteButton.y + deleteButton.height

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    override fun drawContent(graphics: GuiGraphicsExtractor, t: IntArray, mouseX: Int, mouseY: Int) {
        val e = entry ?: return
        drawPreview(graphics, e, t, mouseX, mouseY)
        drawLabels(graphics, t)
        drawSwitch(graphics, t, e.skinType)
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
    }

    // ------------------------------------------------------------------
    // Overlay hooks
    // ------------------------------------------------------------------

    override fun acceptsInput(): Boolean = entry != null

    override fun currentSkinType(): SkinType = entry?.skinType ?: SkinType.CLASSIC

    override fun toggleSkinType() {
        val e = entry ?: return
        e.skinType = if (e.skinType == SkinType.CLASSIC) SkinType.SLIM else SkinType.CLASSIC
        SkinTypeStore.setType(e.file.name, e.skinType)
    }

    override fun onCloseRequested(instant: Boolean) {
        // Closing is the ultimate blur: commit a pending file rename (ESC, click outside).
        // Instant closes skip it — they are programmatic (delete / entry gone).
        if (!instant) commitRename()
    }

    override fun onChildFocused(previous: GuiEventListener?, child: GuiEventListener) {
        if (previous != null && previous !== child) commitRename()
    }

    override fun onBackgroundClick(mouseX: Int, mouseY: Int) {
        // Clicking anywhere but the delete button disarms the pending confirmation.
        if (!isOnDelete(mouseX, mouseY)) disarmDelete()
        // Click away from the fields: commit before the base clears the focus.
        if (focusedChild != null) commitRename()
    }

    override fun onEnterPressed() {
        commitRename()
    }

    private companion object {
        private const val DELETE_W = 70
        private const val APPLY_W = 70
    }
}
