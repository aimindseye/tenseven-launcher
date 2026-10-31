package io.github.aimindseye.tenseven.model

/**
 * A small strict JSON reader for theme files: objects, arrays, strings, numbers, true/false/null. It builds plain
 * Kotlin values (Map, List, String, Double, Boolean, null) and nothing is ever executed. Written here so the parser
 * behaves the same in unit tests as on the phone.
 */
internal object MiniJson {
    private const val MAX_DEPTH = 8

    /** The parsed value, or null when [text] is not valid JSON. */
    fun parse(text: String): Any? = runCatching { Reader(text).readDocument() }.getOrNull()

    /** A JSON string literal for [value], escaped. */
    fun quote(value: String): String =
        buildString {
            append('"')
            for (c in value) {
                when {
                    c == '"' -> append("\\\"")
                    c == '\\' -> append("\\\\")
                    c == '\n' -> append("\\n")
                    c == '\r' -> append("\\r")
                    c == '\t' -> append("\\t")
                    c < ' ' -> append("\\u%04x".format(c.code))
                    else -> append(c)
                }
            }
            append('"')
        }

    private class Reader(
        private val s: String,
    ) {
        private var i = 0

        fun readDocument(): Any? {
            val v = readValue(0)
            skip()
            require(i == s.length) { "trailing text" }
            return v
        }

        private fun skip() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        private fun readValue(depth: Int): Any? {
            require(depth <= MAX_DEPTH) { "too deep" }
            skip()
            require(i < s.length) { "end" }
            return when (val c = s[i]) {
                '{' -> readObject(depth)
                '[' -> readArray(depth)
                '"' -> readString()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c.isDigit()) readNumber() else error("unexpected")
            }
        }

        private fun literal(
            word: String,
            value: Any?,
        ): Any? {
            require(s.startsWith(word, i)) { "bad literal" }
            i += word.length
            return value
        }

        private fun readNumber(): Double {
            val start = i
            if (s[i] == '-') i++
            while (i < s.length && (s[i].isDigit() || s[i] in ".eE+-")) i++
            return s.substring(start, i).toDouble()
        }

        private fun readString(): String {
            i++
            val out = StringBuilder()
            while (true) {
                require(i < s.length) { "unterminated string" }
                val c = s[i++]
                when {
                    c == '"' -> return out.toString()
                    c == '\\' -> {
                        require(i < s.length) { "bad escape" }
                        when (val e = s[i++]) {
                            '"', '\\', '/' -> out.append(e)
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'u' -> {
                                require(i + 4 <= s.length) { "bad unicode" }
                                out.append(s.substring(i, i + 4).toInt(16).toChar())
                                i += 4
                            }
                            else -> error("bad escape")
                        }
                    }
                    c < ' ' -> error("control character")
                    else -> out.append(c)
                }
            }
        }

        private fun readArray(depth: Int): List<Any?> {
            i++
            val list = mutableListOf<Any?>()
            skip()
            if (i < s.length && s[i] == ']') {
                i++
                return list
            }
            while (true) {
                list.add(readValue(depth + 1))
                skip()
                require(i < s.length) { "end" }
                when (s[i++]) {
                    ',' -> Unit
                    ']' -> return list
                    else -> error("expected , or ]")
                }
            }
        }

        private fun readObject(depth: Int): Map<String, Any?> {
            i++
            val map = linkedMapOf<String, Any?>()
            skip()
            if (i < s.length && s[i] == '}') {
                i++
                return map
            }
            while (true) {
                skip()
                require(i < s.length && s[i] == '"') { "expected key" }
                val key = readString()
                skip()
                require(i < s.length && s[i++] == ':') { "expected :" }
                map[key] = readValue(depth + 1)
                skip()
                require(i < s.length) { "end" }
                when (s[i++]) {
                    ',' -> Unit
                    '}' -> return map
                    else -> error("expected , or }")
                }
            }
        }
    }
}
