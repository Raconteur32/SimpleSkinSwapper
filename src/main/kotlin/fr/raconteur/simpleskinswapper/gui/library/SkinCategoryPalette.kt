package fr.raconteur.simpleskinswapper.gui.library

import net.minecraft.world.item.DyeColor

/**
 * Category colors come from the 16 vanilla dyes' wool (map) colors — muted by design.
 * Entries derive from the DyeColor enum at first touch (runtime-resolved, vanilla dye
 * order), so no hex is hardcoded and every game language already names each entry.
 */
object SkinCategoryPalette {

    data class Entry(val dyeName: String, val argb: Int)

    @JvmField
    val ENTRIES: List<Entry> = DyeColor.values().map { dye ->
        Entry(dye.getName(), 0xFF000000.toInt() or dye.mapColor.col)
    }

    /** Color of a dye NAME from the running version's table; unknown names fall back to
     *  white. Categories store the name, never a hex — the displayed color is derived. */
    @JvmStatic
    fun colorOf(dyeName: String): Int =
        ENTRIES.firstOrNull { it.dyeName == dyeName }?.argb ?: ENTRIES.first { it.dyeName == "white" }.argb

    /** Dye name of a legacy stored hex, or null when it matches no dye of this version
     *  (used by the migration to convert old color codes into dye names). */
    @JvmStatic
    fun dyeNameForColor(hex: String): String? {
        val normalized = toHex(parse(hex))
        return ENTRIES.firstOrNull { toHex(it.argb) == normalized }?.dyeName
    }

    @JvmStatic
    fun toHex(argb: Int): String = String.format(java.util.Locale.ROOT, "#%06X", argb and 0xFFFFFF)

    private fun parse(hex: String): Int = try {
        0xFF000000.toInt() or Integer.parseInt(hex.removePrefix("#"), 16)
    } catch (e: NumberFormatException) {
        0xFFF9FFFE.toInt()
    }
}
