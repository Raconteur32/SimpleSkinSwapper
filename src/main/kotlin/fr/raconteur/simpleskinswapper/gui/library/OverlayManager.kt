package fr.raconteur.simpleskinswapper.gui.library

import fr.raconteur.simpleskinswapper.gui.SkinEntry
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent

/**
 * Owns the library screen's full-screen overlay lifecycle: the open detail/add panels,
 * the shared confirmation popup, re-attach on resize (init clears every widget), raising
 * the open overlays back to the top of the widget order after card rebuilds, pruning of
 * fully-closed overlays, and the overlay input contracts (click tri-state, key/char
 * forwarding). The panels keep their `parent: SkinLibraryScreen` back-reference — the
 * manager is the screen's collaborator, not their parent.
 */
internal class OverlayManager(private val screen: SkinLibraryScreen) {

    // Open skin detail overlay (null = closed). Registered as a widget while open.
    var detail: SkinDetailPanel? = null
        private set

    // Open add-skin overlay (null = closed), opened from the trailing "+" card.
    var addPanel: SkinAddPanel? = null
        private set

    // The shared destructive-action confirmation, above every panel while open.
    var confirmPopup: ConfirmPopup? = null
        private set

    /** True while any full-screen overlay covers the screen. */
    val anyOpen: Boolean get() = detail != null || addPanel != null

    internal fun openDetail(card: SkinLibraryCard) {
        if (anyOpen) return
        val panel = SkinDetailPanel(screen)
        panel.open(card)
        detail = panel
        screen.registerOverlayWidget(panel)
    }

    /** Opens the add-skin overlay from the trailing "+" card. */
    internal fun openAddPanel() {
        if (anyOpen) return
        val card = screen.trailingAddCard() ?: return
        val panel = SkinAddPanel(screen)
        panel.open(card)
        addPanel = panel
        screen.registerOverlayWidget(panel)
    }

    /** Shows the shared confirmation popup; any previous popup is replaced. */
    internal fun openConfirmPopup(popup: ConfirmPopup) {
        confirmPopup = popup
        popup.show()
    }

    internal fun closeConfirmPopup() {
        confirmPopup = null
    }

    /** init() also runs on window resize (rebuildWidgets clears every widget first):
     *  re-attach the overlays so they survive the resize instead of turning into
     *  ghosts that swallow all input without rendering. */
    internal fun reattachOverlays() {
        detail?.let {
            it.onScreenResized(screen.width, screen.height)
            screen.registerOverlayWidget(it)
        }
        addPanel?.let {
            it.onScreenResized(screen.width, screen.height)
            screen.registerOverlayWidget(it)
        }
    }

    /** Re-appends the open overlays so they stay last in the widget order (on top). */
    internal fun raiseOverlays() {
        detail?.let { screen.removeOverlayWidget(it); screen.registerOverlayWidget(it) }
        addPanel?.let { screen.removeOverlayWidget(it); screen.registerOverlayWidget(it) }
    }

    /** Fully-closed overlays are unregistered outside of their own render pass. An
     *  overlay that is no longer a screen child (cleared by a widget rebuild without
     *  init) would swallow all input invisibly — drop it too. Runs every frame. */
    internal fun pruneClosed() {
        detail = pruned(detail)
        addPanel = pruned(addPanel)
    }

    /** Drops an overlay that was cleared by a widget rebuild or fully closed itself. */
    private fun <T> pruned(panel: T?): T? where T : AbstractWidget, T : SkinOverlayPanel {
        if (panel == null) return null
        if (!screen.children().contains(panel)) return null
        if (panel.isRemovePending) {
            screen.removeOverlayWidget(panel)
            return null
        }
        return panel
    }

    /** Re-points the detail panel at the fresh entry after a reload, closing it if the skin is gone. */
    internal fun rebindDetail(entries: List<SkinEntry>) {
        val d = detail ?: return
        val id = d.entrySkinId ?: return
        val fresh = entries.firstOrNull { it.skinId == id }
        if (fresh == null) d.close(instant = true) else d.rebind(fresh)
    }

    /** Model switch: re-points the panel at the sibling before the rebuild, matching by
     *  skin id (rebindDetail would force-close over the vanished original id). */
    internal fun rebindDetailIfSkin(id: String, fresh: SkinEntry) {
        if (detail?.entrySkinId == id) detail?.rebind(fresh)
    }

    /** Model switch: folds the closing panel into the sibling's fresh card slot. */
    internal fun retargetDetailTo(x: Int, y: Int, w: Int, h: Int) {
        detail?.retargetTo(x, y, w, h)
    }

    /** Overlay panels own the click entirely while open; `null` means no overlay consumed it. */
    internal fun handleOverlayClick(click: MouseButtonEvent, doubled: Boolean): Boolean? {
        detail?.let { d ->
            val handled = d.mouseClicked(click, doubled)
            if (handled) screen.setFocused(d)
            return handled
        }
        addPanel?.let { d ->
            val handled = d.mouseClicked(click, doubled)
            if (handled) screen.setFocused(d)
            return handled
        }
        return null
    }

    /** Popup first (Esc cancels), then the open overlay panels. */
    internal fun handleKeyPressed(event: KeyEvent): Boolean? {
        confirmPopup?.let { return it.onEsc() }
        detail?.let { return it.keyPressed(event) }
        addPanel?.let { return it.keyPressed(event) }
        return null
    }

    internal fun handleCharTyped(event: CharacterEvent): Boolean? {
        detail?.let { return it.charTyped(event) }
        addPanel?.let { return it.charTyped(event) }
        return null
    }

    internal fun handleMouseDragged(click: MouseButtonEvent, offsetX: Double, offsetY: Double): Boolean? {
        detail?.let { return it.mouseDragged(click, offsetX, offsetY) }
        addPanel?.let { return it.mouseDragged(click, offsetX, offsetY) }
        return null
    }

    internal fun handleMouseReleased(click: MouseButtonEvent): Boolean? {
        detail?.let { return it.mouseReleased(click) }
        addPanel?.let { return it.mouseReleased(click) }
        return null
    }

    internal fun handleMouseScrolled(mouseX: Double, mouseY: Double, hozAmount: Double, vertAmount: Double): Boolean? {
        detail?.let { return it.mouseScrolled(mouseX, mouseY, hozAmount, vertAmount) }
        addPanel?.let { return it.mouseScrolled(mouseX, mouseY, hozAmount, vertAmount) }
        return null
    }
}
