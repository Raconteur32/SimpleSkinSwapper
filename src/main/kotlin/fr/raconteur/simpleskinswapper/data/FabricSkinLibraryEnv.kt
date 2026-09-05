package fr.raconteur.simpleskinswapper.data

import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Path

/** Production environment: the game directory's skins folder. */
object FabricSkinLibraryEnv : SkinLibraryEnv {
    override fun skinsDir(): Path = FabricLoader.getInstance().gameDir.resolve("skins")
}
