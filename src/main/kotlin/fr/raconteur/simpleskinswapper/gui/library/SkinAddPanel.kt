package fr.raconteur.simpleskinswapper.gui.library

import fr.raconteur.simpleskinswapper.changeskin.AccountSkinFetcher
import fr.raconteur.simpleskinswapper.gui.EdgeSafeButtonWidget
import fr.raconteur.simpleskinswapper.gui.SkinType
import fr.raconteur.simpleskinswapper.gui.SkinUtils
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.fabricmc.loader.api.FabricLoader
import java.io.File
import java.nio.file.Files

/**
 * Add-skin overlay, opened from the trailing "+" card. Same shell as the detail overlay:
 * bulk preview on the right, management controls on the left — but instead of delete and
 * apply, the column starts with the two "pick a skin" sources (file / MC name) and ends
 * with add + cancel. The confirm stays disabled until a skin has actually been staged.
 */
class SkinAddPanel(
    parent: SkinLibraryScreen
) : AbstractSkinOverlayPanel(parent) {

    private val usernameField: EditBox
    private val fileNameField: EditBox
    private val displayNameField: EditBox
    private val fromFileButton: EdgeSafeButtonWidget
    private val fromMcNameButton: EdgeSafeButtonWidget
    private val confirmButton: EdgeSafeButtonWidget
    private val cancelButton: EdgeSafeButtonWidget

    private var stagedFile: File? = null
    private var stagedType: SkinType = SkinType.CLASSIC
    private var stagedTextureId: Identifier? = null
    private var fetching = false

    // Invalid-account flash on the username field (same pattern as the header import row).
    private var invalidRevertAtMs = 0L
    private var invalidSavedText = ""

    init {
        fromFileButton = EdgeSafeButtonWidget(0, 0, 100, FIELD_HEIGHT + 2,
            Component.translatable("simpleskinswapper.screen.add.from_file")
        ) { parent.pickSkinFile { file -> stage(file, file.nameWithoutExtension) } }
        addChild(fromFileButton)

        usernameField = EditBox(
            client.font, 0, 0, 100, FIELD_HEIGHT,
            Component.translatable("simpleskinswapper.screen.add.username")
        )
        usernameField.setMaxLength(16)
        usernameField.setHint(Component.translatable("simpleskinswapper.screen.add.username"))
        usernameField.setResponder { text ->
            if (!fetching) fromMcNameButton.active = text.isNotBlank()
        }
        addChild(usernameField)

        fromMcNameButton = EdgeSafeButtonWidget(0, 0, 60, FIELD_HEIGHT + 2,
            Component.translatable("simpleskinswapper.screen.add.from_mcname")
        ) { fetchFromAccount() }
        fromMcNameButton.active = false
        addChild(fromMcNameButton)

        displayNameField = EditBox(
            client.font, 0, 0, 100, FIELD_HEIGHT,
            Component.translatable("simpleskinswapper.screen.detail.display_name")
        )
        displayNameField.setMaxLength(64)
        addChild(displayNameField)

        fileNameField = EditBox(
            client.font, 0, 0, 100, FIELD_HEIGHT,
            Component.translatable("simpleskinswapper.screen.detail.file_name")
        )
        fileNameField.setMaxLength(64)
        addChild(fileNameField)

        confirmButton = EdgeSafeButtonWidget(0, 0, 60, FIELD_HEIGHT + 2,
            Component.translatable("simpleskinswapper.screen.add.confirm")
        ) { confirmAdd() }
        confirmButton.active = false
        addChild(confirmButton)

        cancelButton = EdgeSafeButtonWidget(0, 0, 60, FIELD_HEIGHT + 2,
            CommonComponents.GUI_CANCEL
        ) { close() }
        addChild(cancelButton)
    }

    // ------------------------------------------------------------------
    // Open / close
    // ------------------------------------------------------------------

    /** Starts the scale-up animation from [card]'s current rect to the full add rect. */
    fun open(card: SkinAddCard) {
        cleanupStaging()
        usernameField.setValue("")
        displayNameField.setValue("")
        fileNameField.setValue("")
        stagedType = SkinType.CLASSIC
        stagedTextureId = null
        fetching = false
        invalidRevertAtMs = 0L
        usernameField.setTextColor(0xFFE0E0E0.toInt())
        fromMcNameButton.active = false
        confirmButton.active = false

        openFrom(card.x, card.y, card.width, card.height)
    }

    override fun onCloseRequested(instant: Boolean) {
        // The staging file is temporary by design: dropped on every close.
        cleanupStaging()
    }

    // ------------------------------------------------------------------
    // Staging
    // ------------------------------------------------------------------

    private fun stagingFile(): File =
        FabricLoader.getInstance().gameDir.resolve(".simpleskinswapper-add-staging.png").toFile()

    /** Drops the staged skin (the staging file is temporary by design). */
    private fun cleanupStaging() {
        stagedFile?.delete()
        stagedFile = null
        stagedTextureId = null
    }

    /** Stages a skin for the preview: auto-detects the model and suggests a file name. */
    private fun stage(file: File, suggestedName: String) {
        stagedFile = file
        stagedType = SkinUtils.detectSkinType(file)
        fileNameField.setValue(suggestedName)
        stagedTextureId = null
        SkinUtils.loadSkinTextureAsync(file, "skin/add_staging") { id -> stagedTextureId = id }
    }

    private fun fetchFromAccount() {
        val username = usernameField.value.trim()
        if (username.isEmpty() || fetching) return
        fetching = true
        fromMcNameButton.active = false
        AccountSkinFetcher.fetch(
            username, stagingFile().toPath(),
            { file ->
                fetching = false
                fromMcNameButton.active = usernameField.value.isNotBlank()
                stage(file, AccountSkinFetcher.sanitizeFilename(username))
            },
            {
                fetching = false
                fromMcNameButton.active = usernameField.value.isNotBlank()
                flashInvalidAccount(username)
            }
        )
    }

    private fun flashInvalidAccount(previousText: String) {
        invalidSavedText = previousText
        usernameField.setTextColor(0xFFFF5555.toInt())
        usernameField.setValue(Component.translatable("simpleskinswapper.screen.carousel.invalid_account").string)
        invalidRevertAtMs = System.currentTimeMillis() + 1500L
    }

    private fun tickInvalidFlash() {
        if (invalidRevertAtMs != 0L && System.currentTimeMillis() >= invalidRevertAtMs) {
            invalidRevertAtMs = 0L
            usernameField.setTextColor(0xFFE0E0E0.toInt())
            usernameField.setValue(invalidSavedText)
        }
    }

    /** A skin is staged, the file name is valid and the target does not exist yet. */
    private fun canConfirm(): Boolean {
        val name = sanitize(fileNameField.value)
        if (stagedFile == null || name.isEmpty()) return false
        val target = FabricLoader.getInstance().gameDir.resolve("skins").resolve("$name.png")
        return !Files.exists(target)
    }

    private fun sanitize(value: String): String =
        value.replace(Regex("[^A-Za-z0-9_\\- ]"), "_").trim()

    private fun confirmAdd() {
        val file = stagedFile ?: return
        val name = sanitize(fileNameField.value)
        if (name.isEmpty()) return
        if (parent.confirmAddSkin(file, name, displayNameField.value.trim(), stagedType)) {
            // Ownership of the staging file transferred to the skins folder.
            stagedFile = null
            close(instant = true)
        }
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    /** Explicit left-column rows: two button rows, then label+field pairs, switch, buttons.
     *  Every gap is the same ROW_GAP so the column keeps one vertical rhythm. */
    private fun btnRowY(row: Int): Int = targetRect()[1] + PANEL_PAD + row * (FIELD_HEIGHT + 2 + ROW_GAP)

    private fun dispLabelY(): Int = btnRowY(1) + FIELD_HEIGHT + 2 + ROW_GAP

    private fun dispFieldY(): Int = dispLabelY() + LABEL_LINE

    private fun fileLabelY(): Int = dispFieldY() + FIELD_HEIGHT + ROW_GAP

    private fun fileFieldY(): Int = fileLabelY() + LABEL_LINE

    override fun switchRowY(): Int = fileFieldY() + FIELD_HEIGHT + ROW_GAP

    override fun repositionChildren() {
        val t = targetRect()
        val leftW = leftWidth(t)

        fromFileButton.setWidth(leftW)
        fromFileButton.setPosition(t[0] + PANEL_PAD, btnRowY(0))

        // Compact source button (label-sized) so the username field keeps the room.
        val searchW = client.font.width(fromMcNameButton.message) + 12
        val usernameW = leftW - searchW - 8
        usernameField.setWidth(usernameW)
        // The field is 2px shorter than the button: nudge it down so their centerlines align.
        usernameField.setPosition(t[0] + PANEL_PAD, btnRowY(1) + (FIELD_HEIGHT + 2 - FIELD_HEIGHT) / 2)
        fromMcNameButton.setWidth(searchW)
        fromMcNameButton.setPosition(t[0] + PANEL_PAD + usernameW + 8, btnRowY(1))

        displayNameField.setWidth(leftW)
        displayNameField.setPosition(t[0] + PANEL_PAD, dispFieldY())
        fileNameField.setWidth(leftW)
        fileNameField.setPosition(t[0] + PANEL_PAD, fileFieldY())

        // Confirm/cancel split the column in two so cancel never overflows past it.
        val buttonY = switchRowY() + SWITCH_BODY_H + ROW_GAP
        val halfW = (leftW - 8) / 2
        confirmButton.setWidth(halfW)
        confirmButton.setPosition(t[0] + PANEL_PAD, buttonY)
        cancelButton.setWidth(halfW)
        cancelButton.setPosition(t[0] + PANEL_PAD + halfW + 8, buttonY)
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    override fun tickPanel() {
        tickInvalidFlash()
        confirmButton.active = canConfirm()
    }

    override fun drawContent(graphics: GuiGraphicsExtractor, t: IntArray, mouseX: Int, mouseY: Int) {
        drawPreview(graphics, t, mouseX, mouseY)
        drawLabels(graphics, t)
        drawSwitch(graphics, t, stagedType)
    }

    private fun drawPreview(graphics: GuiGraphicsExtractor, t: IntArray, mouseX: Int, mouseY: Int) {
        val p = previewRect(t)
        val textureId = stagedTextureId
        if (textureId == null) {
            graphics.centeredText(
                client.font, Component.translatable("simpleskinswapper.screen.add.none"),
                (p[0] + p[2]) / 2, (p[1] + p[3]) / 2 - client.font.lineHeight / 2, 0xFFAAAAAA.toInt()
            )
            return
        }
        drawRotatablePreview(graphics, p, textureId, stagedType == SkinType.SLIM, mouseX, mouseY)
    }

    private fun drawLabels(graphics: GuiGraphicsExtractor, t: IntArray) {
        val x = t[0] + PANEL_PAD
        graphics.text(client.font, Component.translatable("simpleskinswapper.screen.detail.display_name"), x, dispLabelY(), 0xFFB0B8C0.toInt())
        graphics.text(client.font, Component.translatable("simpleskinswapper.screen.detail.file_name"), x, fileLabelY(), 0xFFB0B8C0.toInt())
    }

    // ------------------------------------------------------------------
    // Overlay hooks
    // ------------------------------------------------------------------

    override fun currentSkinType(): SkinType = stagedType

    override fun toggleSkinType() {
        stagedType = if (stagedType == SkinType.CLASSIC) SkinType.SLIM else SkinType.CLASSIC
    }

    override fun onEnterPressed() {
        if (canConfirm()) {
            confirmAdd()
        } else if (focusedChild === usernameField) {
            fetchFromAccount()
        }
    }
}
