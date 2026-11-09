package io.github.aimindseye.tenseven.model

/**
 * The user's chosen icon presentation. "Original" never replaces installed app artwork.
 * "TenSeven" opts into the launcher glyphs. External icon packs remain separately chosen.
 * Extra apps bundled with TenSeven retain their own first-party glyphs in either mode.
 */
enum class IconPresentation(val key: String) {
    Original("original"),
    TenSeven("tenseven");

    companion object {
        fun decode(key: String?): IconPresentation = entries.firstOrNull { it.key == key } ?: TenSeven
    }
}

object IconPresentationPolicy {
    // Original mode also restores bundled-app icons; an explicit external pack takes precedence.
    @Suppress("UNUSED_PARAMETER")
    fun useGlyph(style: IconPresentation, isTenSevenExtra: Boolean, hasExternalIconPack: Boolean): Boolean =
        style == IconPresentation.TenSeven && !hasExternalIconPack

    fun useMonochrome(style: IconPresentation, hasExternalIconPack: Boolean): Boolean =
        style == IconPresentation.TenSeven && !hasExternalIconPack
}
