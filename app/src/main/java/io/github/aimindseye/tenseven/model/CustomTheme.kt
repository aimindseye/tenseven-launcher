package io.github.aimindseye.tenseven.model

import kotlin.math.pow

/** The optional wallpaper of a theme file: the same five values the built-in wallpapers are drawn from. */
data class CustomWallpaper(
    val top: Int,
    val bottom: Int,
    val glow: Int,
    val ridge: Int,
    val seed: Int,
)

/** Colours a theme file may set for the launcher's screens (opaque ARGB ints). Anything left out takes the default for the mode. */
data class CustomPalette(
    val background: Int? = null,
    val card: Int? = null,
    val tile: Int? = null,
    val line: Int? = null,
    val text: Int? = null,
    val muted: Int? = null,
    val dim: Int? = null,
)

/** A theme imported from a file. [mode] is the design module's stable value: "dark", "light" or "follow-system". */
data class CustomTheme(
    val id: String,
    val name: String,
    val mode: String,
    val accent: Int,
    val onAccent: Int,
    val palette: CustomPalette = CustomPalette(),
    val wallpaper: CustomWallpaper? = null,
)

/** The result of reading a theme file: a theme, or the reason it was refused (shown to the person as it is). */
sealed interface ThemeParse {
    data class Ok(
        val theme: CustomTheme,
    ) : ThemeParse

    data class Refused(
        val reason: String,
    ) : ThemeParse
}

/**
 * The theme file format (version 1) and its checks. A file is plain data: it is read, checked and drawn from, never run.
 *
 * ```
 * { "format": "tenseven-theme", "version": 1, "name": "Ocean", "mode": "dark",
 *   "accent": "#00A8DF", "onAccent": "#FFFFFF",
 *   "palette": { "background": "#000000", "card": "#141414", "tile": "#1C1C1C", "line": "#2A2A2A",
 *                "text": "#FFFFFF", "muted": "#9E9E9E", "dim": "#5E5E5E" },
 *   "wallpaper": { "top": "#1F6FEB", "bottom": "#9CD3FF", "glow": "#FFF4C2", "ridge": "#0B3C8C", "seed": 11 } }
 * ```
 *
 * Only `format`, `version`, `name`, `mode` and `accent` are required. Colours are `#RRGGBB`.
 */
object CustomThemes {
    const val FORMAT = "tenseven-theme"
    const val VERSION = 1
    const val MAX_BYTES = 64 * 1024
    const val MAX_THEMES = 12
    const val MAX_NAME = 40
    const val FILE_SUFFIX = ".tenseventheme.json"
    private val MODES = setOf("dark", "light", "follow-system")

    // The built-in dark and light backgrounds the checks compare against when a file leaves the palette out.
    private const val DARK_BACKGROUND = 0x000000
    private const val LIGHT_BACKGROUND = 0xF2F4F7
    private const val DARK_TEXT = 0xFFFFFF
    private const val LIGHT_TEXT = 0x12171C

    fun parse(
        text: String,
        takenIds: Set<String> = emptySet(),
    ): ThemeParse {
        if (text.length > MAX_BYTES) return refused("The file is larger than ${MAX_BYTES / 1024} KB.")
        val root = MiniJson.parse(text.removePrefix("﻿")) as? Map<*, *> ?: return refused("The file is not valid theme JSON.")
        if (root["format"] != FORMAT) return refused("This is not a Tenseven theme file (format must be \"$FORMAT\").")
        val version = (root["version"] as? Double)?.toInt()
        if (version == null || version > VERSION) return refused("This theme file needs a newer Tenseven (version $version).")
        val name = (root["name"] as? String)?.trim().orEmpty()
        if (name.isEmpty() || name.length > MAX_NAME) return refused("The theme needs a name of 1 to $MAX_NAME characters.")
        val mode = (root["mode"] as? String)?.takeIf { it in MODES } ?: return refused("mode must be dark, light or follow-system.")
        val accent = color(root["accent"]) ?: return refused("accent must be a colour like #00A8DF.")
        val onAccent =
            if (root["onAccent"] == null) {
                if (contrast(0xFFFFFF, accent) >= contrast(0x000000, accent)) 0xFFFFFF else 0x000000
            } else {
                color(root["onAccent"]) ?: return refused("onAccent must be a colour like #FFFFFF.")
            }
        val palette = readPalette(root["palette"]) ?: return refused("palette has a value that is not a colour like #141414.")
        val wallpaper =
            if (root["wallpaper"] == null) null else readWallpaper(root["wallpaper"]) ?: return refused("wallpaper needs top, bottom, glow and ridge colours and a seed from 0 to 9999.")
        val problem = readability(mode, accent, onAccent, palette)
        if (problem != null) return refused(problem)
        val theme = CustomTheme(uniqueId(name, takenIds), name, mode, accent or OPAQUE, onAccent or OPAQUE, palette, wallpaper)
        return ThemeParse.Ok(theme)
    }

