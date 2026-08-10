package fr.raconteur.simpleskinswapper.gui

import fr.raconteur.simpleskinswapper.SimpleSkinSwapper
import net.fabricmc.loader.api.FabricLoader
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object SkinShuffleImporter {
    private val SOURCE_DIR = FabricLoader.getInstance().gameDir
        .resolve("config").resolve("skinshuffle").resolve("skins")
    private val MARKER_FILE = SOURCE_DIR.resolve(".sssimported")
    private val TARGET_DIR = FabricLoader.getInstance().gameDir
        .resolve("skins")

    @JvmStatic
    fun importIfNeeded() {
        if (!Files.isDirectory(SOURCE_DIR) || Files.exists(MARKER_FILE)) return

        SimpleSkinSwapper.LOGGER.info("SkinShuffleImporter: importing skins from {}", SOURCE_DIR)

        try {
            Files.createDirectories(TARGET_DIR)
            var count = 0

            Files.list(SOURCE_DIR).use { files ->
                for (file in files.toList()) {
                    if (!file.fileName.toString().lowercase().endsWith(".png")) continue
                    val dest = TARGET_DIR.resolve(file.fileName)
                    if (!Files.exists(dest)) {
                        Files.copy(file, dest, StandardCopyOption.COPY_ATTRIBUTES)
                        count++
                    }
                }
            }

            Files.createFile(MARKER_FILE)
            SimpleSkinSwapper.LOGGER.info("SkinShuffleImporter: imported {} skin(s).", count)
        } catch (e: IOException) {
            SimpleSkinSwapper.LOGGER.warn("SkinShuffleImporter: import failed: {}", e.message)
        }
    }
}
