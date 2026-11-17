package io.github.aimindseye.tenseven.platform

import java.io.File

/** Read-only compatibility for existing Active Frames summaries. Never changes or migrates notes. */
object LegacyNoteSummaries {
    private const val MAGIC = "sable-note 1"
    private const val PREVIEW_LIMIT = 120
    private const val CHECKLIST_TEXT_GROUP = 3
    private val heading = Regex("^#+\\s*")
    private val checklist = Regex("^(\\s*)- \\[([ xX])] ?(.*)$")

    data class Summary(
        val title: String,
        val preview: String,
        val updatedAt: Long,
    )

    fun latest(directory: File): Summary? =
        directory
            .listFiles { file -> file.isFile && file.name.endsWith(".note") }
            .orEmpty()
            .mapNotNull { file -> runCatching { decode(file.readText()) }.getOrNull() }
            .maxByOrNull { it.updatedAt }

    private fun decode(text: String): Summary? {
        val parts = text.split("\n\n", limit = 2)
        val header = parts.first().lines()
        if (parts.size != 2 || header.firstOrNull() != MAGIC) return null
        val fields =
            header
                .drop(1)
                .mapNotNull { line ->
                    val at = line.indexOf(' ')
                    if (at > 0) line.substring(0, at) to line.substring(at + 1) else null
                }.toMap()
        return fields["created"]?.toLongOrNull()?.let { created ->
            summary(parts.last(), fields["updated"]?.toLongOrNull() ?: created)
        }
    }

    private fun summary(
        body: String,
        updated: Long,
    ): Summary {
        val lines =
            body
                .lineSequence()
                .map {
                    checklist.matchEntire(it)?.groupValues?.get(CHECKLIST_TEXT_GROUP) ?: it.replace(heading, "")
                }.map { it.trim() }
                .filter { it.isNotEmpty() }
                .toList()
        val preview = lines.drop(1).joinToString(" · ")
        val bounded = if (preview.length > PREVIEW_LIMIT) preview.take(PREVIEW_LIMIT).trimEnd() + "…" else preview
        return Summary(lines.firstOrNull() ?: "Empty note", bounded, updated)
    }
}