    private fun refused(reason: String) = ThemeParse.Refused(reason)

    private fun readability(
        mode: String,
        accent: Int,
        onAccent: Int,
        palette: CustomPalette,
    ): String? {
        val backgrounds =
            palette.background?.let { listOf(it) }
                ?: when (mode) {
                    "dark" -> listOf(DARK_BACKGROUND)
                    "light" -> listOf(LIGHT_BACKGROUND)
                    else -> listOf(DARK_BACKGROUND, LIGHT_BACKGROUND)
                }
        val texts =
            palette.text?.let { listOf(it) }
                ?: when (mode) {
                    "dark" -> listOf(DARK_TEXT)
                    "light" -> listOf(LIGHT_TEXT)
                    else -> listOf(DARK_TEXT, LIGHT_TEXT)
                }
        if (palette.background != null || palette.text != null) {
            // Pair a custom colour with the matching default of its own mode so the check is not against a mixed pair.
            if (backgrounds.size == 1 && texts.size == 1 && contrast(texts[0], backgrounds[0]) < TEXT_CONTRAST) {
                return "The text colour is hard to read on the background (needs a contrast of at least 4.5)."
            }
        }
        if (backgrounds.size == 1 && palette.muted != null && contrast(palette.muted, backgrounds[0]) < MUTED_CONTRAST) {
            return "The muted colour is hard to read on the background (needs a contrast of at least 3)."
        }
        if (backgrounds.any { contrast(accent, it) < ACCENT_CONTRAST }) {
            return "The accent colour is hard to see on the background (needs a contrast of at least 3)."
        }
        if (contrast(onAccent, accent) < ACCENT_CONTRAST) return "onAccent is hard to read on the accent colour (needs a contrast of at least 3)."
        return null
    }

    private fun readPalette(value: Any?): CustomPalette? {
        if (value == null) return CustomPalette()
        val map = value as? Map<*, *> ?: return null
        fun pick(key: String): Pair<Boolean, Int?> = if (map[key] == null) true to null else color(map[key]).let { (it != null) to it }
        val parts = listOf("background", "card", "tile", "line", "text", "muted", "dim").map { it to pick(it) }
        if (parts.any { !it.second.first }) return null
        val v = parts.associate { it.first to it.second.second?.or(OPAQUE) }
        return CustomPalette(v["background"], v["card"], v["tile"], v["line"], v["text"], v["muted"], v["dim"])
    }

    private fun readWallpaper(value: Any?): CustomWallpaper? {
        val map = value as? Map<*, *> ?: return null
        val seed = (map["seed"] as? Double)?.toInt() ?: return null
        if (seed !in 0..MAX_SEED) return null
        return CustomWallpaper(
            top = color(map["top"])?.or(OPAQUE) ?: return null,
            bottom = color(map["bottom"])?.or(OPAQUE) ?: return null,
            glow = color(map["glow"])?.or(OPAQUE) ?: return null,
            ridge = color(map["ridge"])?.or(OPAQUE) ?: return null,
            seed = seed,
        )
    }

