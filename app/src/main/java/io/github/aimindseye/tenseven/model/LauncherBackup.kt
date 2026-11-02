// The idea (a versioned, validated JSON file the user keeps) follows MinkLauncher OpenSource
// (github.com/katoaapps/openminilaunch, commit b9b26f7, features/backup/*), Copyright 2026 Katoa Apps, Apache
// License 2.0. The code is Tenseven's own: it covers Tenseven's preferences and uses its own small JSON reader so
// that it can be unit tested without Android.
package io.github.aimindseye.tenseven.model

/** One saved setting. */
sealed interface BackupValue {
    data class Text(val value: String) : BackupValue

    data class Number(val value: Int) : BackupValue

    data class Items(val values: Set<String>) : BackupValue
}

/**
 * Launcher settings as a backup file: which settings are included, the file format and checks on a file being
 * restored. Only the allow-listed keys below travel; setup state, and anything else in preferences, does not.
 * Pure policy, unit tested.
 */
object LauncherBackup {
    const val FORMAT = "tenseven-backup"
    const val VERSION = 1
    const val MAX_CHARACTERS = 1_000_000
    private const val MAX_ITEMS = 5_000

    private enum class Type { TEXT, NUMBER, ITEMS }

    // Preference key to value type. Keys stay equal to StartStateRepository's (a unit test checks this table).
    private val ALLOWED =
        mapOf(
            "favorite_apps" to Type.ITEMS,
            "start_tiles" to Type.ITEMS,
            "home_hidden_sections_v2" to Type.ITEMS,
            "quick_bar" to Type.TEXT,
            "home_apps" to Type.TEXT,
            "glance_widgets" to Type.TEXT,
            "grid_order" to Type.TEXT,
            "home_mode" to Type.TEXT,
            "home_style" to Type.TEXT,
            "icon_pack" to Type.TEXT,
            "bb7_home_rows" to Type.NUMBER,
            "bb7_panels" to Type.TEXT,
            "bb7_scrim" to Type.NUMBER,
            "type_to_search" to Type.NUMBER,
            "bb7_icon_size" to Type.NUMBER,
            "widgets_page" to Type.NUMBER,
            "bb7_desktop" to Type.TEXT,
            "grid_columns" to Type.NUMBER,
            "grid_labels" to Type.NUMBER,
            "lock_layer" to Type.NUMBER,
            "home_wallpaper" to Type.NUMBER,
            "hub_excluded_apps" to Type.ITEMS, // legacy backup decode only; never imply opt-in
            "hub_enabled_apps" to Type.ITEMS,
            "hub_peek_text" to Type.NUMBER,
            "appearance_mode" to Type.TEXT,
            "appearance_accent" to Type.TEXT,
            "appearance_custom" to Type.TEXT,
            "custom_themes" to Type.TEXT,
        )

    val keys: Set<String> get() = ALLOWED.keys

    /** Entries from preferences' raw values, keeping only included keys of the expected type. */
    fun select(all: Map<String, Any?>): Map<String, BackupValue> =
        buildMap {
            for ((key, raw) in all) {
                // Do not re-export the old implicit-all denylist after switching to explicit opt-in.
                if (key == "hub_excluded_apps") continue
                val value = convert(key, raw) ?: continue
                put(key, value)
            }
        }

    private fun convert(
        key: String,
        raw: Any?,
    ): BackupValue? =
        when (ALLOWED[key]) {
            Type.TEXT -> (raw as? String)?.let { BackupValue.Text(it) }
            Type.NUMBER -> (raw as? Int)?.let { BackupValue.Number(it) }
            Type.ITEMS -> (raw as? Set<*>)?.filterIsInstance<String>()?.toSet()?.let { BackupValue.Items(it) }
            null -> null
        }

    fun encode(entries: Map<String, BackupValue>): String {
        val body =
            entries.toSortedMap().entries.joinToString(",\n    ") { (key, value) ->
                "${quote(key)}: " +
                    when (value) {
                        is BackupValue.Text -> quote(value.value)
                        is BackupValue.Number -> value.value.toString()
                        is BackupValue.Items -> value.values.sorted().joinToString(",", "[", "]") { quote(it) }
                    }
            }
        return "{\n  \"format\": \"$FORMAT\",\n  \"version\": $VERSION,\n  \"settings\": {\n    $body\n  }\n}\n"
    }

