package fr.raconteur.simpleskinswapper.arch

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.jupiter.api.Test

/**
 * Architecture rules over the stonecutter-generated source tree of the version project
 * running the suite. The tree directory arrives through the `konsist.tree` system
 * property (see the shared build script), so the same rules run against all four
 * versions' live code — the per-tree counterpart of `detektAll` (old-version branches
 * are live code in their tree, commented text in the VCS).
 *
 * Rules live here, one test per rule, each carrying its justifying comment. One wave
 * of rules = one commit (detekt-ledger discipline, see the code-review checklist).
 */
class ArchitectureTest {

    private val scope =
        Konsist.scopeFromDirectory(System.getProperty("konsist.tree") ?: error("konsist.tree not set"))

    @Test
    fun `no java sql imports`() {
        scope.files.assertTrue { !it.hasImport { import -> import.name.startsWith("java.sql.") } }
    }

    /** Review checklist "frozen wire formats": Gson exists for the MineSkin wire contract and
     *  the config file loader — any new consumer is a deliberate decision, not an accident. */
    @Test
    fun `gson stays confined to its two deliberate consumers`() {
        val allowed = setOf("SimpleSkinSwapperConfig.kt", "MineSkinUploader.kt")
        scope.files.assertTrue {
            allowed.any { name -> it.path.endsWith(name) } ||
                !it.hasImport { import -> import.name.startsWith("com.google.gson") }
        }
    }

    /**
     * Layering (docs/atlas.md §1, verified imports as of 2026-09-26). Each core package may
     * import only the internal sub-packages listed; root-level symbols (PlayerMessaging
     * helpers, SimpleSkinSwapper, SimpleSkinSwapperClient) are always allowed. A violation
     * here means a new core dependency — update the map AND the atlas together.
     */
    @Test
    fun `core package layering matches the atlas`() {
        val allowedInternalImports = mapOf(
            // leaf: persistence helpers only, imports nothing internal
            "fr.raconteur.simpleskinswapper.data" to setOf<String>(),
            // registry model sits directly on data
            "fr.raconteur.simpleskinswapper.library" to setOf("data."),
            // networking never reaches into the app layers
            "fr.raconteur.simpleskinswapper.networking" to setOf<String>(),
            // config consumes its own gui.config widgets (YACL wiring)
            "fr.raconteur.simpleskinswapper.config" to setOf("gui.config."),
            // swap runtime may use the facades + value types; open questions #3/#4 in the
            // atlas track exactly these two gui allowances
            "fr.raconteur.simpleskinswapper.changeskin" to setOf(
                "config.",
                "data.",
                "library.",
                "networking.",
            ),
        )
        scope.files.assertTrue { file ->
            val pkg = file.packagee?.name
            val allowed = allowedInternalImports.entries
                .firstOrNull { pkg == it.key }?.value
                ?: return@assertTrue true
            file.imports.all { import ->
                val relative = import.name
                    .takeIf { it.startsWith("fr.raconteur.simpleskinswapper.") }
                    ?.removePrefix("fr.raconteur.simpleskinswapper.")
                    ?: return@all true
                if (!relative.contains('.')) return@all true // root-level symbol
                // an explicit same-package import carries no layering information
                if (pkg == "fr.raconteur.simpleskinswapper." + relative.substringBeforeLast('.')) {
                    return@all true
                }
                allowed.any { relative.startsWith(it) }
            }
        }
    }

    /** The GUI layers go through the swap runtime for anything remote — no direct
     *  networking import from gui/gui.library/gui.config (true today, keep it that way). */
    @Test
    fun `gui never talks to networking directly`() {
        scope.files.assertTrue { file ->
            val pkg = file.packagee?.name
            val inGui = pkg == "fr.raconteur.simpleskinswapper.gui" ||
                pkg == "fr.raconteur.simpleskinswapper.gui.library" ||
                pkg == "fr.raconteur.simpleskinswapper.gui.config"
            !inGui || !file.hasImport { import -> import.name.startsWith("fr.raconteur.simpleskinswapper.networking") }
        }
    }

    /** Review checklist "overlay lifecycle": full-screen overlays must share the
     *  AbstractSkinOverlayPanel skeleton (animation, blur, focus plumbing, commit-on-close)
     *  so the screen can re-attach and prune them uniformly. Nobody implements
     *  SkinOverlayPanel directly except the skeleton itself. */
    @Test
    fun `overlay panels share the skeleton`() {
        scope.classes()
            .assertTrue { klass ->
                !klass.hasParentWithName("SkinOverlayPanel") ||
                    klass.name == "AbstractSkinOverlayPanel" ||
                    klass.hasParentWithName("AbstractSkinOverlayPanel")
            }
    }
}
