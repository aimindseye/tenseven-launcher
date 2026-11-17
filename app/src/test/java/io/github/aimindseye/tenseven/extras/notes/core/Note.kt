// Derived from SableOS (github.com/aimindseye/zinwaq25-sableos @ 31420f599c2f,
// sable-src/apps/r8/android/notes/src/main),
// Apache License 2.0. Changed: package and imports renamed for Tenseven; native Rust calls replaced by Kotlin where
// noted.
package io.github.aimindseye.tenseven.extras.notes.core

import io.github.aimindseye.tenseven.extras.notes.core.NotesText.t

/**
 * One note: plain text with lightweight structure ("# " headings and [Checklist] lines) and an
 * optional colour label. The first line that has text is the title.
 */
data class Note(
    val id: String,
    val body: String,
    val createdAt: Long,
    val updatedAt: Long,
    val label: NoteLabel = NoteLabel.NONE,
) {
    val title: String get() = NoteText.title(body)
}

private object NoteColors {
    const val NONE: Long = 0xFF64748B
    const val YELLOW: Long = 0xFFF5C542
    const val ORANGE: Long = 0xFFF28C45
    const val RED: Long = 0xFFE5534B
    const val PINK: Long = 0xFFE8458F
    const val PURPLE: Long = 0xFF7A42E8
    const val BLUE: Long = 0xFF4D9CFF
    const val GREEN: Long = 0xFF35C66B
}

/** Colour labels, as in Color Notes and BlackBerry Remember's categories. [NONE] draws a neutral bar. */
enum class NoteLabel(
    val argb: Long,
    private val nameKey: String,
) {
    NONE(NoteColors.NONE, "no_colour"),
    YELLOW(NoteColors.YELLOW, "yellow"),
    ORANGE(NoteColors.ORANGE, "orange"),
    RED(NoteColors.RED, "red"),
    PINK(NoteColors.PINK, "pink"),
    PURPLE(NoteColors.PURPLE, "purple"),
    BLUE(NoteColors.BLUE, "blue"),
    GREEN(NoteColors.GREEN, "green"),
    ;

    val displayName: String get() = t(nameKey)

    fun next(): NoteLabel = entries[(ordinal + 1) % entries.size]

    companion object {
        fun parse(name: String?): NoteLabel = entries.firstOrNull { it.name == name } ?: NONE
    }
}

/** Title and preview text for lists. */
object NoteText {
    val UNTITLED: String get() = t("empty_note")
    private const val PREVIEW_CHARS = 120
    private val HEADING = Regex("""^#+\s*""")

    fun title(body: String): String = lines(body).firstOrNull() ?: UNTITLED

    /** The lines after the title, joined on one line. */
    fun preview(body: String): String =
        lines(body).drop(1).joinToString(" · ").let {
            if (it.length > PREVIEW_CHARS) it.take(PREVIEW_CHARS).trimEnd() + "…" else it
        }

    /** Non-blank lines without heading or checklist markers. */
    private fun lines(body: String): List<String> =
        body
            .lineSequence()
            .map { Checklist.parse(it)?.text ?: it.replace(HEADING, "") }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
}