    /** The settings in a backup file, or the reason it was refused. Unknown keys and wrong types are refused, not skipped. */
    fun decode(json: String): Result<Map<String, BackupValue>> =
        runCatching {
            require(json.length <= MAX_CHARACTERS) { "Backup file is too large" }
            val root = Json(json).readDocument() as? Map<*, *> ?: error("Not a backup file")
            require(root["format"] == FORMAT) { "Not a Tenseven backup" }
            val version = root["version"] as? Int ?: error("Backup has no version")
            require(version == VERSION) { "Backup version $version is not supported" }
            val settings = root["settings"] as? Map<*, *> ?: error("Backup has no settings")
            buildMap {
                for ((key, raw) in settings) {
                    val name = key as? String ?: error("Invalid setting name")
                    val type = ALLOWED[name] ?: error("Unknown setting: $name")
                    val value =
                        when (type) {
                            Type.TEXT -> (raw as? String)?.let { BackupValue.Text(it) }
                            Type.NUMBER -> (raw as? Int)?.let { BackupValue.Number(it) }
                            Type.ITEMS ->
                                (raw as? List<*>)?.takeIf { list -> list.all { it is String } && list.size <= MAX_ITEMS }
                                    ?.let { list -> BackupValue.Items(list.filterIsInstance<String>().toSet()) }
                        } ?: error("Wrong value for $name")
                    put(name, value)
                }
            }
        }

    private fun quote(text: String): String {
        val out = StringBuilder("\"")
        for (c in text) {
            when {
                c == '"' -> out.append("\\\"")
                c == '\\' -> out.append("\\\\")
                c == '\n' -> out.append("\\n")
                c == '\r' -> out.append("\\r")
                c == '\t' -> out.append("\\t")
                c < ' ' -> out.append("\\u%04x".format(c.code))
                else -> out.append(c)
            }
        }
        return out.append('"').toString()
    }

    /** Just enough JSON for this file: objects, arrays, strings, integers, booleans and null. */
    private class Json(private val text: String) {
        private var pos = 0

        fun readDocument(): Any? {
            val value = readValue(0)
            skipSpace()
            require(pos == text.length) { "Unexpected text after the backup" }
            return value
        }

        private fun readValue(depth: Int): Any? {
            require(depth < MAX_DEPTH) { "Backup is nested too deeply" }
            skipSpace()
            return when (val c = peek()) {
                '{' -> readObject(depth)
                '[' -> readArray(depth)
                '"' -> readString()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c in '0'..'9') readInt() else error("Unexpected character at $pos")
            }
        }

        private fun readObject(depth: Int): Map<String, Any?> {
            pos++
            val map = linkedMapOf<String, Any?>()
            skipSpace()
            if (peek() == '}') return map.also { pos++ }
            while (true) {
                skipSpace()
                val key = readString()
                skipSpace()
                expect(':')
                map[key] = readValue(depth + 1)
                skipSpace()
                when (next()) {
                    ',' -> continue
                    '}' -> return map
                    else -> error("Expected , or } at $pos")
                }
            }
        }

        private fun readArray(depth: Int): List<Any?> {
            pos++
            val list = mutableListOf<Any?>()
            skipSpace()
            if (peek() == ']') return list.also { pos++ }
            while (true) {
                list += readValue(depth + 1)
                require(list.size <= MAX_ITEMS) { "Too many items" }
                skipSpace()
                when (next()) {
                    ',' -> continue
                    ']' -> return list
                    else -> error("Expected , or ] at $pos")
                }
            }
        }

        private fun readString(): String {
            expect('"')
            val out = StringBuilder()
            while (true) {
                when (val c = next()) {
                    '"' -> return out.toString()
                    '\\' ->
                        when (val e = next()) {
                            '"', '\\', '/' -> out.append(e)
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000c')
                            'u' -> {
                                require(pos + 4 <= text.length) { "Bad escape" }
                                out.append(text.substring(pos, pos + 4).toInt(16).toChar())
                                pos += 4
                            }
                            else -> error("Bad escape at $pos")
                        }
                    else -> out.append(c)
                }
            }
        }

        private fun readInt(): Int {
            val start = pos
            if (peek() == '-') pos++
            while (pos < text.length && text[pos] in '0'..'9') pos++
            return text.substring(start, pos).toIntOrNull() ?: error("Bad number at $start")
        }

        private fun literal(
            word: String,
            value: Any?,
        ): Any? {
            require(text.startsWith(word, pos)) { "Unexpected text at $pos" }
            pos += word.length
            return value
        }

        private fun skipSpace() {
            while (pos < text.length && text[pos].isWhitespace()) pos++
        }

        private fun peek(): Char = if (pos < text.length) text[pos] else error("Unexpected end of backup")

        private fun next(): Char = peek().also { pos++ }

        private fun expect(c: Char) {
            require(next() == c) { "Expected $c at ${pos - 1}" }
        }

        private companion object {
            const val MAX_DEPTH = 8
            const val MAX_ITEMS = 5_000
        }
    }
}
