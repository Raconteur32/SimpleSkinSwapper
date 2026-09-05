package fr.raconteur.simpleskinswapper.data

import java.nio.file.Path

/**
 * The library core's single seam to the environment: where the skins folder lives.
 * Production wires the game dir (see [FabricSkinLibraryEnv]); tests pass a temp folder,
 * keeping every core class runnable under plain JVM unit tests.
 */
fun interface SkinLibraryEnv {
    fun skinsDir(): Path
}
