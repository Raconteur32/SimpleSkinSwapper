package fr.raconteur.simpleskinswapper.gui.library

/**
 * Fixed category color palette: ten hues, each with a pastel and a vivid variant (ARGB).
 * Category colors always come from this palette — never free-form.
 */
object SkinCategoryPalette {

    data class Entry(val hue: String, val pastel: Int, val vivid: Int)

    @JvmField
    val ENTRIES = listOf(
        Entry("red", 0xFFFCA5A5.toInt(), 0xFFEF4444.toInt()),
        Entry("orange", 0xFFFDBA74.toInt(), 0xFFF97316.toInt()),
        Entry("yellow", 0xFFFCD34D.toInt(), 0xFFF59E0B.toInt()),
        Entry("lime", 0xFFBEF264.toInt(), 0xFF84CC16.toInt()),
        Entry("green", 0xFF86EFAC.toInt(), 0xFF22C55E.toInt()),
        Entry("cyan", 0xFF67E8F9.toInt(), 0xFF06B6D4.toInt()),
        Entry("blue", 0xFF93C5FD.toInt(), 0xFF3B82F6.toInt()),
        Entry("violet", 0xFFA5B4FC.toInt(), 0xFF6366F1.toInt()),
        Entry("pink", 0xFFF9A8D4.toInt(), 0xFFEC4899.toInt()),
        Entry("brown", 0xFFC8A882.toInt(), 0xFF8B5E3C.toInt())
    )

    /** Default color for new categories: vivid blue. */
    const val DEFAULT_HEX = "#3B82F6"

    /** Flat swatch list for pickers: vivid row first, then pastel row. */
    @JvmStatic
    fun swatches(): List<Int> = ENTRIES.flatMap { listOf(it.vivid, it.pastel) }

    @JvmStatic
    fun toHex(argb: Int): String = String.format(java.util.Locale.ROOT, "#%06X", argb and 0xFFFFFF)

    @JvmStatic
    fun parse(hex: String): Int = try {
        0xFF000000.toInt() or Integer.parseInt(hex.removePrefix("#"), 16)
    } catch (e: NumberFormatException) {
        parse(DEFAULT_HEX)
    }
}
