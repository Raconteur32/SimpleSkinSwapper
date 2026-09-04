package fr.raconteur.simpleskinswapper.gui.library

import fr.raconteur.simpleskinswapper.SimpleSkinSwapper
import net.fabricmc.loader.api.FabricLoader
import java.io.IOException
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchService

/**
 * Watches the skins folder for external changes (files dropped in, deleted from a file
 * manager). Own writes (deletes, imports, renames done by this mod) register a grace window
 * through [markSelfTriggered] so they don't count as external changes. [pollChanges] drains
 * the watch service and reports whether the library view should be rebuilt.
 */
class LibraryFileWatcher(private val onSkinsChanged: () -> Unit) {

    private val selfTriggeredFiles = HashMap<String, Long>()
    private var watchService: WatchService? = null

    fun start() {
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

    fun stop() {
        watchService?.let {
            try {
                it.close()
            } catch (ignored: IOException) {
            }
            watchService = null
        }
    }

    /** Suppresses watcher reactions to [filename] for a short grace window. */
    fun markSelfTriggered(filename: String) {
        selfTriggeredFiles[filename] = System.currentTimeMillis() + SELF_TRIGGERED_GRACE_MS
    }

    /** Cancels the grace window (e.g. the deletion failed and the file stays). */
    fun unmarkSelfTriggered(filename: String) {
        selfTriggeredFiles.remove(filename)
    }

    /** Drains pending events; runs [onSkinsChanged] when a non-self change happened. */
    fun pollChanges() {
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
            onSkinsChanged()
        }
    }

    private companion object {
        const val SELF_TRIGGERED_GRACE_MS = 1000L
    }
}
