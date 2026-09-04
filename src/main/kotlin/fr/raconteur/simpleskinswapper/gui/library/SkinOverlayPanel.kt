package fr.raconteur.simpleskinswapper.gui.library

/**
 * Contract shared by the full-screen overlay panels (detail, add): lets the screen
 * prune and re-attach them uniformly across widget rebuilds and resizes.
 */
internal interface SkinOverlayPanel {
    /** True once the panel has fully closed and must be unregistered. */
    val isRemovePending: Boolean

    /** Syncs the panel's widget bounds after a window resize. */
    fun onScreenResized(width: Int, height: Int)
}
