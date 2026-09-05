package fr.raconteur.simpleskinswapper.gui.library

import fr.raconteur.simpleskinswapper.data.FabricSkinLibraryEnv
import fr.raconteur.simpleskinswapper.library.LibraryMigrator
import fr.raconteur.simpleskinswapper.library.SkinCardStore
import fr.raconteur.simpleskinswapper.library.SkinRegistry
import fr.raconteur.simpleskinswapper.library.SkinRecord
import fr.raconteur.simpleskinswapper.library.TextureHashing
import fr.raconteur.simpleskinswapper.library.TextureLifecycle
import fr.raconteur.simpleskinswapper.library.TextureNamer

/** Single production wiring for the library core. Every facade shares these instances —
 *  two registries would diverge in memory and overwrite each other's saves. */
internal object LibraryServices {
    val registry by lazy { SkinRegistry(FabricSkinLibraryEnv) }
    val cards by lazy { SkinCardStore(FabricSkinLibraryEnv) }
    val namer by lazy { TextureNamer(FabricSkinLibraryEnv, TextureHashing.sha256) }
    val lifecycle by lazy { TextureLifecycle(FabricSkinLibraryEnv, registry, namer) }
    val migrator by lazy { LibraryMigrator(FabricSkinLibraryEnv, registry, cards, TextureHashing.sha256) }
}

/** GUI-facing singleton over the skin registry. */
object SkinRecords {

    private val instance get() = LibraryServices.registry

    @JvmStatic fun all(): List<SkinRecord> = instance.all()
    @JvmStatic fun findById(id: String): SkinRecord? = instance.findById(id)
    @JvmStatic fun find(textureHash: String, model: String): SkinRecord? = instance.find(textureHash, model)
    @JvmStatic fun create(textureHash: String, model: String, name: String, file: String): SkinRecord? =
        instance.create(textureHash, model, name, file)
    @JvmStatic fun rename(id: String, name: String) = instance.rename(id, name)
    @JvmStatic fun remove(id: String): Boolean = instance.remove(id)
}

/** GUI-facing singleton over the texture lifecycle (ingest, delete, external pruning). */
object SkinLifecycle {

    private val instance get() = LibraryServices.lifecycle

    /** Ingests a staged PNG; null when undecodable or the pair already exists. */
    @JvmStatic fun createSkin(png: ByteArray, model: String, name: String): SkinRecord? =
        instance.createSkin(png, model, name)

    /** Deletes a skin and its texture when no other skin shares it. */
    @JvmStatic fun removeSkin(id: String): Boolean = instance.removeSkin(id)

    /** Skins whose texture file vanished externally; callers purge their cards. */
    @JvmStatic fun pruneMissingTextures(): List<String> = instance.pruneMissingTextures()
}
