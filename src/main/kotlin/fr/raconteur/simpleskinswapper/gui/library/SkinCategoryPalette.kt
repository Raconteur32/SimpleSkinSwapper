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

    /** Default color for new categories: blue dye wool color. */
    const val DEFAULT_HEX = "#3C44AA"

    @JvmStatic
    fun toHex(argb: Int): String = String.format(java.util.Locale.ROOT, "#%06X", argb and 0xFFFFFF)

    @JvmStatic
    fun parse(hex: String): Int = try {
        0xFF000000.toInt() or Integer.parseInt(hex.removePrefix("#"), 16)
    } catch (e: NumberFormatException) {
        parse(DEFAULT_HEX)
    }
}