    /** `#RRGGBB` as 0xRRGGBB, else null. */
    fun color(value: Any?): Int? {
        val text = value as? String ?: return null
        if (text.length != 7 || text[0] != '#') return null
        return text.substring(1).toIntOrNull(16)?.takeIf { text.substring(1).all { c -> c.isDigit() || c in 'a'..'f' || c in 'A'..'F' } }
    }

    /** WCAG contrast ratio of two RRGGBB colours. */
    fun contrast(
        a: Int,
        b: Int,
    ): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun luminance(rgb: Int): Double {
        fun channel(shift: Int): Double {
            val c = ((rgb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    private fun uniqueId(
        name: String,
        taken: Set<String>,
    ): String {
        val slug = name.lowercase().map { if (it.isLetterOrDigit()) it else '-' }.joinToString("").trim('-').replace(Regex("-+"), "-").ifEmpty { "theme" }
        var id = "custom-$slug"
        var n = 2
        while (id in taken) id = "custom-$slug-${n++}"
        return id
    }

    private fun hex(rgb: Int) = "#%06X".format(rgb and 0xFFFFFF)

    /** The theme as a file (the export, and also how themes are stored). */
    fun toJson(theme: CustomTheme): String {
        val p = theme.palette
        val palette =
            listOfNotNull(
                p.background?.let { "\"background\": \"${hex(it)}\"" },
                p.card?.let { "\"card\": \"${hex(it)}\"" },
                p.tile?.let { "\"tile\": \"${hex(it)}\"" },
                p.line?.let { "\"line\": \"${hex(it)}\"" },
                p.text?.let { "\"text\": \"${hex(it)}\"" },
                p.muted?.let { "\"muted\": \"${hex(it)}\"" },
                p.dim?.let { "\"dim\": \"${hex(it)}\"" },
            )
        return buildString {
            append("{\n  \"format\": \"$FORMAT\",\n  \"version\": $VERSION,\n")
            append("  \"name\": ${MiniJson.quote(theme.name)},\n  \"mode\": \"${theme.mode}\",\n")
            append("  \"accent\": \"${hex(theme.accent)}\",\n  \"onAccent\": \"${hex(theme.onAccent)}\"")
            if (palette.isNotEmpty()) append(",\n  \"palette\": { ${palette.joinToString(", ")} }")
            theme.wallpaper?.let {
                append(",\n  \"wallpaper\": { \"top\": \"${hex(it.top)}\", \"bottom\": \"${hex(it.bottom)}\", ")
                append("\"glow\": \"${hex(it.glow)}\", \"ridge\": \"${hex(it.ridge)}\", \"seed\": ${it.seed} }")
            }
            append("\n}\n")
        }
    }

    /** Stored themes: a JSON array of theme files; entries that no longer pass are dropped. */
    fun parseStored(text: String?): List<CustomTheme> {
        val list = (if (text.isNullOrBlank()) null else MiniJson.parse(text)) as? List<*> ?: return emptyList()
        val out = mutableListOf<CustomTheme>()
        for (entry in list) {
            val map = entry as? Map<*, *> ?: continue
            val id = map["id"] as? String ?: continue
            val file = map["file"] as? String ?: continue
            val parsed = parse(file) as? ThemeParse.Ok ?: continue
            out.add(parsed.theme.copy(id = id))
        }
        return out.take(MAX_THEMES)
    }

    fun formatStored(themes: List<CustomTheme>): String =
        themes.joinToString(prefix = "[", postfix = "]", separator = ",") {
            "{\"id\":${MiniJson.quote(it.id)},\"file\":${MiniJson.quote(toJson(it))}}"
        }

    /** The stored value of the accent preference while a custom theme is chosen. */
    const val ACCENT_VALUE = "custom"
    private const val OPAQUE = 0xFF000000.toInt()
    private const val TEXT_CONTRAST = 4.5
    private const val MUTED_CONTRAST = 3.0
    private const val ACCENT_CONTRAST = 3.0
    private const val MAX_SEED = 9999
}
