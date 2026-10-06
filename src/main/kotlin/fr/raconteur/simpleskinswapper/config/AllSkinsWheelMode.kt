package fr.raconteur.simpleskinswapper.config

/**
 * When the skin wheel shows an all-skins group: never, only when no category
 * contributes a wheel (fallback), or always ahead of the category wheels.
 * Serialized by Gson as the constant name ("NEVER"/"FALLBACK"/"ALWAYS").
 */
enum class AllSkinsWheelMode {
    NEVER,
    FALLBACK,
    ALWAYS
}
