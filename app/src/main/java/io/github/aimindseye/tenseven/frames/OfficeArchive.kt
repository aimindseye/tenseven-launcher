package io.github.aimindseye.tenseven.frames

import java.io.InputStream
import java.util.zip.ZipInputStream

/** Bounded OOXML inspection. Never extracts files, evaluates formulas, macros, or external links. */
object OfficeArchive {
    const val MAX_BYTES = 2 * 1024 * 1024
    fun excerpt(input: InputStream, kind: String, text: (String) -> String): String {
        require(kind in setOf("word", "excel", "powerpoint"))
        val scan = ArchiveScan(kind, text)
        ZipInputStream(input).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry -> scan.accept(zip, entry.name) }
        }
        return scan.result()
    }

    private class ArchiveScan(private val kind: String, private val text: (String) -> String) {
        private var total = 0
        private var entries = 0
        private var pages = 0
        private val snippets = mutableListOf<String>()
        fun accept(zip: ZipInputStream, name: String) {
            require(++entries <= 256) { "Document has too many parts" }
            if (isPage(name)) pages++
            val keep = selected(name)
            val content = readPart(zip, keep)
            if (keep) text(content).takeIf(String::isNotBlank)?.let(snippets::add)
        }
        private fun isPage(name: String): Boolean = when (kind) {
            "powerpoint" -> name.matches(Regex("ppt/slides/slide[0-9]+\\.xml"))
            "excel" -> name.matches(Regex("xl/worksheets/sheet[0-9]+\\.xml"))
            else -> false
        }
        private fun selected(name: String): Boolean = when (kind) {
            "word" -> name == "word/document.xml"
            "excel" -> name == "xl/sharedStrings.xml" || name == "xl/worksheets/sheet1.xml"
            else -> name == "ppt/slides/slide1.xml"
        }
        private fun readPart(zip: ZipInputStream, keep: Boolean): String {
            val buffer = ByteArray(8192)
            val output = java.io.ByteArrayOutputStream()
            while (true) {
                val count = zip.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_BYTES) { "Document preview exceeds limit" }
                if (keep) {
                    require(output.size() + count <= 256 * 1024) { "Document part exceeds limit" }
                    output.write(buffer, 0, count)
                }
            }
            return output.toString("UTF-8")
        }
        fun result(): String {
            require(entries > 0) { "Not an Office archive" }
            val prefix = when (kind) { "excel" -> "$pages sheets"; "powerpoint" -> "$pages slides"; else -> "Document text" }
            return AppSummaryPolicy.text(listOf(prefix, snippets.joinToString(" · ")).filter(String::isNotBlank).joinToString(" · "))
        }
    }
}
