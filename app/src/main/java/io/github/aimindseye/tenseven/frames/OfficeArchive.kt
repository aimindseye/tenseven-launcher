package io.github.aimindseye.tenseven.frames

import java.io.InputStream
import java.util.zip.ZipInputStream

/** Bounded OOXML inspection. Never extracts files, evaluates formulas, macros, or external links. */
object OfficeArchive {
    const val MAX_BYTES = 2 * 1024 * 1024
    fun excerpt(input: InputStream, kind: String, text: (String) -> String): String {
        require(kind in setOf("word", "excel", "powerpoint"))
        var total = 0
        var entries = 0
        var pages = 0
        val snippets = mutableListOf<String>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(++entries <= 256) { "Document has too many parts" }
                val name = entry.name
                if (kind == "powerpoint" && name.matches(Regex("ppt/slides/slide[0-9]+\\.xml"))) pages++
                if (kind == "excel" && name.matches(Regex("xl/worksheets/sheet[0-9]+\\.xml"))) pages++
                val selected = when (kind) {
                    "word" -> name == "word/document.xml"
                    "excel" -> name == "xl/sharedStrings.xml" || name == "xl/worksheets/sheet1.xml"
                    else -> name == "ppt/slides/slide1.xml"
                }
                val buffer = ByteArray(8192)
                val content = java.io.ByteArrayOutputStream()
                while (true) {
                    val count = zip.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= MAX_BYTES) { "Document preview exceeds limit" }
                    if (selected) {
                        require(content.size() + count <= 256 * 1024) { "Document part exceeds limit" }
                        content.write(buffer, 0, count)
                    }
                }
                if (selected) text(content.toString("UTF-8")).takeIf(String::isNotBlank)?.let(snippets::add)
            }
        }
        require(entries > 0) { "Not an Office archive" }
        val prefix = when (kind) { "excel" -> "$pages sheets"; "powerpoint" -> "$pages slides"; else -> "Document text" }
        return AppSummaryPolicy.text(listOf(prefix, snippets.joinToString(" · ")).filter(String::isNotBlank).joinToString(" · "))
    }
}
