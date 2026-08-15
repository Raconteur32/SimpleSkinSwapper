package fr.raconteur.simpleskinswapper.gui.config

import dev.isxander.yacl3.api.utils.Dimension
import dev.isxander.yacl3.gui.AbstractWidget
import dev.isxander.yacl3.gui.OptionListWidget
import dev.isxander.yacl3.gui.controllers.ListEntryWidget
import fr.raconteur.simpleskinswapper.config.ServerCommand
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

/**
 * Widget of a server-command list entry: [address field] [command field].
 * Removal is handled by YACL's native per-entry remove button; emptying the
 * command field also deletes the entry on save since blank commands are not persisted.
 *
 * Focus is managed explicitly: since MC 26.x, clicking an [EditBox] no longer focuses
 * it by itself (the container hierarchy is expected to propagate setFocused), and YACL's
 * ListEntryWidget does not propagate it either — so without the explicit setFocused(true)
 * below, canConsumeInput() stays false and typing is silently ignored.
 */
class ServerCommandControllerElement(
    private val control: ServerCommandController,
    dim: Dimension<Int>,
) : AbstractWidget(dim) {

    private val addressBox: EditBox =
        EditBox(textRenderer, 0, 0, 0, 0, Component.translatable("simpleskinswapper.config.server_address"))
    private val commandBox: EditBox =
        EditBox(textRenderer, 0, 0, 0, 0, Component.translatable("simpleskinswapper.config.server_command"))

    private val boxes: List<EditBox> = listOf(addressBox, commandBox)
    private val children: List<GuiEventListener> = boxes
    private var focusedChild: GuiEventListener? = null

    init {
        // layout() must run before setValue: EditBox.setValue -> moveCursorToEnd computes the
        // horizontal scroll offset (displayPos) from the CURRENT width; with width=0 the text
        // is scrolled entirely out of view and the field renders as if empty (showing the
        // hint) until a click recomputes displayPos with the real width.
        layout()
        val initial = control.option().pendingValue()
        addressBox.setMaxLength(256)
        addressBox.value = initial.address
        addressBox.setHint(Component.translatable("simpleskinswapper.config.server_address.hint"))
        addressBox.setResponder { push() }
        commandBox.setMaxLength(256)
        commandBox.value = initial.command
        commandBox.setHint(Component.translatable("simpleskinswapper.config.server_command.hint"))
        commandBox.setResponder { push() }
    }

    /** Pushes the current field contents to the option as a new pending value. */
    private fun push() {
        control.option().requestSet(ServerCommand(addressBox.value, commandBox.value))
    }

    override fun setDimension(dim: Dimension<Int>) {
        super.setDimension(dim)
        layout()
    }

    private fun layout() {
        val dim = dimension
        val gap = 4
        val fieldsWidth = dim.width() - gap
        val addressWidth = fieldsWidth / 2

        addressBox.x = dim.x()
        addressBox.y = dim.y()
        addressBox.width = addressWidth
        addressBox.height = dim.height()

        commandBox.x = dim.x() + addressWidth + gap
        commandBox.y = dim.y()
        commandBox.width = fieldsWidth - addressWidth
        commandBox.height = dim.height()
    }

    //? if >=26.1 {
    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        repairListEntryFocus()
        addressBox.extractRenderState(graphics, mouseX, mouseY, delta)
        commandBox.extractRenderState(graphics, mouseX, mouseY, delta)
    }
    //?} else {
    /*override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        repairListEntryFocus()
        addressBox.render(graphics, mouseX, mouseY, delta)
        commandBox.render(graphics, mouseX, mouseY, delta)
    }
    *///?}

    // --- Focus repair (vanilla quirk workaround) ---
    //
    // Vanilla's ContainerObjectSelectionList.Entry.setFocused(child) unfocuses the previous
    // child even when it IS the new one. For a container child (YACL's ListEntryWidget), the
    // ContainerEventHandler default setFocused(false) clears the container's focused child via
    // setFocused(null) — wiping, right after our mouseClicked returns, the focus we had just
    // re-acquired when clicking from one field to the other. Since OptionListWidget routes
    // charTyped to entries solely through ListEntryWidget.getFocused(), keystrokes then reach
    // nothing. We can't subclass ListEntryWidget (YACL instantiates it), so after a click that
    // focused one of our fields we re-assert the wrapper's focus on the next render, once the
    // vanilla wipe has settled. The one-shot flag ensures we never steal focus back from the
    // entry's own buttons (remove/move), which suffer the same vanilla wipe.

    private var focusRepairPending = false
    private var listEntryWidget: ListEntryWidget? = null

    private fun repairListEntryFocus() {
        if (!focusRepairPending) return
        var lw = listEntryWidget
        if (lw == null || this !in lw.children()) {
            lw = findListEntryWidget()?.also { listEntryWidget = it } ?: return // retry next frame
        }
        focusRepairPending = false
        if (lw.focused !== this) {
            lw.setFocused(this)
        }
    }

    /** Finds the [ListEntryWidget] YACL wrapped this element in, by walking the screen tree. */
    private fun findListEntryWidget(): ListEntryWidget? {
        //? if >=26.2 {
        val screen = client.gui.screen() ?: return null
        //?} else {
        /*val screen = client.screen ?: return null
        *///?}
        for (child in screen.children()) {
            if (child !is OptionListWidget) continue
            for (entry in child.children()) {
                val widget = (entry as? OptionListWidget.OptionEntry)?.widget ?: continue
                if (widget is ListEntryWidget && this in widget.children()) return widget
            }
        }
        return null
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        for (child in children) {
            if (child.isMouseOver(event.x, event.y) && child.mouseClicked(event, doubleClick)) {
                focusedChild = child
                boxes.forEach { it.isFocused = it === child }
                // Vanilla is about to wipe the wrapper's focus (see above): repair next frame.
                focusRepairPending = true
                return true
            }
        }
        focusedChild = null
        boxes.forEach { it.isFocused = false }
        return false
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean =
        focusedChild?.mouseReleased(event) == true

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean =
        focusedChild?.mouseDragged(event, dx, dy) == true

    override fun keyPressed(event: KeyEvent): Boolean =
        focusedChild?.keyPressed(event) == true

    override fun keyReleased(event: KeyEvent): Boolean =
        focusedChild?.keyReleased(event) == true

    override fun charTyped(event: CharacterEvent): Boolean =
        focusedChild?.charTyped(event) == true

    private var widgetFocused = false

    override fun setFocused(focused: Boolean) {
        widgetFocused = focused
    }

    override fun isFocused(): Boolean = widgetFocused

    override fun unfocus() {
        focusedChild = null
        boxes.forEach { it.isFocused = false }
    }

    override fun matchesSearch(query: String): Boolean =
        addressBox.value.contains(query, ignoreCase = true) ||
            commandBox.value.contains(query, ignoreCase = true)
}
